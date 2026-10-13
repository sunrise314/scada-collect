package cn.iocoder.yudao.module.scada.framework.scada.sse;

import cn.iocoder.yudao.module.scada.framework.scada.protocol.PointValue;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SSE 推送服务器（Server-Sent Events）
 *
 * <p>实时曲线推送的甜点方案：</p>
 * <ul>
 *   <li>浏览器原生 EventSource 自动重连，不需要手写心跳</li>
 *   <li>HTTP 1.1 长连接，不需要 WebSocket 的握手/帧协议</li>
 *   <li>单向推送，正好匹配"PLC → 前端"的数据流</li>
 *   <li>比轮询省 90% 带宽</li>
 * </ul>
 *
 * <p>核心设计：</p>
 * <ol>
 *   <li>每个前端连接订阅一批 pointId，订阅关系存在 {@code connSubscriptions}</li>
 *   <li>{@code RealtimePushService.submit} 把 Driver 读到的值塞进 {@code pendingValues}，
 *       按 pointId 索引</li>
 *   <li>聚合线程 1Hz 扫一次 pendingValues，按订阅关系批量发到对应客户端</li>
 * </ol>
 *
 * <p>1Hz 聚合是关键：100 个客户端 × 100 个点位 × 1Hz 推送 = 1 万条/秒，
 * 如果逐条发会爆浏览器。聚合后每个客户端每秒只收 1 帧包含 N 条值的 JSON。</p>
 *
 * <p>断线自动清理：SseEmitter.onTimeout/onError/onCompletion 触发时清订阅关系，
 * 避免 Map 无限增长。</p>
 */
@Slf4j
@Component
public class SsePushServer {

    /** 默认 30 分钟超时，足够日常监控 */
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    /** 客户端 ID → SseEmitter */
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /** 客户端 ID → 订阅的 pointId 集合 */
    private final Map<String, Set<Long>> connSubscriptions = new ConcurrentHashMap<>();

    /** pointId → 最新值缓存（1Hz 推送前聚合用） */
    private final Map<Long, PointValue> pendingValues = new ConcurrentHashMap<>();

    private final ScheduledExecutorService aggregator = Executors.newSingleThreadScheduledExecutor(
            r -> { Thread t = new Thread(r, "scada-sse-agg"); t.setDaemon(true); return t; });

    public SsePushServer() {
        // 1Hz 聚合推送
        aggregator.scheduleAtFixedRate(this::flushAll, 1, 1, TimeUnit.SECONDS);
        log.info("SsePushServer 已启动，聚合周期=1s");
    }

    /** 新建一个 SSE 连接，订阅一组 pointId */
    public SseEmitter subscribe(String clientId, Set<Long> pointIds) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.put(clientId, emitter);
        connSubscriptions.put(clientId, new HashSet<>(pointIds));

        emitter.onCompletion(() -> cleanup(clientId, "complete"));
        emitter.onTimeout(() -> cleanup(clientId, "timeout"));
        emitter.onError(t -> cleanup(clientId, "error: " + t.getMessage()));

        // 立即发一帧 hello，让前端知道订阅成功
        try {
            emitter.send(SseEmitter.event().name("hello").data("connected clientId=" + clientId));
        } catch (IOException e) {
            log.warn("SSE hello 失败 clientId={}: {}", clientId, e.getMessage());
        }
        log.info("SSE 订阅 clientId={} points={}", clientId, pointIds.size());
        return emitter;
    }

    /** 取消订阅 */
    public void unsubscribe(String clientId) {
        cleanup(clientId, "manual");
    }

    /** 上游调用：把读到的值塞进聚合队列 */
    public void submitBatch(List<PointValue> values) {
        for (PointValue v : values) {
            if (v.pointId() != null) {
                pendingValues.put(v.pointId(), v);
            }
        }
    }

    /** 1Hz 聚合：把 pending 推到所有匹配的订阅者 */
    private void flushAll() {
        if (pendingValues.isEmpty() || emitters.isEmpty()) return;
        // 取当前 pending 快照后清空
        Map<Long, PointValue> snapshot = new HashMap<>(pendingValues);
        pendingValues.clear();

        for (Map.Entry<String, Set<Long>> e : connSubscriptions.entrySet()) {
            String clientId = e.getKey();
            Set<Long> subscribed = e.getValue();
            SseEmitter emitter = emitters.get(clientId);
            if (emitter == null) continue;
            // 只发该客户端订阅的点位
            List<PointValue> toSend = new ArrayList<>();
            for (Long pid : subscribed) {
                PointValue v = snapshot.get(pid);
                if (v != null) toSend.add(v);
            }
            if (toSend.isEmpty()) continue;
            try {
                emitter.send(SseEmitter.event().name("values").data(toSend));
            } catch (Exception ex) {
                log.debug("SSE 推送失败 clientId={}: {}", clientId, ex.getMessage());
                cleanup(clientId, "send-fail");
            }
        }
    }

    private void cleanup(String clientId, String reason) {
        emitters.remove(clientId);
        connSubscriptions.remove(clientId);
        log.debug("SSE 清理 clientId={} reason={}", clientId, reason);
    }

    @PreDestroy
    public void shutdown() {
        aggregator.shutdown();
        // 通知所有客户端关闭
        for (Map.Entry<String, SseEmitter> e : emitters.entrySet()) {
            try {
                e.getValue().complete();
            } catch (Exception ignored) {
            }
        }
        log.info("SsePushServer 已关闭，剩余连接={}", emitters.size());
    }
}
