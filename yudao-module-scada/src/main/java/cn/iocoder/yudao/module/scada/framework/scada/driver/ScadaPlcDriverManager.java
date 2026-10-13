package cn.iocoder.yudao.module.scada.framework.scada.driver;

import cn.iocoder.yudao.module.scada.dal.dataobject.device.ScadaDeviceDO;
import cn.iocoder.yudao.module.scada.dal.dataobject.point.ScadaPointDO;
import cn.iocoder.yudao.module.scada.enums.ScadaDeviceStatus;
import cn.iocoder.yudao.module.scada.framework.scada.protocol.PointValue;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.KeyedObjectPool;
import org.apache.commons.pool2.KeyedPooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SCADA PLC 驱动管理器
 *
 * <p>三件事：</p>
 * <ol>
 *   <li>启动时把所有 {@link ScadaPlcDriver} 实现注册到 {@code driverByProtocol} map，
 *       按 {@code getProtocolType()} 路由</li>
 *   <li>Commons-Pool2 池化连接，key=设备 ID，value=协议连接句柄。
 *       池化让"一次任务执行借一个连接、用完归还"，避免每次 read 都重建 TCP</li>
 *   <li>失败重连：设备 status 状态机由 {@link cn.iocoder.yudao.module.scada.service.collect.DeviceConnectionManager} 接管，
 *       这里只负责池的借用与失效剔除</li>
 * </ol>
 *
 * <p>池参数取舍：maxTotalPerKey=1（一台设备一个连接，工业现场不允许并发报文，
 * 否则 PLC 会出现报文交错导致解析错误）；maxIdlePerKey=1；blockWhenExhausted=true
 * 但 maxWaitMillis=2000 防止线程死等。</p>
 *
 * <p>失效连接的回收：调用方 catch 到 IOException 后调用 {@code invalidateConnection}，
 * 池会触发 destroyObject 把连接物理关闭，下次 borrow 会重新 connect。</p>
 */
@Slf4j
@Component
public class ScadaPlcDriverManager {

    private final Map<Integer, ScadaPlcDriver> driverByProtocol = new HashMap<>();

    private final KeyedObjectPool<Long, Object> connectionPool;

    /** 失效连接追踪，避免反复打印重连日志 */
    private final ConcurrentHashMap<Long, Long> lastFailureLoggedAt = new ConcurrentHashMap<>();

    @Autowired
    public ScadaPlcDriverManager(List<ScadaPlcDriver> drivers) {
        // 注册所有 Driver 实现
        for (ScadaPlcDriver d : drivers) {
            ScadaPlcDriver prev = driverByProtocol.put(d.getProtocolType(), d);
            if (prev != null) {
                log.warn("协议类型 {} 的 Driver 实现被覆盖：{} -> {}",
                        d.getProtocolType(), prev.getClass().getSimpleName(), d.getClass().getSimpleName());
            }
            log.info("注册 SCADA Driver: protocol={} impl={}", d.getProtocolType(), d.getClass().getSimpleName());
        }

        // 配置连接池
        GenericKeyedObjectPoolConfig<Object> cfg = new GenericKeyedObjectPoolConfig<>();
        cfg.setMaxTotalPerKey(1);
        cfg.setMaxIdlePerKey(1);
        cfg.setMaxWait(Duration.ofMillis(2000));
        cfg.setMinEvictableIdle(Duration.ofMinutes(5));
        cfg.setBlockWhenExhausted(true);

        this.connectionPool = new GenericKeyedObjectPool<>(new KeyedPooledObjectFactory<>() {
            @Override
            public void activateObject(Long key, org.apache.commons.pool2.PooledObject<Object> p) {
                // 空实现，让 Driver 自检
            }

            @Override
            public boolean validateObject(Long key, org.apache.commons.pool2.PooledObject<Object> p) {
                ScadaDeviceDO device = deviceSnapshot.get(key);
                if (device == null) return false;
                ScadaPlcDriver driver = driverByProtocol.get(device.getProtocolType());
                return driver != null && driver.isConnected(p.getObject());
            }

            @Override
            public org.apache.commons.pool2.PooledObject<Object> makeObject(Long key) throws Exception {
                ScadaDeviceDO device = deviceSnapshot.get(key);
                if (device == null) {
                    throw new IllegalStateException("设备 " + key + " 不存在或已删除");
                }
                ScadaPlcDriver driver = driverByProtocol.get(device.getProtocolType());
                if (driver == null) {
                    throw new IllegalStateException("协议 " + device.getProtocolType() + " 无 Driver 实现");
                }
                Object handle = driver.connect(device);
                return new DefaultPooledObject<>(handle);
            }

            @Override
            public void destroyObject(Long key, org.apache.commons.pool2.PooledObject<Object> p) {
                ScadaDeviceDO device = deviceSnapshot.get(key);
                if (device == null) return;
                ScadaPlcDriver driver = driverByProtocol.get(device.getProtocolType());
                if (driver != null) {
                    try {
                        driver.disconnect(p.getObject());
                    } catch (Exception e) {
                        log.warn("销毁设备 {} 连接失败: {}", key, e.getMessage());
                    }
                }
            }

            @Override
            public void passivateObject(Long key, org.apache.commons.pool2.PooledObject<Object> p) {
                // 空实现
            }
        }, cfg);
    }

    /** 设备元数据快照，避免每次借连接都查 DB；由 DeviceConnectionManager 在设备变更时更新 */
    private final ConcurrentHashMap<Long, ScadaDeviceDO> deviceSnapshot = new ConcurrentHashMap<>();

    /** 设备增删改时由 DeviceService 调用，刷新快照并清池 */
    public void refreshDevice(ScadaDeviceDO device) {
        deviceSnapshot.put(device.getId(), device);
        try {
            connectionPool.clear(device.getId());
        } catch (Exception ignored) {
        }
    }

    public void evictDevice(Long deviceId) {
        deviceSnapshot.remove(deviceId);
        try {
            connectionPool.clear(deviceId);
        } catch (Exception ignored) {
        }
    }

    /**
     * 执行一次批量读：借连接 → readBatch → 归还。
     * 借连接失败会被上层 DeviceConnectionManager 捕获并切状态机到 RECONNECTING。
     */
    public List<PointValue> readBatch(ScadaDeviceDO device, List<ScadaPointDO> points) {
        ScadaPlcDriver driver = driverByProtocol.get(device.getProtocolType());
        if (driver == null) {
            throw new IllegalStateException("协议 " + device.getProtocolType() + " 无 Driver 实现");
        }
        Object conn = null;
        try {
            conn = connectionPool.borrowObject(device.getId());
            return driver.readBatch(conn, points);
        } catch (Exception e) {
            if (conn != null) {
                try {
                    connectionPool.invalidateObject(device.getId(), conn);
                } catch (Exception ignored) {
                }
                conn = null;
            }
            // 失败节流日志：1 秒内同一设备只打一次
            long now = System.currentTimeMillis();
            lastFailureLoggedAt.compute(device.getId(), (k, prev) -> {
                if (prev == null || now - prev > 1000) {
                    log.warn("设备 {} 读取失败: {}", device.getCode(), e.getMessage());
                    return now;
                }
                return prev;
            });
            throw new RuntimeException("设备 " + device.getCode() + " 读取失败: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    connectionPool.returnObject(device.getId(), conn);
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** 单点写，反向控制用 */
    public void writeOne(ScadaDeviceDO device, ScadaPointDO point, Number value) {
        ScadaPlcDriver driver = driverByProtocol.get(device.getProtocolType());
        if (driver == null) {
            throw new IllegalStateException("协议 " + device.getProtocolType() + " 无 Driver 实现");
        }
        Object conn = null;
        try {
            conn = connectionPool.borrowObject(device.getId());
            driver.writeOne(conn, point, value);
        } catch (Exception e) {
            if (conn != null) {
                try { connectionPool.invalidateObject(device.getId(), conn); } catch (Exception ignored) {}
                conn = null;
            }
            throw new RuntimeException("设备 " + device.getCode() + " 写入失败: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try { connectionPool.returnObject(device.getId(), conn); } catch (Exception ignored) {}
            }
        }
    }

    /** 触发某设备重连：先清池，下次借会重建连接 */
    public void reconnect(Long deviceId) {
        try {
            connectionPool.clear(deviceId);
        } catch (Exception ignored) {
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            connectionPool.close();
        } catch (Exception ignored) {
        }
        log.info("SCADA DriverManager 连接池已关闭");
    }
}
