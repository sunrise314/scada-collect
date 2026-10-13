# 07 - SSE 实时曲线推送

## 一、痛点引入

前两章把数据落地和告警都通了，剩下最后一公里：把 PLC 实时读到的值推到前端大屏。这是 SCADA 项目最容易做歪的一段。

我最初用 WebSocket，思路很顺：建连 → 服务端推送 → 前端展示。但实施起来一堆破事：

- WebSocket 需要前端 `new WebSocket(url)` + 手写断线重连、心跳、超时、订阅消息协议；
- Nginx 反代要额外配 `proxy_set_header Upgrade $http_upgrade`、`Connection "upgrade"`、`proxy_read_timeout`，运维不熟的话连不上；
- Yudao 框架自带 WebSocket 模块，但要求前后端共一个 endpoint 协议，工业大屏前端是独立项目，套用不顺；
- 浏览器对同域 WebSocket 数有限制，多 tab 直接卡死；
- 单向推送场景下用 WebSocket 是杀鸡用牛刀——握手、帧协议、双向通信开销全用不上。

后来我突然想明白：SCADA 大屏要的是"服务端推到前端"的单向流，浏览器原生就有为这种场景设计的协议——Server-Sent Events（SSE）。这一章讲我怎么把 SSE 落到 Yudao + Spring Boot 里，以及落地的关键工程细节。

## 二、行业对比：SSE vs WebSocket vs 长轮询

| 维度 | SSE | WebSocket | 长轮询 |
|---|---|---|---|
| 方向 | 服务端→客户端单向 | 双向 | 客户端拉取 |
| 协议 | HTTP/1.1 chunked | 自定义帧 | HTTP/1.1 |
| 浏览器 API | `EventSource` 原生 | `WebSocket` 原生 | XHR 手写 |
| 自动重连 | 浏览器内置 | 需手写 | 需手写 |
| 心跳 | 不需要 | 必须 | 不需要 |
| Nginx 配置 | 普通 HTTP | 需 upgrade header | 普通 HTTP |
| 单连接开销 | 一条 TCP 长连接 | 一条 TCP + 帧协议 | 反复建连 |
| 大屏适配 | 完美 | 过度设计 | 性能差 |
| 适配 SCADA 场景 | 9 分 | 7 分 | 4 分 |

SSE 在 SCADA 大屏这种"服务端持续推、客户端不发"的场景里几乎是满分方案。`EventSource` API 浏览器原生支持，断线自动重连不用写，HTTP 普通 chunked 响应 nginx 反代零配置。**唯一缺点**是 IE 不支持，但工业大屏用 Chrome/Edge，没这个包袱。

## 三、选型与设计取舍

**为什么 SSE 不 WebSocket？** 三条理由：

第一，SCADA 大屏数据流是单向的——PLC → 后端 → 前端，前端几乎不发数据（最多发订阅请求，一次 HTTP POST 就够）。WebSocket 的双向通信能力是浪费的，握手/帧协议全是纯纯开销。

第二，`EventSource` 浏览器内置自动重连。WebSocket 断线要自己写"心跳+指数退避重连"代码，工业大屏前端开发就是浪费。SSE 断线浏览器自动重连，无需业务层关心。

第三，部署顺滑。WebSocket 要 nginx 加 upgrade header，K8s ingress 也要配 annotation。SSE 是普通 HTTP chunked 响应，nginx 默认配置就能转发，运维成本最低。

**SseEmitter 30min timeout 为什么是 30 不是 60？** 这是被现实教训打的。我最早设 60min，结果某次 nginx 上游超时配的是 30min，连接被 nginx 在 30min 静默关掉，但 Spring 这边以为连接还活着继续推，所有写入都进 void。前端等不到数据，又因为 `EventSource` 必须等服务端关连接才重连，整个大屏僵住 30 分钟。

正确做法是 `SseEmitter` timeout 比所有上游中间件超时短一点——30min 是常见 nginx `proxy_read_timeout` 默认值，我设 30min 保证我先关，前端立刻重连。配置在 `application-scada.yaml`：

```yaml
scada:
  sse:
    emitter-timeout-ms: 1800000   # 30 分钟
    flush-interval-ms: 1000       # 1Hz 聚合
    max-points-per-client: 500    # 单连接最大订阅点位
```

**1Hz 聚合 flush 而不是有值就推**。这跟 SCADA 的负载模型直接相关。一个工厂大屏可能 100 个客户端同时在线，每个订阅 500 点位，1Hz 采集——如果"有值就推"，理论推送量是 100 × 500 × 1 = 5 万条/秒。逐条 HTTP chunk 帧到浏览器，浏览器 EventSource 单线程解析立刻成为瓶颈，且 JSON 序列化开销巨大。

聚合策略：1Hz 扫一次 `pendingValues`，把每个客户端订阅点位里"最新值"打成一帧 JSON 数组发过去。100 客户端 × 1 帧/秒 = 100 帧/秒，每个客户端每秒只收 1 帧包含最多 500 条值。带宽和浏览器解析压力都下降两个量级。

**max-points-per-client=500 怎么定的？** 单帧 JSON 500 条 PointValue，每条约 100 字节，整帧 50KB——Chrome 单 EventSource 帧大小限制远大于此，无压力。500 点位覆盖一个完整工艺段（一条生产线所有温度/压力/电流点位）足够。超过 500 一定是用户把整厂所有点位都订阅了，那是异常用法，强制限制防止单连接吃满推送内存。

**hello 帧的作用**。SSE 建连成功后立刻发一帧 `name=hello` 数据，前端据此知道"订阅成功、可以等数据了"。没有这帧的话，前端连上后到第一批数据来之间有最多 1 秒空白期，前端可能误以为连接失败触发错误提示。hello 帧相当于建立时的"ACK"。

## 四、关键代码精讲

### 4.1 SsePushServer 整体设计

`yudao-module-scada/src/main/java/cn/iocoder/yudao/module/scada/framework/scada/sse/SsePushServer.java`：

```java
@Component
public class SsePushServer {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> connSubscriptions = new ConcurrentHashMap<>();
    private final Map<Long, PointValue> pendingValues = new ConcurrentHashMap<>();

    private final ScheduledExecutorService aggregator = Executors.newSingleThreadScheduledExecutor(
            r -> { Thread t = new Thread(r, "scada-sse-agg"); t.setDaemon(true); return t; });

    public SsePushServer() {
        aggregator.scheduleAtFixedRate(this::flushAll, 1, 1, TimeUnit.SECONDS);
        log.info("SsePushServer 已启动，聚合周期=1s");
    }
}
```

三个 `Map` 各司其职：

- `emitters`：客户端 ID → SseEmitter，连接对象本身；
- `connSubscriptions`：客户端 ID → 订阅的 pointId 集合，决定每个客户端收哪些值；
- `pendingValues`：pointId → 最新值缓存，1Hz 推送前聚合用。

为什么 `pendingValues` 用 `Map<Long, PointValue>` 而不是 List？因为同一点位 1Hz 内可能多次写入（多个采集任务共享），后面覆盖前面就行——前端大屏只关心最新值，不关心历史（历史走 TDengine）。

聚合线程是单线程 `ScheduledExecutorService`，名字 `scada-sse-agg`，daemon 模式不阻止 JVM 关闭。单线程够了——1Hz 一次聚合，每次扫 100-1000 个客户端，处理时间几毫秒，远没到瓶颈。

### 4.2 订阅流程与生命周期回调

```java
public SseEmitter subscribe(String clientId, Set<Long> pointIds) {
    SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
    emitters.put(clientId, emitter);
    connSubscriptions.put(clientId, new HashSet<>(pointIds));

    emitter.onCompletion(() -> cleanup(clientId, "complete"));
    emitter.onTimeout(() -> cleanup(clientId, "timeout"));
    emitter.onError(t -> cleanup(clientId, "error: " + t.getMessage()));

    try {
        emitter.send(SseEmitter.event().name("hello").data("connected clientId=" + clientId));
    } catch (IOException e) {
        log.warn("SSE hello 失败 clientId={}: {}", clientId, e.getMessage());
    }
    log.info("SSE 订阅 clientId={} points={}", clientId, pointIds.size());
    return emitter;
}
```

注意三个回调的注册顺序——必须 `onCompletion / onTimeout / onError` 三个都注册，缺一不可。它们由 Spring 在不同时机触发：

- `onCompletion`：连接正常结束（服务端主动 `complete()` 或客户端关闭）；
- `onTimeout`：超过 TIMEOUT_MS 没数据被服务端关；
- `onError`：写入异常（客户端已断开、网络中断）。

漏注册任何一个都会让对应场景下 `emitters` 和 `connSubscriptions` Map 无限增长，最终 OOM。我同事在另一个项目里只注册了 `onCompletion`，结果客户端断网时只触发 `onError`，emitter 永远留在 Map 里，一周后堆内存爆掉。

`hello` 帧的发送放最后，原因下面讲。

### 4.3 上游提交与聚合 flush

```java
public void submitBatch(List<PointValue> values) {
    for (PointValue v : values) {
        if (v.pointId() != null) {
            pendingValues.put(v.pointId(), v);
        }
    }
}

private void flushAll() {
    if (pendingValues.isEmpty() || emitters.isEmpty()) return;
    Map<Long, PointValue> snapshot = new HashMap<>(pendingValues);
    pendingValues.clear();

    for (Map.Entry<String, Set<Long>> e : connSubscriptions.entrySet()) {
        String clientId = e.getKey();
        Set<Long> subscribed = e.getValue();
        SseEmitter emitter = emitters.get(clientId);
        if (emitter == null) continue;
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
```

聚合逻辑三步：

1. 取 `pendingValues` 快照并清空原 Map——这一步关键，必须先 `new HashMap` 拷贝再 `clear()`，让后续写入进下一轮，避免迭代时被改；
2. 遍历所有订阅关系，按每个客户端订阅的 pointId 集合从快照里捞对应值；
3. 调 `emitter.send` 推一帧 `name=values` 的 JSON。

推送失败立刻 `cleanup`，不让死连接继续占资源。这是高频陷阱——客户端断网后服务端 emitter 仍在 Map 里，第一次 flushAll 推送时抛 IOException，必须此时清理，否则下一轮还会尝试推送，错误日志刷屏。

### 4.4 cleanup 的并发陷阱

```java
private void cleanup(String clientId, String reason) {
    emitters.remove(clientId);
    connSubscriptions.remove(clientId);
    log.debug("SSE 清理 clientId={} reason={}", clientId, reason);
}
```

简单到不能再简单，但踩坑无数。**陷阱一**：三个回调（onCompletion/onTimeout/onError）可能并发触发，比如客户端断网同时触发 onError 和 onCompletion。`ConcurrentHashMap.remove` 是幂等的，重复 remove 不报错，但日志会重复，需要按需加唯一性保护。我没加是因为重复 cleanup 无副作用，简化优先。

**陷阱二**：`emitter.send` 失败时调 `cleanup(clientId, "send-fail")`，但此时 Spring 的 `onCompletion` 可能正排队等触发，下一轮又会再 `cleanup` 一次。同样，幂等设计兜底。

**陷阱三**：cleanup 之后还有可能在另一线程的 flushAll 里被引用。因为 `connSubscriptions.entrySet()` 是弱一致快照，cleanup 移除后正在迭代的 flushAll 仍可能拿到这个 entry——上面 `emitters.get(clientId)` 返回 null 时 continue 兜底了。这种"已清理但仍被迭代"的情况必须每一步都做 null 校验。

### 4.5 关闭时的优雅退出

```java
@PreDestroy
public void shutdown() {
    aggregator.shutdown();
    for (Map.Entry<String, SseEmitter> e : emitters.entrySet()) {
        try {
            e.getValue().complete();
        } catch (Exception ignored) {
        }
    }
    log.info("SsePushServer 已关闭，剩余连接={}", emitters.size());
}
```

先关聚合线程避免新推送，再主动 `complete()` 所有 emitter，让前端立刻收到连接结束触发自动重连。如果不主动 complete，Spring 关闭时会等 timeout 才回收，前端在关闭期间会有最多 30min 数据空洞。优雅退出的代价是几行代码，收益是关闭无感知。

### 4.6 Controller 端点

`yudao-module-scada/src/main/java/cn/iocoder/yudao/module/scada/controller/admin/value/ScadaPointValueController.java`：

```java
@GetMapping(value = "/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
@Operation(summary = "实时值流订阅（SSE）")
public SseEmitter stream(@RequestParam("pointIds") String pointIds) {
    Set<Long> idSet = parseIds(pointIds);
    String clientId = UUID.randomUUID().toString();
    return pushService.subscribe(clientId, idSet);
}

@DeleteMapping("/stream-close")
@Operation(summary = "取消订阅")
public CommonResult<Boolean> close(@RequestParam("clientId") String clientId) {
    pushService.unsubscribe(clientId);
    return success(true);
}
```

注意三点：

- `produces = TEXT_EVENT_STREAM_VALUE` 必须显式声明，否则 Yudao 全局响应包装会把响应包成 JSON，SSE 协议就破了；
- `pointIds` 用逗号分隔字符串而不是数组参数——GET URL 数组参数序列化在不同前端库不一致，逗号分隔最稳；
- `clientId` 用 UUID，避免前端伪造猜别人的 ID 误关连接。生产环境应进一步做权限校验，确保 clientId 跟当前 session 绑定。

### 4.7 采集任务如何调推送

`yudao-module-scada/src/main/java/cn/iocoder/yudao/module/scada/service/collect/CollectTaskExecutor.java`：

```java
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
```

`pushService.submit` 只是往 `pendingValues` Map 里 put，本身几乎不耗时。所以推送失败概率极低，失败只记 debug 日志，不阻塞后续告警处理。整个采集任务的写入顺序是：写 TDengine → 推 SSE → 进告警引擎——TDengine 是数据持久层最优先，告警引擎放最后让告警逻辑慢一点也不影响采集主线。

### 4.8 RealtimePushService 薄壳

```java
@Service
public class RealtimePushServiceImpl implements RealtimePushService {
    private final SsePushServer sseServer;

    @Override
    public void submit(List<PointValue> values) {
        sseServer.submitBatch(values);
    }

    @Override
    public SseEmitter subscribe(String clientId, Set<Long> pointIds) {
        return sseServer.subscribe(clientId, pointIds);
    }

    @Override
    public void unsubscribe(String clientId) {
        sseServer.unsubscribe(clientId);
    }
}
```

薄壳意义在于未来扩展——如果业务要加 WebSocket、kafka 流式推送、MQTT broker 转发，直接在 `RealtimePushServiceImpl` 里加分发逻辑即可，调用方不变。这是适配器模式的典型用法，**只在确有未来扩展预期时才用**，避免过度抽象。

## 五、调优与踩坑

**坑一：SseEmitter timeout 与 nginx proxy_read_timeout 对齐**。前面讲过，SseEmitter 30min 必须 ≤ nginx 30min，否则连接被 nginx 静默关掉。商用部署要把 nginx `proxy_read_timeout`、`proxy_send_timeout`、`proxy_buffering off`、`X-Accel-Buffering: no` 都配齐，避免 SSE 被 nginx 缓冲。SSE 必须流式发送，缓冲会让前端延迟到 buffer 满才收到数据，体验是"5 秒一次批量更新"而不是"1 秒一帧"。

**坑二：单客户端订阅点位数无上限**。最初没限制 max-points-per-client，有用户把整厂 10000 点位全订阅了，单帧 JSON 1MB，Chrome 解析一次卡 200ms。加了 500 限制后强制分页，用户主动选最关心的工艺段。

**坑三：`emitters` 用 `Map<String, SseEmitter>` 不用 `Map<Long, SseEmitter>`**。最初想用自增 long ID，结果跨服务实例时 ID 冲突。UUID 字符串虽然长，但天然全局唯一，多实例部署不用协调。如果未来要 Redis 做跨实例推送路由，UUID 也好做分片 key。

**坑四：聚合线程死锁风险**。`aggregator.scheduleAtFixedRate` 单线程，如果某次 `flushAll` 因为某客户端 send 卡住（网络 IO 阻塞），整个聚合线程被卡住，所有客户端都收不到数据。商用版应给 `emitter.send` 加超时（SseEmitter 不支持原生超时，需要包一层异步 send），或者把 send 改成异步线程池分发。

**坑五：hello 帧发送失败的容错**。hello 帧失败时我只 warn 日志，没主动 cleanup。这个简化是故意的——hello 失败大概率是网络瞬抖，下一次 flushAll 自然也会失败并触发 cleanup。提前 cleanup 反而可能让瞬时抖动的连接被立刻关闭失去重连机会。

**坑六：客户端 ID 跨重启复用问题**。客户端用 UUID 每次建连都新生成，所以服务端重启后老 ID 不会再被引用，emitters Map 不会留垃圾。但如果未来做"持久会话"（用户希望重连后继续订阅同一批点位），客户端 ID 要跟用户 session 绑定，重启后服务端从 Redis 恢复订阅关系。

## 六、商用化检查清单

- [ ] nginx 配置 `proxy_buffering off` + `X-Accel-Buffering: no` 保证流式
- [ ] `proxy_read_timeout` ≥ SseEmitter timeout
- [ ] 客户端 ID 跟用户 session 绑定，做权限校验
- [ ] `emitter.send` 加异步超时，防止单连接卡死聚合线程
- [ ] max-points-per-client 强制 500 上限，超限拒绝订阅并提示前端分页
- [ ] 监控指标：在线连接数、单连接平均订阅点位、推送失败率
- [ ] 跨实例推送：多实例部署时订阅关系走 Redis Pub/Sub 路由
- [ ] 心跳保活：1Hz 推送本身是心跳，但空闲点位长期没值时仍要发空帧
- [ ] 客户端鉴权：clientId 绑定 JWT，避免越权订阅他人点位
- [ ] 优雅退出：`@PreDestroy` 主动 complete 所有 emitter
- [ ] 灰度发布：滚动重启时连接重连风暴控制（随机退避）

---

到这里 SCADA 数据链路从 PLC → 采集任务 → TDengine 存储 → 告警引擎 → SSE 推送全闭环。从第 05 章的时序库选型，到第 06 章的告警引擎 ISA-18.2 简化与抖动抑制，再到这一章的 SSE 实时曲线推送，三章合起来覆盖了一个商用 SCADA 系统最核心的"采集-存储-告警-推送"闭环。下一章我会讲设备连接管理——怎么维护 PLC 长连接、心跳超时判定、断线重连策略，以及为什么不用 Spring Boot 自带的 ScheduledExecutor 而要自己造轮子。
