package cn.iocoder.yudao.module.scada.service.collect;

import cn.iocoder.yudao.module.scada.dal.dataobject.device.ScadaDeviceDO;
import cn.iocoder.yudao.module.scada.dal.mysql.device.ScadaDeviceMapper;
import cn.iocoder.yudao.module.scada.enums.ScadaDeviceStatus;
import cn.iocoder.yudao.module.scada.framework.scada.driver.ScadaPlcDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备连接管理器（断线重连状态机）
 *
 * <p>状态机见 {@link ScadaDeviceStatus}，重连采用指数退避 1/2/4/8/30s。</p>
 *
 * <p>核心规则：</p>
 * <ul>
 *   <li>每次 readBatch 失败，{@code CollectTaskExecutor} 调 {@link #onConnectionError}，
 *       设备切到 RECONNECTING</li>
 *   <li>每个 RECONNECTING 设备由本组件的"重连守护线程"按指数退避尝试重连，
 *       成功 → CONNECTED，失败 → 继续递增退避，封顶 30s</li>
 *   <li>DISABLED / 长期 ERROR 的设备由 {@code getOrConnect} 直接拒绝返回 null</li>
 *   <li>每 60s 扫一次全量设备做心跳超时检测：超过 30s 没心跳的转 RECONNECTING</li>
 * </ul>
 *
 * <p>不做长连接 keepalive：Driver 内部各自维护 keepalive（Modbus 是 socket keepalive，
 * OPC UA 是 session keepalive），上层只关心应用层心跳。</p>
 */
@Slf4j
@Component
public class DeviceConnectionManager {

    private final ScadaDeviceMapper deviceMapper;
    private final ScadaPlcDriverManager driverManager;

    /** 设备 ID → 重连状态 */
    private final Map<Long, ReconnectState> reconnectStates = new ConcurrentHashMap<>();

    /** 设备元数据快照，避免每次执行都查 DB */
    private final Map<Long, ScadaDeviceDO> deviceCache = new ConcurrentHashMap<>();

    @Autowired
    public DeviceConnectionManager(ScadaDeviceMapper deviceMapper,
                                   ScadaPlcDriverManager driverManager) {
        this.deviceMapper = deviceMapper;
        this.driverManager = driverManager;
        startReconnectDaemon();
        startHeartbeatWatchdog();
    }

    /** 调用方：CollectTaskExecutor.executeTask 开头调用 */
    public ScadaDeviceDO getOrConnect(Long deviceId) {
        ScadaDeviceDO device = deviceCache.computeIfAbsent(deviceId, this::loadDevice);
        if (device == null) return null;
        if (Boolean.FALSE.equals(device.getEnabled())) {
            return null;
        }
        Integer status = device.getStatus();
        if (status == null) status = ScadaDeviceStatus.UNKNOWN.getState();
        // 状态判定：DISABLED / ERROR 拒绝；CONNECTING / RECONNECTING 等待（返回 null 让任务挂起）
        if (status.equals(ScadaDeviceStatus.DISABLED.getState())) return null;
        if (status.equals(ScadaDeviceStatus.ERROR.getState())) return null;
        if (status.equals(ScadaDeviceStatus.RECONNECTING.getState())) {
            // 试图立刻触发一次重连
            tryReconnect(device);
            return null;
        }
        return device;
    }

    /** 一次成功读取后调用，更新心跳时间 */
    public void onHeartbeat(Long deviceId) {
        ScadaDeviceDO device = deviceCache.get(deviceId);
        if (device == null) return;
        Integer oldStatus = device.getStatus();
        device.setStatus(ScadaDeviceStatus.CONNECTED.getState());
        device.setLastHeartbeatAt(LocalDateTime.now());
        device.setLastError(null);
        // 只在状态变化时写 DB，避免高频
        if (oldStatus == null || !oldStatus.equals(ScadaDeviceStatus.CONNECTED.getState())) {
            deviceMapper.updateHeartbeat(deviceId, ScadaDeviceStatus.CONNECTED.getState(), null);
            log.info("设备 {} 状态 {} -> CONNECTED", device.getCode(), oldStatus);
        }
    }

    /** 一次失败读取后调用，切状态机到 RECONNECTING */
    public void onConnectionError(Long deviceId, String error) {
        ScadaDeviceDO device = deviceCache.get(deviceId);
        if (device == null) return;
        Integer oldStatus = device.getStatus();
        device.setStatus(ScadaDeviceStatus.RECONNECTING.getState());
        device.setLastError(error);
        deviceMapper.updateHeartbeat(deviceId, ScadaDeviceStatus.RECONNECTING.getState(), error);
        // 注册重连状态：1s 后首次尝试
        reconnectStates.computeIfAbsent(deviceId, k -> new ReconnectState(0, System.currentTimeMillis() + 1000));
        log.warn("设备 {} 状态 {} -> RECONNECTING err={}", device.getCode(), oldStatus, error);
    }

    /** 设备元数据变更（增删改）时刷新快照 */
    public void refreshDevice(ScadaDeviceDO device) {
        deviceCache.put(device.getId(), device);
        driverManager.refreshDevice(device);
    }

    public void evictDevice(Long deviceId) {
        deviceCache.remove(deviceId);
        reconnectStates.remove(deviceId);
        driverManager.evictDevice(deviceId);
    }

    private ScadaDeviceDO loadDevice(Long deviceId) {
        return deviceMapper.selectById(deviceId);
    }

    /** 重连守护线程：每 500ms 扫一次，到点的设备尝试重连 */
    private void startReconnectDaemon() {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    long now = System.currentTimeMillis();
                    for (Map.Entry<Long, ReconnectState> e : reconnectStates.entrySet()) {
                        ReconnectState s = e.getValue();
                        if (now < s.nextAttemptAt) continue;
                        ScadaDeviceDO device = deviceCache.get(e.getKey());
                        if (device == null) {
                            reconnectStates.remove(e.getKey());
                            continue;
                        }
                        tryReconnect(device);
                    }
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception ex) {
                    log.error("重连守护异常: {}", ex.getMessage());
                }
            }
        }, "scada-reconnect");
        t.setDaemon(true);
        t.start();
        log.info("SCADA 重连守护线程已启动");
    }

    /** 主动尝试一次重连：触发 driverManager 池重建，成功切 CONNECTED，失败递增退避 */
    private void tryReconnect(ScadaDeviceDO device) {
        try {
            driverManager.reconnect(device.getId());
            // 触发一次心跳：让下次 getOrConnect 重新借出连接
            reconnectStates.remove(device.getId());
            device.setStatus(ScadaDeviceStatus.CONNECTED.getState());
            device.setLastHeartbeatAt(LocalDateTime.now());
            deviceMapper.updateHeartbeat(device.getId(), ScadaDeviceStatus.CONNECTED.getState(), null);
            log.info("设备 {} 重连成功", device.getCode());
        } catch (Exception ex) {
            ReconnectState s = reconnectStates.get(device.getId());
            if (s == null) s = new ReconnectState(0, System.currentTimeMillis() + 1000);
            int nextAttempt = s.attempts + 1;
            long backoffMs = computeBackoffMs(nextAttempt);
            reconnectStates.put(device.getId(), new ReconnectState(nextAttempt, System.currentTimeMillis() + backoffMs));
            if (nextAttempt % 10 == 0) {
                log.warn("设备 {} 重连 {} 次失败，下次 {}ms 后: {}",
                        device.getCode(), nextAttempt, backoffMs, ex.getMessage());
            }
        }
    }

    /** 指数退避 1/2/4/8/16/30/30...s */
    private long computeBackoffMs(int attempt) {
        long base = 1000L * (1L << Math.min(attempt, 5));
        return Math.min(base, 30_000);
    }

    /** 心跳超时守护：每 60s 扫一次，超过 30s 没心跳的转 RECONNECTING */
    private void startHeartbeatWatchdog() {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(60_000);
                    LocalDateTime cutoff = LocalDateTime.now().minusSeconds(30);
                    for (Map.Entry<Long, ScadaDeviceDO> e : deviceCache.entrySet()) {
                        ScadaDeviceDO d = e.getValue();
                        if (Boolean.FALSE.equals(d.getEnabled())) continue;
                        if (!ScadaDeviceStatus.CONNECTED.getState().equals(d.getStatus())) continue;
                        if (d.getLastHeartbeatAt() == null || d.getLastHeartbeatAt().isBefore(cutoff)) {
                            onConnectionError(d.getId(), "心跳超时 30s");
                        }
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception ex) {
                    log.error("心跳守护异常: {}", ex.getMessage());
                }
            }
        }, "scada-heartbeat");
        t.setDaemon(true);
        t.start();
        log.info("SCADA 心跳守护线程已启动");
    }

    /** 重连状态：已尝试次数 + 下次尝试时间戳 */
    private record ReconnectState(int attempts, long nextAttemptAt) {}
}
