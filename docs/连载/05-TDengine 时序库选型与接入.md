# 05 - TDengine 时序库选型与接入

## 一、痛点引入

前面几章我搭好了采集链路，把 PLC 的值顺利读到了 Java 进程里。但读出来放哪？这是一个比想象中难得多的问题。

最早我天真地想直接灌到 MySQL：每个点位每秒一条 `point_value` 表，1 个厂 1000 个点 1Hz，一天 8600 万行。MySQL 的 B+ 树索引在这种写入节奏下会疯狂页分裂，磁盘 IOPS 一上午打满；查一周历史曲线，要么走 `ts BETWEEN ? AND ?` 全表扫，要么做分区分表，运维成本爆炸。我同事真的在另一个项目里这么干过，最后那张表到了 6 亿行，单条 insert 耗时从 0.1ms 涨到 30ms，磁盘满了三次。

工业 SCADA 的存储需求其实特别明确：

- 写多读少，写入是稳定流式而非突发；
- 时间是天然主键，按时间范围查是绝对主流；
- 几乎不删除（删的也是老分区，按时间删而不是按 id 删）；
- 永远不做事务、不做 JOIN；
- 客户端要的是"最近 N 秒"或"某段时间降采样后的均值/最大值"，而不是单行精确查找。

这种 workload 跟传统 OLTP 完全相反，所以我需要专门的时序库。这一章就是我选 TDengine 的过程和接入的实战。

## 二、行业对比：TDengine vs InfluxDB vs IoTDB

选型时我认真对比了三家的特性：

| 维度 | TDengine | InfluxDB | IoTDB |
|---|---|---|---|
| 数据模型 | 超表+子表（设备建模友好） | measurement+tag+field | 设备+测点（树形 path） |
| 写入吞吐 | 单核 100w points/s（官方） | 单节点约 70w | 单机 100w+ |
| 集群 | 开源版即支持 | 社区版单节点，企业版收费 | 开源版支持 |
| Java 生态 | JDBC + 原生/REST 两套驱动 | 客户端非 JDBC | JDBC 风格 |
| 协议原生支持 | OpenTSDB / InfluxDB / MQTT | InfluxDB line proto | MQTT |
| 商用授权 | AGPL/商业 | MIT + 企业版 | Apache 2.0 |

我选 TDengine 主要三个原因：

第一，超表+子表模型跟 SCADA 的"设备-点位"概念几乎是 1:1 映射。一台设备一个子表，一个点位一条记录，加 TAGS 就能反查元数据。InfluxDB 的 measurement 一坨，没这个分层。

第二，开源版自带集群。InfluxDB 2.x 社区版单节点，商用部署要做高可用就只能上企业版，预算打不住。我同事在另一个项目里吃了这个亏——InfluxDB 单节点崩了半天没数据。

第三，JDBC 驱动。我可以直接用 Spring 的 JdbcTemplate，不用引入新 SDK。IoTDB 虽然也是 Apache 2.0 且 Java 友好，但 2024 年它的 REST 接口稳定性不如 TDengine，企业版功能也相对单薄。

## 三、选型与设计取舍

决定上 TDengine 后还有几个关键设计决策，我一个个说。

**独立数据源，不走 MyBatis-Plus**。Yudao 主库是 MySQL，整套 MyBatis-Plus 拦截器（分页插件、逻辑删除、字段加密、租户隔离）都挂在主 DataSource 上。如果 TDengine 也走主数据源，那些拦截器会对 TDengine 的 SQL 生效——比如逻辑删除会自动加 `deleted=0` 条件，TDengine 不认这个字段直接报错；分页插件会把 `LIMIT` 包成 `LIMIT ? OFFSET ?`，但 TDengine 的 LIMIT 语法跟 MySQL 不完全一致。所以我单独起一个 HikariDataSource，名字叫 `tdengineDataSource`，完全不进 MyBatis-Plus 的扫描路径。

**RESTful Driver 而不是原生 JDBC**。TDengine 有两套驱动：原生 `jdbc:TAOS://` 走 6030 端口，需要本机装 taos 客户端 .so 库；REST `jdbc:TAOS-RS://` 走 6041 端口，纯 HTTP。原生驱动吞吐量高三倍，但部署痛苦——Docker 镜像里要装 taosd-client，跨平台编译坑多。我选 REST 是因为容器化部署一搞就跑通，写吞吐虽然差点，但 SCADA 1Hz 采集够用，瓶颈永远不在 TDengine 这层。

**超表设计：双 TAG**。`TAGS(point_id, tag)` 看起来冗余，但很有用。`tag` 是点位的字符串编码（比如 `temp_oven_1`），用来做子表名和按 tag 过滤；`point_id` 是 MySQL 元数据表的外键，反查元数据时用。如果只留一个 tag，每次反查都得多一次字符串匹配；只留 point_id 又失去了人类可读的过滤能力。双 TAG 给业务层留了灵活性。

## 四、关键代码精讲

### 4.1 独立数据源配置

`yudao-module-scada/src/main/java/cn/iocoder/yudao/module/scada/framework/scada/tdengine/TDengineConfig.java`：

```java
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "scada.tdengine")
public class TDengineConfig {

    private String url = "jdbc:TAOS-RS://127.0.0.1:6041/scada";
    private String username = "root";
    private String password = "taosdata";
    private int maxPoolSize = 10;

    @Bean(name = "tdengineDataSource")
    public DataSource tdengineDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("com.taosdata.jdbc.rs.RestfulDriver");
        ds.setMaximumPoolSize(maxPoolSize);
        ds.setMinimumIdle(2);
        ds.setConnectionTimeout(5000);
        ds.setIdleTimeout(60_000);
        ds.setMaxLifetime(30 * 60_000);
        log.info("TDengine DataSource 已初始化 url={} maxPool={}", url, maxPoolSize);
        return ds;
    }
}
```

注意几个细节：

- `@Bean(name = "tdengineDataSource")` 显式指定 bean 名，避免跟主数据源冲突；
- `maxPoolSize=10` 不是随意定的。TDengine 写入是按设备子表自动 batch，并发连接越多反而越容易触发"一个连接一个写入节点"的争抢，10 个足够支撑 1Hz × 1000 点；
- `minimumIdle=2`，避免空闲时连接全断，下一次写入要重建 TCP；
- `maxLifetime=30min` 对齐 nginx 的 30min 上游超时，避免连接被中间网关静默关闭后还以为是好连接。

配置块在 `application-scada.yaml`：

```yaml
scada:
  tdengine:
    url: jdbc:TAOS-RS://${SCADA_TDENGINE_HOST:127.0.0.1}:${SCADA_TDENGINE_PORT:6041}/scada
    username: ${SCADA_TDENGINE_USER:root}
    password: ${SCADA_TDENGINE_PASS:taosdata}
    driver-class-name: com.taosdata.jdbc.rs.RestfulDriver
    max-pool-size: 10
    database: scada
```

生产环境通过 `SCADA_TDENGINE_HOST` 等环境变量注入，开发环境走默认值。

### 4.2 Schema 初始化

`yudao-module-scada/src/main/java/cn/iocoder/yudao/module/scada/framework/scada/tdengine/TDengineSqlMapper.java`：

```java
public void initSchema() {
    try {
        jdbc.execute("CREATE DATABASE IF NOT EXISTS scada KEEP 3650 DURATION 10 PRECISION 'ms'");
        jdbc.execute("USE scada");
        jdbc.execute("CREATE STABLE IF NOT EXISTS scada_point_value (" +
                "ts TIMESTAMP, val_num DOUBLE, val_str NCHAR(64), quality TINYINT" +
                ") TAGS (point_id BIGINT, tag NCHAR(64))");
        log.info("TDengine schema 已就绪");
    } catch (Exception e) {
        log.error("TDengine schema 初始化失败: {}", e.getMessage());
    }
}
```

几个关键参数：

- `KEEP 3650` 表示数据保留 3650 天（10 年），这是 TDengine 自动按时间分区+老数据回收的依据。SCADA 历史曲线经常要查"去年同期"做对比，10 年够用；
- `DURATION 10` 每 10 天一个数据文件，按时间分区裁剪；
- `PRECISION 'ms'` 毫秒精度。SCADA 1Hz 看似秒级就够，但 PLC 跳变值要分析相位时需要毫秒。

`CREATE STABLE IF NOT EXISTS` 是幂等的，所以 `@PostConstruct` 启动时调一次不会冲突。

### 4.3 批量写入的拼接式 SQL

这是整个 TDengine 接入最关键的一段代码，也是踩过最多坑的地方：

```java
public void batchInsert(List<PointValue> values) {
    if (values.isEmpty()) return;
    StringBuilder sb = new StringBuilder("INSERT INTO ");
    for (PointValue v : values) {
        sb.append("scada_point_value USING scada_point_value TAGS(")
                .append(v.pointId() == null ? "0" : v.pointId())
                .append(", '")
                .append(escapeTag(v.tag()))
                .append("') VALUES ");
        sb.append('(')
                .append(toEpochMs(v.ts())).append(", ")
                .append(v.valueNum() == null ? "NULL" : v.valueNum().doubleValue())
                .append(", ")
                .append(v.valueStr() == null ? "NULL" : "'" + escapeStr(v.valueStr()) + "'")
                .append(", ")
                .append(v.quality())
                .append(") ");
    }
    try {
        jdbc.execute(sb.toString());
    } catch (Exception e) {
        log.warn("TDengine 批量写失败 count={} err={}", values.size(), e.getMessage());
        throw new RuntimeException("TDengine 写入失败: " + e.getMessage(), e);
    }
}
```

最终拼出的 SQL 长这样：

```sql
INSERT INTO scada_point_value USING scada_point_value TAGS(101, 'temp_oven_1') VALUES (1718270000000, 85.3, NULL, 1) scada_point_value USING scada_point_value TAGS(102, 'press_pump_2') VALUES (1718270000000, 2.1, NULL, 1)
```

**为什么用拼字符串而不是 PreparedStatement batch？** 这是被现实教训打出来的设计。我最早用 `PreparedStatement.addBatch()` + `executeBatch()`，发现 TDengine REST 驱动的 JDBC 实现把 batch 拆成单条 HTTP 请求一条条发，N 次 RTT 性能差 100 倍。而拼 SQL 一次 HTTP 写完一批，TDengine 服务端按 USING 子句自动建子表+多 VALUES 批量插入，单次往返性能比 PreparedStatement 高两个数量级。

风险是 SQL 注入，所以我加了 `escapeStr`（单引号双写）和 `escapeTag`（非字母数字下划线全替换为下划线，避免子表名带特殊字符破坏 SQL）。子表名一定要干净，因为 TDengine 用 `p_{tag}` 风格命名子表，含特殊字符会直接 SQL 解析失败。

### 4.4 历史曲线与降采样查询

```java
public List<Object[]> queryHistory(String tag, LocalDateTime from, LocalDateTime to, int limit) {
    String sql = "SELECT ts, val_num FROM scada_point_value WHERE tag = ? AND ts >= ? AND ts <= ? " +
            "ORDER BY ts ASC LIMIT ?";
    return jdbc.query(sql, ...);
}

public List<Object[]> queryDownsample(String tag, LocalDateTime from, LocalDateTime to, int intervalMinutes) {
    String sql = String.format(
            "SELECT avg(val_num) FROM scada_point_value WHERE tag = ? AND ts >= ? AND ts <= ? " +
                    "INTERVAL(%d, 1m)", intervalMinutes);
    return jdbc.query(sql, ...);
}
```

`WHERE tag = ?` 自动路由到对应子表，TDengine 按 TAG 做存储分片，查询时只扫子表文件不扫超表，所以毫秒级返回。

`INTERVAL(5, 1m)` 是 TDengine 降采样的核心语法——5 分钟一个 bucket，每个 bucket 算 avg。这条 SQL 一次能把 1 小时的原始数据（3600 条）压成 12 个均值点，前端曲线渲染直接用，不用前端再 decimate。

## 五、调优与踩坑

**坑一：单节点写入 100w points/s 怎么算的**。这个数字是官方的，但要满足三个前提：原生驱动不走 REST、批量大、子表少。我实测 REST 驱动 1000 点 × 1Hz 才 1000 points/s，远没到瓶颈，瓶颈在 Hikari 连接数。如果业务真要 100w/s，得换原生驱动 + 大批 + 多子表并发。

**坑二：subtable 数量爆炸**。每个点位一个子表，1000 点位就是 1000 子表。TDengine 默认 `maxTablesPerVnode=1000`，超过会触发 vnode 分裂，启动慢且写入抖动。生产环境记得调大 `maxTablesPerVnode` 到 5000+。

**坑三：JdbcTemplate 用 `?` 占位符传 NCHAR 字段时驱动版本兼容**。早期我用 2.x 驱动，NCHAR 字段传中文会乱码；升到 3.x 驱动后正常。所以 `pom.xml` 里把 taos-jdbcdriver 锁版本到 3.x。

**坑四：escapeTag 的边界**。我之前在 tag 里塞过中文 `温度_1`，转义后变 `_1`，跟 `压力_1` 转义后的 `_1` 撞名，写入互相覆盖。后来规定 tag 全英文，由元数据表存中文映射，从根本上避免。

## 六、商用化检查清单

上线前我把 TDengine 接入过一遍商用化检查，列出来供参考：

- [ ] `KEEP` 与业务最长回溯期对齐（10 年/3 年）
- [ ] 子表数量上限预估，调整 `maxTablesPerVnode`
- [ ] 启动时 `initSchema` 失败是否 fail-fast 还是降级（我选降级 + 告警，避免 DBA 改库时整个服务挂）
- [ ] 写入失败走 ReplayBuffer 补采路径（章节 04 已实现 RocksDB 队列）
- [ ] Hikari `maxLifetime` 与 nginx/中间网关 upstream timeout 对齐
- [ ] tag 字符集白名单（仅字母数字下划线），从根上避免子表名冲突
- [ ] 数据库账号最小权限（应用账号只授 scada 库的 RW，不授系统库）
- [ ] 监控 TDengine `taosd` 进程 + REST 6041 端口存活
- [ ] 历史曲线 SQL 的 `LIMIT` 上限（我设 5000 条，防前端一次拉爆内存）
- [ ] 降采样 `INTERVAL` 在大跨度查询时强制走降采样，禁止全量原始值导出

---

到这里 TDengine 这条存储链路就闭环了：Driver 读到值 → PointValueService 写入超表 → 历史曲线/降采样查询走 TDengine。下一章我接着讲这条链路上最敏感的一段——告警引擎：怎么用 ISA-18.2 状态机管告警生命周期、怎么用 on-delay/off-delay 抑制 PLC 抖动，以及为什么规则必须全量驻内存。
