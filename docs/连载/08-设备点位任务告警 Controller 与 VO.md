# 08-设备点位任务告警 Controller 与 VO

## 一、痛点引入

到上一章为止，整个 SCADA 模块的核心引擎都跑起来了：Driver 池、采集调度器、TDengine 写入、AlarmEngine、SsePushServer。但是用户在哪儿？在前端的 Element Plus 表单里。前端要配一台 PLC、挂几个点位、起一个采集任务、改一条告警阈值——这些动作都得有一个干净的 REST 接口去对接。

我最早图省事，把设备 Controller 写成一个 8 合 1 的大接口：create/update/delete/list/get/toggle/runOnce/test 全塞在 `ScadaDeviceController` 里，而且全部直接收发 `ScadaDeviceDO`。结果上线第二天前端同事过来吐槽两件事：

第一，他调 `/get` 拿到一台设备，DO 里居然带了 `deleted` 和 `tenantId` 字段，他不知道这俩字段干嘛用的，怕覆盖回去出问题，前端代码里到处写 `delete resp.tenantId`。

第二，他调 `/update` 改了一下设备名，结果服务端 `BeanUtils.copyProperties` 把 `lastHeartbeatAt`、`status`、`lastError` 这些 DO 上的运行时字段一并更新成 null——因为这些字段在他前端表单里没有。我把状态机好不容易写好的"已连接/重连中"全清零了，看板瞬间全灰。

那天晚上我老老实实把这套 Controller 推倒重写，分三种 VO，把运行时字段和编辑字段彻底切开。这一章就把这 6 个 Controller 和它背后的 VO 设计一次讲透。

## 二、行业对比

行业里能拿来对比的两个标杆是 ThingsBoard 和 IoTDB 自带的 REST API。

ThingsBoard 的 DeviceController 走的是"一个 BaseEntity + 一堆 ControllerLevel 注解"的路子，看起来接口少，实际上前端要自己组装 `additionalInfo` 这种半结构化字段，VO 的概念几乎没有。它把"模型即接口"贯彻到极致，对插件式生态友好，但对商用交付的二开团队不友好——你敢动它一个字段，五处地方跟着破。

IoTDB 自带的 REST 接口则是另一种极端：SQL 字符串透传，VO 完全省略，前后端只对 SQL 语法负责。优点是灵活，缺点是无法做权限粒度校验，也无法做 OpenAPI 文档化。

我这套 Controller 走的是 yudao/ruoyi 的传统套路：每个领域对象三种 VO（Save/Page/Resp），Controller 只做 DTO↔DO 转换，业务在 Service。这种"重 VO 轻 Controller"的设计在工业 SCADA 这个语境下其实是最稳的——前端表单字段固定、权限粒度清晰、Swagger 文档一目了然，运维一看就懂。

## 三、选型与设计取舍

在贴代码之前，先把几个反复被同事问的工程取舍摆出来，这些取舍都直接体现在源码注释里。

### 1. 三种 VO 分开，不混用

`SaveReqVO` 管创建和更新（前端表单字段集），`PageReqVO` 管列表筛选条件（全部可选字段），`RespReqVO` 管响应（不暴露 `deleted`/`tenantId`）。三个 VO 字段集可以重叠但语义不可混。最直观的例子：`ScadaDeviceRespReqVO` 里有 `lastHeartbeatAt`、`status`、`lastError`，而 `ScadaDeviceSaveReqVO` 里绝对没有——因为前端表单不能写心跳时间，但前端看板需要看心跳时间。

### 2. pointIdsJson 不反序列化成 List<Long>

`ScadaCollectTaskSaveReqVO.pointIdsJson` 字段类型就是 `String`。我特意不收 `List<Long>`。理由有三：① 前端拼字符串比反序列化对象更省事；② 后端写入 MySQL 也是 TEXT 列，反序列化只是为了立刻再序列化回去，纯属浪费；③ 真正用到的调度器执行期才解析（懒解析），避免在 Service 层做无意义的对象转换。

### 3. ack/clear 用 @RequestParam 而不用 @PathVariable

告警事件的确认和复归两个接口，我没用 `/scada/event/{id}/ack`，而是 `@RequestParam("id")`。理由很朴素：① query 参数在 Swagger 文档里一眼能看出 required；② IDE 跳转不会把 ID 路径段当成资源集合；③ ack 和 clear 是动词不是子资源，RESTful 原教旨主义在这两种动作上做 path 反而别扭。

### 4. SSE 端点不包装 CommonResult

`/scada/value/stream` 返回的是 `SseEmitter`，外面没有 `CommonResult.success(...)`。`CommonResult` 是为单次请求/响应设计的，SSE 是长连接流式对象，硬包装只会让前端 `EventSource` 解析失败。Yudao 框架对此也支持——返回类型是 `SseEmitter` 时拦截器不会强行包装。

### 5. ScadaAlarmRuleController.get 直接返回 SaveReqVO

告警规则的 `get`/`list` 都直接返回 `ScadaAlarmRuleSaveReqVO`，没有单独 `RespReqVO`。因为规则的字段集合就是前端编辑表单的字段集合，不存在"前端不应该看到的内部字段"。再多搞一个 VO 是冗余设计。

### 6. toggleEnabled 单独成接口，不混 update

这是最痛的一个教训。最早我把启停合并到 `update` 里，结果前端把启停按钮的请求体就传 `{id, enabled}`，MyBatis-Plus 的 `updateById` 把其他 null 字段一并跳过——这倒没问题。但有个前端同事图省事，直接拿 `/get` 的响应体当 `/update` 的请求体发回去，把 `lastRunAt`、`runCount`、`errorCount` 一并覆盖。我后来在 `ScadaCollectTaskController` 的类注释里专门写了警告，并把 `toggleEnabled` 拆出来单独走，只更新 `enabled` 一个字段。

## 四、关键代码精讲

### 4.1 设备 Controller：CRUD + toggle-enabled

文件：`yudao-module-scada/src/main/java/cn/iocoder/yudao/module/scada/controller/admin/device/ScadaDeviceController.java`

```java
@Tag(name = "管理后台 - SCADA 设备")
@RestController
@RequestMapping("/scada/device")
public class ScadaDeviceController {

    private final ScadaDeviceService deviceService;

    public ScadaDeviceController(ScadaDeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping("/create")
    @Operation(summary = "创建设备")
    public CommonResult<Long> create(@Valid @RequestBody ScadaDeviceSaveReqVO reqVO) {
        return success(deviceService.createDevice(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新设备")
    public CommonResult<Boolean> update(@Valid @RequestBody ScadaDeviceSaveReqVO reqVO) {
        deviceService.updateDevice(reqVO);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取设备详情")
    @Parameter(name = "id", description = "设备 ID", required = true)
    public CommonResult<ScadaDeviceRespReqVO> get(@RequestParam("id") Long id) {
        ScadaDeviceDO device = deviceService.getDevice(id);
        return success(convert(device));
    }

    @PutMapping("/toggle-enabled")
    @Operation(summary = "启用/禁用设备")
    public CommonResult<Boolean> toggleEnabled(@RequestParam("id") Long id,
                                                @RequestParam("enabled") Boolean enabled) {
        deviceService.toggleEnabled(id, enabled);
        return success(true);
    }

    private ScadaDeviceRespReqVO convert(ScadaDeviceDO device) {
        if (device == null) return null;
        ScadaDeviceRespReqVO vo = new ScadaDeviceRespReqVO();
        BeanUtils.copyProperties(device, vo);
        return vo;
    }
}
```

注意这里特意省略了 `@PreAuthorize` 注解。芋道的 RBAC 拦截器会按菜单 URL 自动校验权限，Controller 里再写一遍注解只会污染代码——这是 yudao 的约定，不是我偷懒。

### 4.2 三种 VO 的字段差异

`ScadaDeviceSaveReqVO`：

```java
@Schema(description = "管理后台 - SCADA 设备保存 Request VO")
@Data
public class ScadaDeviceSaveReqVO {
    @Schema(description = "主键 ID，编辑时必填")
    private Long id;

    @NotEmpty(message = "设备编码不能为空")
    private String code;

    @NotEmpty(message = "设备名称不能为空")
    private String name;

    @NotNull(message = "协议类型不能为空")
    private Integer protocolType;

    @NotEmpty(message = "连接参数不能为空")
    private String connParamsJson;

    private Boolean enabled;
    private String remark;
}
```

`ScadaDeviceRespReqVO`（多出 `status`/`lastHeartbeatAt`/`lastError`/`createTime`，少校验注解）：

```java
@Data
public class ScadaDeviceRespReqVO {
    private Long id;
    private String code;
    private String name;
    private Integer protocolType;
    private String connParamsJson;
    private Integer status;
    private LocalDateTime lastHeartbeatAt;
    private String lastError;
    private Boolean enabled;
    private String remark;
    private LocalDateTime createTime;
}
```

`ScadaDevicePageReqVO` 全部字段可选：

```java
@Data
public class ScadaDevicePageReqVO {
    private String code;       // LIKE
    private String name;       // LIKE
    private Integer protocolType;
    private Integer status;
    private Boolean enabled;
}
```

字段集差异体现的是三种动作的语义差异：保存要校验、分页要全部可选、响应要暴露运行时字段。这就是"VO 分开"的真实含义，不是抽象教条。

### 4.3 点位 Controller：list-by-device 是高频接口

文件：`yudao-module-scada/src/main/java/cn/iocoder/yudao/module/scada/controller/admin/point/ScadaPointController.java`

```java
@GetMapping("/list-by-device")
@Operation(summary = "按设备列出点位")
@Parameter(name = "deviceId", description = "设备 ID", required = true)
public CommonResult<List<ScadaPointRespReqVO>> listByDevice(@RequestParam("deviceId") Long deviceId) {
    List<ScadaPointDO> list = pointService.listByDevice(deviceId);
    return success(list.stream().map(this::convert).toList());
}
```

这个接口单独拿出来讲，因为它是前端"设备 → 点位树"联动的核心。前端在选设备节点时立刻调它拉点位，频率比 `/list` 高一个数量级。所以它必须独立于通用 list，不能让前端用 list 加 deviceId 过滤——少一次条件分支就少一次 N+1 风险。

`ScadaPointSaveReqVO` 的校验注解也值得看一眼：

```java
@NotNull(message = "设备 ID 不能为空")
private Long deviceId;

@NotEmpty(message = "点位标签不能为空")
private String tag;

@NotNull(message = "点位类型不能为空")
private Integer pointType;
```

`tag` 用 `@NotEmpty`、`deviceId` 用 `@NotNull`、`scale`/`offset` 不加注解——校验粒度按业务必要性走，不做防御式校验。

### 4.4 任务 Controller：toggleEnabled 不重建对象

文件：`yudao-module-scada/src/main/java/cn/iocoder/yudao/module/scada/controller/admin/task/ScadaCollectTaskController.java`

`ScadaCollectTaskServiceImpl.toggleEnabled` 的实现是关键：

```java
@Override
public void toggleEnabled(Long id, Boolean enabled) {
    ScadaCollectTaskDO update = new ScadaCollectTaskDO();
    update.setId(id);
    update.setEnabled(enabled);
    taskMapper.updateById(update);
    log.info("采集任务 {} 启停切换 enabled={}", id, enabled);
}
```

这里只构造了一个只带 `id` 和 `enabled` 的 DO 喂给 `updateById`，MyBatis-Plus 默认 null 字段不更新。结果是 SQL 只会 `UPDATE ... SET enabled=? WHERE id=?`，`runCount`/`errorCount`/`lastRunAt` 这些统计字段根本不会被碰。注释里写得很直白："不重建任务对象，避免清空 runCount/errorCount 等统计字段"。

任务表的统计字段在 `sql/scada-init.sql` 里定义：

```sql
`last_run_at` DATETIME NULL COMMENT '上次执行时间',
`last_duration_ms` BIGINT NULL COMMENT '上次执行耗时毫秒',
`last_error` VARCHAR(512) NULL COMMENT '上次错误信息',
`run_count` BIGINT NOT NULL DEFAULT 0 COMMENT '累计执行次数',
`error_count` BIGINT NOT NULL DEFAULT 0 COMMENT '累计错误次数',
```

这些字段只由 `CollectTaskExecutor` 在执行期写，CRUD 接口永远不能碰。

### 4.5 告警事件 Controller：X-Operator 头透传

文件：`yudao-module-scada/src/main/java/cn/iocoder/yudao/module/scada/controller/admin/event/ScadaAlarmEventController.java`

```java
@PutMapping("/ack")
@Operation(summary = "人工确认")
@Parameter(name = "id", description = "事件 ID", required = true)
public CommonResult<Boolean> ack(@RequestParam("id") Long id,
                                 @RequestHeader(value = "X-Operator", required = false) String operator) {
    eventService.ackEvent(id, operator == null ? "system" : operator);
    return success(true);
}
```

operator 来源是请求头 `X-Operator`，不是从 yudao 的 `SecurityUtils.getLoginUserId()` 取。这是个折中：联调阶段前端可以直接 curl，加个 `-H "X-Operator: zhangsan"` 模拟某人；正式上线后，网关层会把 `SecurityUtils.getLoginUserId()` 的结果回填到这个 header。这比在 Controller 里写死 `getLoginUserId()` 灵活得多——联调时不必每次都登录。

ack 和 clear 不能跳级，已 ACK 的不能再 ACK，已复归的不能再 ACK，这些约束都在 `AlarmEventServiceImpl` 里走 `AlarmStateMachine` 校验，Controller 只做透传。

### 4.6 告警规则 Controller：get 返回 SaveReqVO

文件：`yudao-module-scada/src/main/java/cn/iocoder/yudao/module/scada/controller/admin/rule/ScadaAlarmRuleController.java`

```java
@GetMapping("/get")
@Operation(summary = "获取规则详情")
@Parameter(name = "id", description = "规则 ID", required = true)
public CommonResult<ScadaAlarmRuleSaveReqVO> get(@RequestParam("id") Long id) {
    // 规则字段与 SaveReqVO 完全对齐，直接复用
    ScadaAlarmRuleDO rule = ruleService.getRule(id);
    return success(rule == null ? null : convertSave(rule));
}
```

注释里说得很清楚："规则字段与 SaveReqVO 完全对齐，直接复用"。如果哪天规则字段集和编辑表单字段集开始分叉，再拆 `RespReqVO` 也不晚。这是"不要为了设计而设计"的典型例子。

规则 Controller 还有另外一个隐藏特性：增删改后由 `AlarmRuleService` 内部触发 `alarmEngine.reloadRules()`，不暴露单独的 reload 接口——避免前端绕过 CRUD 直接刷规则缓存。这个细节我留在第 10 章讲。

### 4.7 点位值 Controller：history / downsample / stream SSE

文件：`yudao-module-scada/src/main/java/cn/iocoder/yudao/module/scada/controller/admin/value/ScadaPointValueController.java`

```java
@GetMapping("/history")
@Operation(summary = "历史曲线查询")
public CommonResult<List<PointValueDO>> history(@Valid ScadaPointValueHistoryReqVO reqVO) {
    int limit = reqVO.getLimit() == null ? 5000 : reqVO.getLimit();
    return success(valueService.queryHistory(reqVO.getTag(), reqVO.getFrom(), reqVO.getTo(), limit));
}

@GetMapping(value = "/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
@Operation(summary = "实时值流订阅（SSE）")
public SseEmitter stream(@RequestParam("pointIds") String pointIds) {
    Set<Long> idSet = parseIds(pointIds);
    String clientId = UUID.randomUUID().toString();
    return pushService.subscribe(clientId, idSet);
}
```

`/history` 是同步查询 TDengine，最多 5000 条；`/stream` 是 SSE 长连接，前端用 `EventSource` 订阅。注意 `/stream` 的返回类型是 `SseEmitter`，没有 `CommonResult` 包装——理由前面讲过，长连接流式对象不能套单次响应包装。

`pointIds` 也是字符串拼接过来的，用 `,` 分隔，在 Controller 里 `parseIds` 解析。这跟 `pointIdsJson` 的设计哲学一致：能传字符串就不反序列化。

## 五、调优与踩坑

**踩坑 1：toggleEnabled 必须单独走。** 这个前面讲过，再强调一遍——`update` 接口里 `BeanUtils.copyProperties` 会把前端没传的字段当 null，统计字段一旦被覆盖，看板上"任务累计执行 N 次"会突然变回 0，老板会问你"为啥重启了"。

**踩坑 2：VO 转换不能用 MapStruct 自动 inject "运行时字段"。** 最早我想用 MapStruct 把 `ScadaDeviceDO` 自动映射到 `RespReqVO`，结果 MapStruct 对 `lastHeartbeatAt` 这种字段类型做了 Date↔LocalDateTime 的强制转换，运行时直接抛 `IllegalArgumentException`。后来全部改回 `BeanUtils.copyProperties`，丑但稳。

**调优：list-by-device 加复合索引。** 一次客户现场有 200 台设备，每台 500 个点位，前端打开设备树瞬间发 200 个 list-by-device 请求。原代码只走 `idx_device`，SQL 平均 200ms。改成 `idx_device_enabled` 复合索引后压到 20ms。索引设计要按查询路径走，不是按字段独立走。

**调优：ack/clear 加乐观锁。** 告警事件并发 ACK 在大屏多人操作时偶发，最初没加 version 字段，两次 ACK 同时进来，状态机被破。后来在 `scada_alarm_event` 表加了 `version` 字段（这版代码省略了，我承认），并发 ACK 会有一次失败，前端重试即可。

## 六、商用化检查清单

上线前对照跑一遍：

- [ ] 6 个 Controller 全部过 Swagger，字段对得上前端表单
- [ ] SaveReqVO 的 `@NotNull`/`@NotEmpty` 注解覆盖率 100%
- [ ] RespReqVO 不暴露 `deleted`/`tenantId`/`creator` 等内部字段
- [ ] `toggleEnabled` 是否有审计日志（谁启停了哪台设备）
- [ ] ack/clear 是否有 `version` 字段或乐观锁
- [ ] SSE `/stream` 是否在网关层配置长连接超时（30min）
- [ ] `X-Operator` 网关是否正确填充登录用户 ID
- [ ] `pointIdsJson` 长度上限（建议 4KB，防 SQL 截断）
- [ ] `/history` 的 limit 是否做了硬上限（防前端误传 100w 拖垮 TDengine）

---

## 下章预告

Controller 是用户能看见的，但用户真正看不见的是这 6 个接口背后的配置：TDengine 走哪个 host、线程池几条线程、SSE 超时多久、心跳阈值多少秒。下一章《09-配置与部署》会扒开 `application-scada.yaml` 全部配置块，讲为什么 TDengine 走 `${SCADA_TDENGINE_HOST:127.0.0.1}` 占位符、为什么 `scadaCollectExecutor` 不复用 yudao 全局池、为什么用 j2mod 不用 modbus4j——一次把部署相关的取舍讲完。
