package cn.iocoder.yudao.module.scada.service.device;

import cn.iocoder.yudao.module.scada.controller.admin.device.vo.ScadaDevicePageReqVO;
import cn.iocoder.yudao.module.scada.controller.admin.device.vo.ScadaDeviceSaveReqVO;
import cn.iocoder.yudao.module.scada.dal.dataobject.device.ScadaDeviceDO;

import java.util.List;

/**
 * SCADA 设备服务接口
 */
public interface ScadaDeviceService {

    Long createDevice(ScadaDeviceSaveReqVO reqVO);

    void updateDevice(ScadaDeviceSaveReqVO reqVO);

    void deleteDevice(Long id);

    ScadaDeviceDO getDevice(Long id);

    List<ScadaDeviceDO> getDeviceList(ScadaDevicePageReqVO reqVO);

    /** 设备启停 */
    void toggleEnabled(Long id, Boolean enabled);
}
