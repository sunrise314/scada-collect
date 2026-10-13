package cn.iocoder.yudao.module.scada.framework.scada.tdengine;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * TDengine 数据源配置
 *
 * <p>独立于主库 DataSource：主库（yudao MySql）走 MyBatis-Plus 拦截器，
 * 这里走纯 JDBC，避免分页/逻辑删除拦截器污染 TDengine SQL。</p>
 *
 * <p>TDengine 的 JDBC 驱动比较特殊：</p>
 * <ul>
 *   <li>REST 接口驱动：jdbc:TAOS-RS://host:6041/db</li>
 *   <li>原生接口驱动：jdbc:TAOS://host:6030/db，需要本机安装 taos 客户端</li>
 *   <li>本配置默认用 REST：跨平台兼容性好，写吞吐稍弱但够 1Hz 采集</li>
 * </ul>
 *
 * <p>Hikari 连接池：maxPoolSize=10 够了——TDengine 写是按设备子表自动 batch，
 * 并发连接太多反而触发"一个连接一个写入节点"的争抢。商用版可加 prepareStatement 缓存。</p>
 *
 * <p>schema 初始化：超表 scada_point_value（tag, ts, val_num, val_str, quality），
 * 子表名 = point.tag，建子表时带上 tag 列值，加速按 tag 过滤。</p>
 */
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

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public int getMaxPoolSize() { return maxPoolSize; }
    public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
}
