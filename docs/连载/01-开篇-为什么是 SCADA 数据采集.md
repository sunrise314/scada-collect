# 开篇：为什么是 SCADA 数据采集

## 一、痛点：工厂车间里的"数据孤岛"

我做工业软件这些年，去过不少中小制造工厂。每次走进车间，第一眼看到的总是墙上那排触摸屏 HMI，操作工盯着西门子、三菱、施耐德各家 PLC 的画面来回切。问厂长"这些产线数据能不能导出来做能耗分析、做良率追溯"，得到的回答十有八九是："厂家留了接口，但没人会调，最后还是 U 盘拷 CSV。"

这就是 SCADA（Supervisory Control and Data Acquisition，监控与数据采集）系统要解决的根本问题——把分散在几十台 PLC、变频器、数采网关里的过程数据，统一拉上来，存成时序，再喂给 MES、能管、BI 这些上层系统。

痛点是真实的：

- **协议割裂**：一台设备用 Modbus TCP，另一台用 OPC UA，老设备只有 S7 专有协议，新上的边缘网关推 MQTT。谁都不愿意改自己。
- **数据不落地**：PLC 内部寄存器是"转瞬即逝"的，掉电就丢。很多工厂出了质量事故想往回查半小时前的温度曲线，查不到。
- **商用 SCADA 太重**：西门子 WinCC、罗克韦尔 FactoryTalk，授权按点位数收费，一套下来几十万起步，还得配专业工程师驻场。
- **开源方案太碎**：ThingsBoard、Node-RED 能拼，但要拼出"万级点位 + 时序存储 + 告警联动 + 断线补采"这一整套，工程量不比自研小。

我接手的项目诉求很具体：一家中型注塑厂，4 台中央供料系统 + 12 台注塑机，总共 16 台 PLC，约 8000 个点位，要求 1 秒刷新一次，断网 1 小时内数据不丢，能复归后补采。预算只有商业方案的零头。

这就是 scada-collect 这个项目的由来。它基于芋道（yudao-vue-pro）脚手架二次开发，定位很明确：**覆盖中小制造工厂 95% 的工业现场需求，不引入 ThingsBoard 那种插件式 SPI + Netty Pipeline 的复杂度**。

## 二、行业对比：ThingsBoard / FactoryTalk / Ignition 怎么做

在决定自研前，我把市面上三类方案都摸了一遍。

**ThingsBoard（开源 IoT 平台）**。它的优势是插件式架构——传输层（Transport）用 Netty Pipeline，每种协议一个 TransportService，通过 SPI 加载，社区有 Modbus、OPC UA、MQTT、CoAP 等十几个实现。听起来很美，但代价是：要扩一个新协议你得懂 Netty、懂 SPI、懂它的设备模型（Device Profile）。对一个 4-16 台 PLC 的工厂来说，这是"用大炮打蚊子"。而且 ThingsBoard 的规则引擎走 JS 脚本，万级点位 1Hz 全量进规则链，性能撑不住，要么上集群要么降频。

**罗克韦尔 FactoryTalk**。商用闭源，强项是和 Allen-Bradley PLC 的深度集成（Logix 协议原生），画面组态、报警管理都是工业级。缺点也明显：协议基本只对自家友好，接第三方设备要走 OPC DA；授权贵；部署强绑定 Windows。

**Inductive Automation Ignition**。我个人比较欣赏的一款，模块化做得好，OPC UA 是一等公民，自带 MQTT Engine 能让边缘设备直接发 Sparkplug B。它的"按设备数授权"模式对中小厂友好。但它是 Java + Python(Jython) 混合架构，组态画面用 Perspective，二次开发要学它那套 IDE 和语法，对想深度定制告警引擎和推送协议的团队来说，还是隔了一层。

横向比完，结论很清楚：**商用方案解决的是"通用性 + 易用性"，开源方案解决的是"可扩展性"，但都没解决"中小工厂既要轻量部署、又要数据完整、还要能深度定制告警/推送"这个夹层需求。** 这正是 scada-collect 的空间。

## 三、选型与设计取舍

yudao-vue-pro 给我提供了多租户、权限、代码生成、MySQL + MyBatis-Plus 这些"地基层"能力，省掉两三周造轮子的活。我在它之上加了 `yudao-module-scada` 这个独立模块，对外只暴露 6 个 Controller，对内封装 4 个 Driver + 5 张表 + 1 个时序库。

几个核心取舍我先讲清楚，后面章节会逐个展开：

第一，**不引入 SPI 加载器**。ThingsBoard 那套 ServiceLoader + Netty Pipeline 太重，项目规模不需要插件式扩展。Spring `@Component` 注入 Driver 实现，IDE 跳转友好，加协议就是加个实现类，比写 META-INF/services 配置直观得多。

第二，**协议只覆盖四类**：Modbus TCP/RTU、OPC UA、S7-1200/1500、MQTT Sparkplug B。这是我调研下来中小工厂覆盖 95% 场景的最小集合。OPC DA、BACnet、IEC 61850 这些要么淘汰要么太专，不做。

第三，**时序库选 TDengine 3.x，不选 InfluxDB**。TDengine 一个超表 + 子表的模型和"一个点位一张子表"天然契合，写入 100w points/s 单机够用，而且它原生支持降采样，做历史曲线不用自己 rolling。

第四，**实时推送用 SSE 不用 WebSocket**。95% 的实时推送场景是单向的（服务端推值给前端），SSE 走 HTTP 兼容性更好，nginx 反代不需要额外配 Upgrade 头。

第五，**单实例部署，不做多 Master 互备**。工业现场一台采集服务器够了，多活要引入分布式锁，工厂用不到。ReplayBuffer 单机 RocksDB，重启不丢但跨节点不共享——这个限制我明明白白写进 README。

## 四、领域模型：五张表说清整个系统

我不想画那种密密麻麻的 ER 图。整个 SCADA 业务模型就 5 张表，全部 DDL 在 `yudao-module-scada/src/main/resources/sql/scada-init.sql`。时序数据不入 MySQL，走 TDengine。所有表都带 `tenant_id`、`creator`、`create_time`、`updater`、`update_time`、`deleted` 这套芋道标准字段，多租户拦截器自动注入。

第一张是设备表 `scada_device`。关键字段是 `protocol_type`（TINYINT，10=ModbusTCP/20=OPCUA/30=S7-1200/40=MQTTSparkplug）和 `conn_params_json`（TEXT，按协议存不同结构的连接参数）。状态字段 `status` 用 TINYINT 编码 0-5 六个状态（禁用/未知/连接中/已连接/重连中/错误），这就是后面断线重连状态机的全部状态空间。唯一索引 `uk_tenant_code` 保证设备编码在租户内唯一。

第二张是点位表 `scada_point`。这是整个系统的核心，一个点位对应一台设备上的一个传感器/寄存器/标签。`tag` 字段租户内唯一，直接用作 TDengine 子表名；`address` 存协议地址（Modbus 是 HR40001、OPC UA 是 ns=2;s=Tag1、S7 是 DB1.DBX0.0）；`scale`/`offset` 做工程量转换；`dead_band` 是死区百分比；`scan_rate_ms` 是扫描周期。一台设备典型有 100-5000 个点位，超过 5000 我建议拆子设备。

第三张是采集任务表 `scada_collect_task`。一个任务 = 一台设备 + 一组点位 + 一个采集周期。`task_type` 分 POLL/SUBSCRIBE 两种，`point_ids_json` 用 JSON 数组存关联点位 ID，避免任务-点位关联表的 N+N 查询。`run_count`/`error_count` 累计统计，`last_run_at` 是调度器判断"到没到点"的依据，索引 `idx_enabled_due (enabled, last_run_at)` 是调度扫表的核心索引。

第四、五张是告警规则表 `scada_alarm_rule` 和告警事件表 `scada_alarm_event`。规则挂点位上，支持 THRESHOLD/BAND/RATE 三种类型，带 `on_delay_ms`/`off_delay_ms` 做抖动抑制（触发条件持续 N 毫秒才告警，复归条件持续 M 毫秒才解除）。事件状态 0=正常/1=未确认/2=已确认/3=复归未确认/9=已屏蔽，这是 ISA-18.2 报警管理标准的简化版三态闭环。

五张表，没有多余的关联表，没有冗余的字典表。业务模型一旦收敛，后面所有代码都顺着这个结构长。

## 五、调优与踩坑

开工前两个月我踩过几个坑，提前给后人留个记号。

一是**多租户和 TDengine 子表名的冲突**。TDengine 子表名不能含中文字符，而 yudao 的租户拦截器会给所有查询注入 `tenant_id` 条件。如果让 tag 跨租户全局唯一，子表名冲突反而难处理。最后定的方案是 tag 在租户内唯一（`uk_tenant_tag (tenant_id, tag, deleted)`），子表名直接用 tag，不掺租户前缀，因为同一个 tag 只可能属于一个租户。

二是**MySQL 时区坑**。`last_run_at` 用 DATETIME 存，调度器判断"到没到点"时要把它转毫秒和 `System.currentTimeMillis()` 比。一开始我直接用 `LocalDateTime.now()` 和数据库取出来的值比，没转时区，结果东八区的工厂任务总是慢 8 秒触发。后来统一在 `CollectTaskExecutor.isDue()` 里走 `atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()`。

三是**连接池 maxTotalPerKey 不能 >1**。这点第二章重点讲，工业现场一台 PLC 不允许并发报文，否则报文交错解析错乱。我一开始图省事设成 4，结果某品牌变频器直接报"从站忙"错误。

## 六、商用化检查清单

交付一个 SCADA 项目，上线前我会过一遍这几条：

- 时序库写入吞吐压测过吗？万级点位 1Hz 是 1w points/s，TDengine 单节点 100w/s，余量 100 倍，但要看你有没有打超表超级表标签的冗余列。
- 断线补采验证过吗？拔网线 5 分钟，看 ReplayBuffer 里有没有数据，插回网线后历史曲线是否补齐。
- 告警抖动抑制验证过吗？写个脚本让某点位值在阈值上下抖动，看是不是 on_delay 把误报挡掉了。
- SSE 单连接推送点位数有没有超过 500？超过了前端要分屏，否则单帧 JSON 太大会卡。
- PLC 侧的连接数配额够吗？西门子 S7-1200 默认只允许 8 个连接，SCADA 占一个、HMI 占一个、调试工程师再占一个就满了。
- 反控写权限有没有二次确认？反控是"人命关天"的功能，建议前端弹框 + 后端日志双保险。

到这里，整体骨架立住了。但 5 张表只是领域模型，真正把数据从 PLC 拉上来，靠的是下一章要讲的——多协议接入适配器。我会拆开 Modbus、OPC UA、S7、MQTT 四个 Driver，讲清楚为什么一台设备只允许一个连接、为什么不用 SPI、批量合并怎么把 100 个点位压成 2 个报文。那才是这个项目最硬的一块。
