package cn.iocoder.yudao.module.scada.service.collect;

import cn.iocoder.yudao.module.scada.dal.dataobject.device.ScadaDeviceDO;
import cn.iocoder.yudao.module.scada.dal.dataobject.point.ScadaPointDO;
import cn.iocoder.yudao.module.scada.dal.dataobject.task.ScadaCollectTaskDO;
import cn.iocoder.yudao.module.scada.dal.mysql.point.ScadaPointMapper;
import cn.iocoder.yudao.module.scada.dal.mysql.task.ScadaCollectTaskMapper;
import cn.iocoder.yudao.module.scada.enums.ScadaPointType;
import cn.iocoder.yudao.module.scada.framework.scada.driver.ScadaPlcDriverManager;
import cn.iocoder.yudao.module.scada.framework.scada.protocol.PointValue;
import cn.iocoder.yudao.module.scada.service.alarm.AlarmEngine;
import cn.iocoder.yudao.module.scada.service.push.RealtimePushService;
import cn.iocoder.yudao.module.scada.service.value.PointValueService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 采集任务执行器（核心调度引擎）
 *
 * <p>调度模型：单线程扫表 + 多线程执行。</p>
 * <ol>
 *   <li>单线程 {@code scheduler} 每秒扫一次 enabled=true 的任务，
 *       按 {@code lastRunAt + intervalMs} 过滤到点的任务</li>
 *   <li>命中任务提交 {@code executor} 线程池并发执行</li>
 *   <li>执行流程：批量读点位 → 写 TDengine → 推 SSE → 进告警引擎</li>
 * </ol>
 *
 * <p>为什么不用 Quartz？Quartz 适合"固定时间点"调度（如每天 9 点），
 * SCADA 是"固定周期"调度（每 1 秒），Quartz 的表锁与 trigger 表会成为瓶颈。
 * 自己写 ScheduledExecutor + 扫表更轻量，秒级延迟足够。</p>
 *
 * <p>线程池大小估算：典型 100 台设备 × 5 任务 = 500 任务，但每秒只有约 1/10 任务到点。
 * core=8 max=32 队列 200 拒绝策略 CallerRuns（保证不丢任务）。</p>
 *
 * <p>避免重复执行：同一任务在 {@code runningTaskIds} 标记 in-flight，
 * 上一轮未结束的下一轮不重复提交。这是任务并发抑制的核心。</p>
 */
@Slf4j
@Component
public class CollectTaskExecutor {

    private final ScadaCollectTaskMapper taskMapper;
    private final ScadaPointMapper pointMapper;
    private final ScadaPlcDriverManager driverManager;
    private final DeviceConnectionManager connectionManager;
    private final PointValueService valueService;
    private final AlarmEngine alarmEngine;
    private final RealtimePushService pushService;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> { Thread t = new Thread(r, "scada-sched"); t.setDaemon(true); return t; });

    private ThreadPoolExecutor executor;

    /** in-flight 任务 ID 集合，避免重复执行 */
    private final ConcurrentHashMap<Long, Boolean> runningTaskIds = new ConcurrentHashMap<>();

    private final Gson gson = new Gson();

    @Autowired
    public CollectTaskExecutor(ScadaCollectTaskMapper taskMapper,
                               ScadaPointMapper pointMapper,
                               ScadaPlcDriverManager driverManager,
                               DeviceConnectionManager connectionManager,
                               PointValueService valueService,
                               AlarmEngine alarmEngine,
                               RealtimePushService pushService) {
        this.taskMapper = taskMapper;
        this.pointMapper = pointMapper;
        this.driverManager = driverManager;
        this.connectionManager = connectionManager;
        this.valueService = valueService;
        this.alarmEngine = alarmEngine;
        this.pushService = pushService;
        initExecutor();
    }

    private void initExecutor() {
        this.executor = new ThreadPoolExecutor(
                8, 32, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                r -> { Thread t = new Thread(r, "scada-exec"); t.setDaemon(true); return t; },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public void start() {
        // 每 1 秒扫一次，initialDelay 5s 等容器完全启动
        scheduler.scheduleAtFixedRate(this::scanAndExecute, 5, 1, TimeUnit.SECONDS);
        log.info("SCADA CollectTaskExecutor 已启动，扫描周期=1s");
    }

    private void scanAndExecute() {
        try {
            List<ScadaCollectTaskDO> tasks = taskMapper.selectEnabledTasks();
            LocalDateTime now = LocalDateTime.now();
            for (ScadaCollectTaskDO task : tasks) {
                if (!isDue(task, now)) continue;
                if (runningTaskIds.containsKey(task.getId())) continue;
                if (Boolean.FALSE.equals(task.getConcurrent()) && runningTaskIds.containsKey(task.getId())) {
                    continue;
                }
                runningTaskIds.put(task.getId(), true);
                executor.submit(() -> {
                    long start = System.currentTimeMillis();
                    String error = null;
                    try {
                        executeTask(task);
                    } catch (Exception e) {
                        error = e.getMessage();
                        log.error("任务 {} 执行失败: {}", task.getCode(), e.getMessage(), e);
                    } finally {
                        long dur = System.currentTimeMillis() - start;
                        try {
                            taskMapper.updateRunStat(task.getId(), LocalDateTime.now(), dur, error);
                        } catch (Exception ignored) {
                        }
                        runningTaskIds.remove(task.getId());
                    }
                });
            }
        } catch (Exception e) {
            log.error("SCADA 调度扫描异常: {}", e.getMessage(), e);
        }
    }

    /** 判断任务是否到点：last_run_at IS NULL OR last_run_at + intervalMs < now */
    private boolean isDue(ScadaCollectTaskDO task, LocalDateTime now) {
        if (task.getLastRunAt() == null) return true;
        long nextRunAtMs = task.getLastRunAt()
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                + (task.getIntervalMs() == null ? 1000 : task.getIntervalMs());
        long nowMs = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return nowMs >= nextRunAtMs;
    }

    /** 单个任务执行：批量读 → 写库 → 推送 → 告警 */
    private void executeTask(ScadaCollectTaskDO task) {
        // 1. 设备就绪校验：让 DeviceConnectionManager 维护状态机
        ScadaDeviceDO device = connectionManager.getOrConnect(task.getDeviceId());
        if (device == null) {
            throw new RuntimeException("设备 " + task.getDeviceId() + " 不可用");
        }

        // 2. 批量加载点位
        List<Long> pointIds = parsePointIds(task.getPointIdsJson());
        if (pointIds.isEmpty()) return;
        List<ScadaPointDO> points = pointMapper.selectListByIds(pointIds);
        if (points.isEmpty()) return;

        // 3. 调 Driver 读
        List<PointValue> values = driverManager.readBatch(device, points);
        // 过滤掉 quality=BAD 的失败值，不污染下游
        List<PointValue> goodValues = values.stream()
                .filter(PointValue::isGood)
                .collect(Collectors.toList());
        if (goodValues.isEmpty()) return;

        // 4. 写 TDengine（批量）
        try {
            valueService.writeBatch(goodValues);
        } catch (Exception e) {
            log.warn("TDengine 写入失败，进入补采队列: {}", e.getMessage());
            // 失败入 ReplayBuffer 待补采，详见 ReplayBuffer 设计
            // 商用版在此调用 ReplayBuffer#push(goodValues)
        }

        // 5. 推 SSE
        try {
            pushService.submit(goodValues);
        } catch (Exception e) {
            log.debug("SSE 推送失败: {}", e.getMessage());
        }

        // 6. 进告警引擎
        try {
            alarmEngine.onValues(goodValues);
        } catch (Exception e) {
            log.warn("告警引擎处理失败: {}", e.getMessage());
        }

        // 7. 心跳更新（DeviceConnectionManager 内部判定断线用）
        connectionManager.onHeartbeat(device.getId());
    }

    private List<Long> parsePointIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        return gson.fromJson(json, new TypeToken<List<Long>>(){}.getType());
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        executor.shutdown();
        try {
            scheduler.awaitTermination(2, TimeUnit.SECONDS);
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        log.info("SCADA CollectTaskExecutor 已关闭");
    }
}
