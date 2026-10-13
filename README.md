# scada-collect

基于 yudao-vue-pro（芋道）二次改造的商用级 SCADA 设备数据采集平台。

## 定位

中小制造工厂的多协议设备数据采集、时序存储、实时曲线推送与告警联动。覆盖 95% 的工业现场需求，不引入 ThingsBoard 那种插件式 SPI + Netty Pipeline 的复杂度。

## 功能边界

| 能力 | 实现 | 不做 |
|------|------|------|
| 协议接入 | Modbus TCP/RTU、OPC UA、S7-1200/1500、MQTT Sparkplug B | OPC DA、BACnet、IEC 61850 |
| 点位模型 | 元数据 + 工程量转换 + 死区 + 扫描周期 | 跨设备点位共享 |
| 采集调度 | 1Hz 扫表 + 线程池 + in-flight 防重 | 复杂 DAG 调度 |
| 断线重连 | 状态机 + 指数退避 + ReplayBuffer 补采 | 多 Master 互备 |
| 时序库 | TDengine 3.x（超表 + 子表） | InfluxDB/IoTDB 切换 |
| 告警引擎 | ISA-18.2 简化版 + on/off-delay 抖动抑制 | 复杂事件流 CEP |
| 推送 | SSE 1Hz 聚合 + 客户端订阅点位 | WebSocket |
| 反控 | 单点 writeOne（Modbus/S7/OPC UA） | 批量反控 |

## 模块结构

```
yudao-module-scada/
├── framework/scada/
│   ├── driver/          # 4 个协议 Driver + DriverManager（Commons-Pool2）
│   ├── tdengine/       # 独立数据源 + 手写 JDBC SqlMapper
│   ├── alarm/          # ISA-18.2 状态机
│   ├── sse/            # SSE 推送服务端
│   ├── protocol/       # PointValue record
│   └── config/        # ScadaConfiguration + 线程池装配
├── service/
│   ├── device/         # 设备 CRUD + 启停
│   ├── point/          # 点位 CRUD + tag 唯一性
│   ├── task/           # 采集任务 CRUD + 启停
│   ├── collect/        # 调度执行器 + 重连管理 + 补采缓存
│   ├── value/          # 写 TDengine + 历史曲线
│   ├── alarm/          # 告警引擎 + 规则/事件 CRUD
│   └── push/          # 实时推送薄壳
├── controller/admin/   # 6 个 Controller（device/point/task/rule/event/value）
├── dal/
│   ├── dataobject/    # 5 DO + 1 DTO
│   └── mysql/         # 5 Mapper
├── enums/              # 6 枚举
├── job/                # 启动器 ScadaCollectJob
└── resources/
    ├── application-scada.yaml
    └── sql/scada-init.sql
```

## 部署

### 1. 准备中间件

- TDengine 3.3.x（必装，时序库）
- MySQL 8.x（业务库，跟随 yudao 主库）
- MQTT Broker（可选，仅 MQTT Sparkplug 协议时）
- 不需要 Redis（状态机走内存，规则缓存走内存）

### 2. 配置环境变量

```bash
export SCADA_TDENGINE_HOST=127.0.0.1
export SCADA_TDENGINE_USER=root
export SCADA_TDENGINE_PASS=taosdata
export SCADA_MQTT_URL=tcp://127.0.0.1:1883
```

### 3. 引入 Profile

在 yudao-server 主 `application.yaml` 加：

```yaml
spring:
  profiles:
    include: scada
```

### 4. 初始化 MySQL 表

```bash
mysql -uyudao -p yudao < yudao-module-scada/src/main/resources/sql/scada-init.sql
```

TDengine 超表会由 `PointValueServiceImpl.initSchema()` 启动时自动建。

### 5. maven 打包

`scada-collect/pom.xml` 已配 parent 指向 yudao 2.4.0-jdk8-snapshot，
把 `yudao-module-scada` 作为子模块加到 yudao 根 `pom.xml` 的 `<modules>` 即可：
```xml
<module>scada-collect/yudao-module-scada</module>
```

## 商用限制

- 单实例部署，不支持多 Master 互备（多活需要分布式锁，工业现场用不到）
- ReplayBuffer 单机 RocksDB，重启不丢但跨节点不共享
- TDengine 写入吞吐上限约 100w points/s（单节点），万级点位 1Hz 采集绰绰有余
- SSE 最大 500 点位/客户端，前端需要分屏

## 设计原则

1. **不引入 SPI 加载器**——Spring `@Component` 注入更直接，IDE 跳转友好
2. **Commons-Pool2 池化连接 key=deviceId**，一台设备一个连接，工业现场不允许并发报文
3. **TDengine 不走 MyBatis-Plus 拦截器**，单独 framework/scada/tdengine 包，手动 JDBC
4. **SSE 而非 WebSocket**——95% 实时推送场景 SSE 更优，单向流 + HTTP 兼容性好
5. **告警走 ISA-18.2 状态机**——UNACK/ACKED/RTN_UNACK 三态闭环，不漏不重
6. **抖动抑制 on-delay/off-delay**——触发条件持续 N 毫秒才告警，复归条件持续 M 毫秒才解除

## 作者

sunrise314 / 1468533577@qq.com
