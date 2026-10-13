package cn.iocoder.yudao.module.scada.framework.scada.driver;

import cn.iocoder.yudao.module.scada.dal.dataobject.device.ScadaDeviceDO;
import cn.iocoder.yudao.module.scada.dal.dataobject.point.ScadaPointDO;
import cn.iocoder.yudao.module.scada.framework.scada.protocol.PointValue;

import java.util.List;

/**
 * SCADA PLC 驱动接口
 *
 * <p>这是适配层抽象——所有协议（Modbus/OPC UA/S7/MQTT）实现这个接口，
 * 上层 {@link ScadaPlcDriverManager} 按 {@code device.protocolType} 路由到具体实现。</p>
 *
 * <p>设计取舍：</p>
 * <ul>
 *   <li>不引入 SPI 加载器（Java ServiceLoader）：项目规模不需要插件式扩展，
 *       Spring {@code @Component} 注入更直接，IDE 跳转友好</li>
 *   <li>不做"通用读地址"的 API：每种协议地址语法差异极大（HR/40001 vs ns=2;s=Foo vs DB1.DBX0.0），
 *       强行抽象会变成字符串解析地狱，让 Driver 实现自己解析 {@code point.address}</li>
 *   <li>connect/disconnect 显式分离：连接池由 DriverManager 管，
 *       Driver 只负责"建立 TCP/Session"，不负责池化</li>
 *   <li>readBatch 写批量：Modbus 一个报文读 125 个寄存器，
 *       OPC UA 一个 Publish 读一帧标签，批量是工业场景的甜点</li>
 * </ul>
 */
public interface ScadaPlcDriver {

    /** 该驱动支持的协议类型，见 {@link cn.iocoder.yudao.module.scada.enums.ScadaProtocolType} */
    Integer getProtocolType();

    /**
     * 建立到设备的连接。
     * @param device 设备元数据，{@code connParamsJson} 内含协议特定参数
     * @return 连接句柄对象，给后续 readBatch/writeOne 复用；失败抛 RuntimeException
     */
    Object connect(ScadaDeviceDO device) throws Exception;

    /** 主动断开连接，Driver 重启或设备 disabled 时调用 */
    void disconnect(Object connHandle);

    /** 是否已连接，DriverManager 借出连接前自检 */
    boolean isConnected(Object connHandle);

    /**
     * 批量读取点位值。Driver 内部应按协议特性聚合读：
     * Modbus 按连续地址段聚合，OPC UA 用已建立的订阅缓存。
     * @param connHandle connect 返回的句柄
     * @param points 待读点位，必属同一设备
     * @return 读到的点位值列表，长度可能小于 points（部分失败时 quality=BAD）
     */
    List<PointValue> readBatch(Object connHandle, List<ScadaPointDO> points) throws Exception;

    /**
     * 单点写入，用于反向控制（设定值下发）。
     * 默认抛 UnsupportedOperationException，只读协议（MQTT 订阅）无需实现。
     */
    default void writeOne(Object connHandle, ScadaPointDO point, Number value) throws Exception {
        throw new UnsupportedOperationException("Driver " + getProtocolType() + " 不支持写入");
    }
}
