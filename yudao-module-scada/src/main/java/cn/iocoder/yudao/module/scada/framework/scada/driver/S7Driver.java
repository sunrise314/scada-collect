package cn.iocoder.yudao.module.scada.framework.scada.driver;

import cn.iocoder.yudao.module.scada.dal.dataobject.device.ScadaDeviceDO;
import cn.iocoder.yudao.module.scada.dal.dataobject.point.ScadaPointDO;
import cn.iocoder.yudao.module.scada.enums.ScadaPointType;
import cn.iocoder.yudao.module.scada.enums.ScadaProtocolType;
import cn.iocoder.yudao.module.scada.framework.scada.protocol.PointValue;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.PlcDriverManager;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * S7 PLC 驱动实现（基于 Apache PLC4X 0.13.0）
 *
 * <p>S7 是西门子 S7-200/300/400/1200/1500 系列专有协议，工业现场极其常见。
 * PLC4X 的好处是统一 API 抽象：把 S7、Modbus、OPC UA 都包成 PlcConnection，
 * 同样的 PlcReadRequest 接口。</p>
 *
 * <p>为什么用 PLC4X 而不是 snap7-javase？PLC4X 是 Apache 顶级项目，
 * 维护活跃，无 LGPL 污染；snap7 是 LGPL，商用闭源项目集成受限。</p>
 *
 * <p>S7 地址语法：DB1.DBX0.0（DB1 的第 0 个字节第 0 位，BOOL），
 *   DB1.DBW2（DB1 的第 2 个字，INT16），
 *   DB1.DBD4（DB1 的第 4 个双字，REAL32/FLOAT）。</p>
 *
 * <p>connParamsJson 示例：{host: "192.168.1.30", rack: 0, slot: 1}</p>
 *
 * <p>S7-1200 与 S7-1500 协议本质相同，区别仅在 rack/slot 默认值：
 * S7-1200 默认 rack=0 slot=1，S7-1500 也是 rack=0 slot=1。</p>
 */
@Slf4j
@Component
public class S7Driver implements ScadaPlcDriver {

    private final Gson gson = new Gson();

    @Override
    public Integer getProtocolType() {
        // 同时支持 S7-1200 与 S7-1500：让 DriverManager 注册两次
        // 但 DriverManager 一个 protocol 只能一个 Driver，这里返回 1200 主类型，
        // S7_1500 由上层 ScadaProtocolType.S7_1500 在 DeviceService 里映射到本 Driver
        // 简化处理：通过判断 device.getProtocolType() 在 connect 内兼容
        return ScadaProtocolType.S7_1200.getType();
    }

    @Override
    public Object connect(ScadaDeviceDO device) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = gson.fromJson(device.getConnParamsJson(), Map.class);
        String host = (String) params.get("host");
        Number rack = (Number) params.getOrDefault("rack", 0);
        Number slot = (Number) params.getOrDefault("slot", 1);
        // PLC4X S7 连接串：s7://192.168.1.30?rack=0&slot=1
        String connStr = String.format("s7://%s?rack=%d&slot=%d", host, rack.intValue(), slot.intValue());
        PlcConnection conn = PlcDriverManager.getDefault().getConnection(connStr);
        if (!conn.connect().get()) {
            throw new RuntimeException("S7 连接失败: " + connStr);
        }
        log.info("S7 已连接 {} rack={} slot={}", host, rack, slot);
        return conn;
    }

    @Override
    public void disconnect(Object connHandle) {
        if (connHandle instanceof PlcConnection c) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public boolean isConnected(Object connHandle) {
        if (connHandle instanceof PlcConnection c) {
            return c.isConnected();
        }
        return false;
    }

    @Override
    public List<PointValue> readBatch(Object connHandle, List<ScadaPointDO> points) throws Exception {
        PlcConnection conn = (PlcConnection) connHandle;
        PlcReadRequest.Builder builder = conn.readRequestBuilder();
        // PLC4X 字段名用 tag，地址用 point.address
        for (ScadaPointDO p : points) {
            builder.addTag(p.getTag(), p.getAddress());
        }
        PlcReadRequest req = builder.build();
        PlcReadResponse resp = req.execute().get();

        List<PointValue> result = new ArrayList<>(points.size());
        for (ScadaPointDO p : points) {
            try {
                Object raw = resp.getObject(p.getTag());
                BigDecimal value = convertValue(p, raw);
                result.add(PointValue.of(p.getId(), p.getTag(), value));
            } catch (Exception e) {
                log.warn("S7 读取 {} 失败: {}", p.getTag(), e.getMessage());
                result.add(PointValue.bad(p.getId(), p.getTag()));
            }
        }
        return result;
    }

    @Override
    public void writeOne(Object connHandle, ScadaPointDO point, Number value) throws Exception {
        PlcConnection conn = (PlcConnection) connHandle;
        conn.writeRequestBuilder()
                .addTag(point.getTag(), point.getAddress(), value)
                .build()
                .execute()
                .get();
    }

    private BigDecimal convertValue(ScadaPointDO point, Object raw) {
        if (raw == null) return BigDecimal.ZERO;
        if (point.getPointType().equals(ScadaPointType.BOOL.getType())) {
            return raw instanceof Boolean b
                    ? BigDecimal.valueOf(b ? 1 : 0)
                    : BigDecimal.valueOf(((Number) raw).intValue() != 0 ? 1 : 0);
        }
        if (raw instanceof Number n) {
            double scaled = n.doubleValue() * point.getScale().doubleValue()
                    + point.getOffset().doubleValue();
            // 量程截断
            if (point.getMinVal() != null && scaled < point.getMinVal().doubleValue()) {
                scaled = point.getMinVal().doubleValue();
            }
            if (point.getMaxVal() != null && scaled > point.getMaxVal().doubleValue()) {
                scaled = point.getMaxVal().doubleValue();
            }
            return BigDecimal.valueOf(scaled);
        }
        if (raw instanceof Boolean b) return BigDecimal.valueOf(b ? 1 : 0);
        if (raw instanceof String s) {
            try {
                return new BigDecimal(s);
            } catch (NumberFormatException e) {
                return BigDecimal.ZERO;
            }
        }
        return BigDecimal.ZERO;
    }
}
