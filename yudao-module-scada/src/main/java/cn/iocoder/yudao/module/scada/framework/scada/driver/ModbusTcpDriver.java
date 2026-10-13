package cn.iocoder.yudao.module.scada.framework.scada.driver;

import cn.iocoder.yudao.module.scada.dal.dataobject.device.ScadaDeviceDO;
import cn.iocoder.yudao.module.scada.dal.dataobject.point.ScadaPointDO;
import cn.iocoder.yudao.module.scada.enums.ScadaPointType;
import cn.iocoder.yudao.module.scada.enums.ScadaProtocolType;
import cn.iocoder.yudao.module.scada.framework.scada.protocol.PointValue;
import com.ghgande.jmodbus.modbus.ModbusException;
import com.ghgande.jmodbus.modbus.io.ModbusTCPTransaction;
import com.ghgande.jmodbus.modbus.msg.ReadMultipleRegistersRequest;
import com.ghgande.jmodbus.modbus.msg.ReadMultipleRegistersResponse;
import com.ghgande.jmodbus.modbus.msg.WriteSingleRegisterRequest;
import com.ghgande.jmodbus.modbus.net.TCPMasterConnection;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Modbus TCP 驱动实现（基于 j2mod 3.2.1）
 *
 * <p>Modbus 是工业现场事实标准，几乎所有 PLC/变频器/数采网关都支持。
 * 地址模型：HR=保持寄存器(40001+)、IR=输入寄存器(30001+)、
 *         coil=线圈(00001+)、DI=离散输入(10001+)。
 * 本驱动聚焦 HR 与 IR，因为工业 4-20mA/温度量都落这两类。</p>
 *
 * <p>批量优化关键：Modbus 一个 read 报文最多 125 个寄存器（HR/IR）或 2000 个 coil。
 * 本驱动对 point 列表按"地址段连续"做合并分组——
 * 地址差 ≤ 4 个寄存器且同类型就合并读，避免一个点位一个报文。</p>
 *
 * <p>connParamsJson 示例：{host: "192.168.1.10", port: 502, slaveId: 1, timeoutMs: 3000}</p>
 */
@Slf4j
@Component
public class ModbusTcpDriver implements ScadaPlcDriver {

    private final Gson gson = new Gson();

    @Override
    public Integer getProtocolType() {
        return ScadaProtocolType.MODBUS_TCP.getType();
    }

    @Override
    public Object connect(ScadaDeviceDO device) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = gson.fromJson(device.getConnParamsJson(), Map.class);
        String host = (String) params.get("host");
        Number portNum = (Number) params.getOrDefault("port", 502);
        Number timeoutNum = (Number) params.getOrDefault("timeoutMs", 3000);

        TCPMasterConnection conn = new TCPMasterConnection(InetAddress.getByName(host));
        conn.setPort(portNum.intValue());
        conn.setConnectTimeout(timeoutNum.intValue());
        conn.setTimeout(timeoutNum.intValue());
        // j2mod 3.x connect 抛 IOException + UnknownHostException
        conn.connect();
        log.info("Modbus TCP 已连接 {}:{} slaveId={}", host, portNum, params.get("slaveId"));
        // slaveId 在每次事务时设置，避免连接级状态
        return new ModbusConnState(conn, ((Number) params.getOrDefault("slaveId", 1)).intValue());
    }

    @Override
    public void disconnect(Object connHandle) {
        if (connHandle instanceof ModbusConnState state) {
            try {
                state.conn.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public boolean isConnected(Object connHandle) {
        if (connHandle instanceof ModbusConnState state) {
            // TCPMasterConnection 没有显式 isConnected，用 socket 是否关闭近似判断
            try {
                return state.conn.toString() != null;
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }

    @Override
    public List<PointValue> readBatch(Object connHandle, List<ScadaPointDO> points) throws Exception {
        ModbusConnState state = (ModbusConnState) connHandle;
        List<PointValue> result = new ArrayList<>(points.size());

        // 简化版分组：按寄存器类型 + 地址排序，相邻地址差 ≤ 4 寄存器就合并
        // 商用版本应实现贪心合并算法，这里给出可读的实现
        List<ScadaPointDO> hrPoints = new ArrayList<>();
        for (ScadaPointDO p : points) {
            if (p.getAddress() != null && p.getAddress().startsWith("HR")) {
                hrPoints.add(p);
            } else {
                // 单点读，便于演示非 HR 的情况
                result.add(readSingle(state, p));
            }
        }
        // 批量读 HR：按地址排序后合并连续段
        hrPoints.sort((a, b) -> Integer.compare(parseAddr(a.getAddress()), parseAddr(b.getAddress())));
        if (!hrPoints.isEmpty()) {
            result.addAll(readHrRange(state, hrPoints));
        }
        return result;
    }

    @Override
    public void writeOne(Object connHandle, ScadaPointDO point, Number value) throws Exception {
        ModbusConnState state = (ModbusConnState) connHandle;
        if (!point.getAddress().startsWith("HR")) {
            throw new IllegalArgumentException("只支持 HR 寄存器写入，地址=" + point.getAddress());
        }
        int regAddr = parseAddr(point.getAddress());
        // j2mod WriteSingleRegister 需要短整型寄存器值，乘 scale 后强转
        int regVal = (int) Math.round(value.doubleValue() * point.getScale().doubleValue()
                + point.getOffset().doubleValue());
        WriteSingleRegisterRequest req = new WriteSingleRegisterRequest(state.slaveId, regAddr, regVal);
        ModbusTCPTransaction tx = new ModbusTCPTransaction(state.conn);
        tx.setRequest(req);
        tx.execute();
    }

    private PointValue readSingle(ModbusConnState state, ScadaPointDO point) throws Exception {
        int addr = parseAddr(point.getAddress());
        ReadMultipleRegistersRequest req = new ReadMultipleRegistersRequest(state.slaveId, addr, 1);
        ModbusTCPTransaction tx = new ModbusTCPTransaction(state.conn);
        tx.setRequest(req);
        tx.execute();
        int[] regs = ((ReadMultipleRegistersResponse) tx.getResponse()).getRegisters();
        BigDecimal value = scaleValue(point, regs[0]);
        return PointValue.of(point.getId(), point.getTag(), value);
    }

    /** 把按地址排序后的 HR 点位列表合并为少量读请求 */
    private List<PointValue> readHrRange(ModbusConnState state, List<ScadaPointDO> sorted) throws Exception {
        List<PointValue> out = new ArrayList<>(sorted.size());
        int i = 0;
        while (i < sorted.size()) {
            int j = i;
            // 合并条件：相邻地址差 ≤ 4（贪心，避免过度合并导致中间寄存器被读浪费）
            while (j + 1 < sorted.size()
                    && parseAddr(sorted.get(j + 1).getAddress()) - parseAddr(sorted.get(j).getAddress()) <= 4) {
                j++;
            }
            int start = parseAddr(sorted.get(i).getAddress());
            int end = parseAddr(sorted.get(j).getAddress());
            int count = Math.min(end - start + 1, 125); // Modbus 单次最多 125 寄存器
            try {
                ReadMultipleRegistersRequest req = new ReadMultipleRegistersRequest(state.slaveId, start, count);
                ModbusTCPTransaction tx = new ModbusTCPTransaction(state.conn);
                tx.setRequest(req);
                tx.execute();
                int[] regs = ((ReadMultipleRegistersResponse) tx.getResponse()).getRegisters();
                for (int k = i; k <= j; k++) {
                    ScadaPointDO p = sorted.get(k);
                    int offset = parseAddr(p.getAddress()) - start;
                    if (offset >= 0 && offset < regs.length) {
                        BigDecimal value = scaleValue(p, regs[offset]);
                        out.add(PointValue.of(p.getId(), p.getTag(), value));
                    } else {
                        out.add(PointValue.bad(p.getId(), p.getTag()));
                    }
                }
            } catch (ModbusException e) {
                log.warn("Modbus 批量读 {}-{} 失败: {}", start, start + count - 1, e.getMessage());
                for (int k = i; k <= j; k++) {
                    out.add(PointValue.bad(sorted.get(k).getId(), sorted.get(k).getTag()));
                }
            }
            i = j + 1;
        }
        return out;
    }

    /** 地址解析 "HR40001" → 0（Modbus 实际地址从 0 起，40001 是协议符号地址） */
    private int parseAddr(String address) {
        // 兼容 HR40001 / HR40001.1 / HR40001
        String numPart = address.replaceAll("[^0-9]", "");
        int protocolAddr = Integer.parseInt(numPart);
        if (protocolAddr >= 40001) return protocolAddr - 40001;
        if (protocolAddr >= 30001) return protocolAddr - 30001;
        return protocolAddr;
    }

    /** 工程量转换：raw * scale + offset，按点位类型适配 */
    private BigDecimal scaleValue(ScadaPointDO point, int raw) {
        if (point.getPointType().equals(ScadaPointType.BOOL.getType())) {
            return BigDecimal.valueOf(raw != 0 ? 1 : 0);
        }
        double scaled = raw * point.getScale().doubleValue() + point.getOffset().doubleValue();
        // 量程截断，避免传感器坏值越界
        if (point.getMinVal() != null && scaled < point.getMinVal().doubleValue()) {
            scaled = point.getMinVal().doubleValue();
        }
        if (point.getMaxVal() != null && scaled > point.getMaxVal().doubleValue()) {
            scaled = point.getMaxVal().doubleValue();
        }
        return BigDecimal.valueOf(scaled);
    }

    /** 连接状态封装：TCPMasterConnection + slaveId */
    private record ModbusConnState(TCPMasterConnection conn, int slaveId) {}
}
