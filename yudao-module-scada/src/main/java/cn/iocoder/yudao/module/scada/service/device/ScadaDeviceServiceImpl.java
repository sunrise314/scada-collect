package cn.iocoder.yudao.module.scada.service.device;

import cn.iocoder.yudao.module.scada.controller.admin.device.vo.ScadaDevicePageReqVO;
import cn.iocoder.yudao.module.scada.controller.admin.device.vo.ScadaDeviceSaveReqVO;
import cn.iocoder.yudao.module.scada.dal.dataobject.device.ScadaDeviceDO;
import cn.iocoder.yudao.module.scada.dal.mysql.device.ScadaDeviceMapper;
import cn.iocoder.yudao.module.scada.enums.ScadaDeviceStatus;
import cn.iocoder.yudao.module.scada.service.collect.DeviceConnectionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SCADA 设备服务实现
 *
 * <p>设备增删改后必须通知 {@link DeviceConnectionManager#refreshDevice}，
 * 让连接池清掉旧连接、状态机重置——否则改完参数还在用老连接。</p>
 */
@Slf4j
@Service
public class ScadaDeviceServiceImpl implements ScadaDeviceService {

    private final ScadaDeviceMapper deviceMapper;
    private final DeviceConnectionManager connectionManager;

    public ScadaDeviceServiceImpl(ScadaDeviceMapper deviceMapper,
                                  DeviceConnectionManager connectionManager) {
        this.deviceMapper = deviceMapper;
        this.connectionManager = connectionManager;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDevice(ScadaDeviceSaveReqVO reqVO) {
        ScadaDeviceDO device = new ScadaDeviceDO();
        BeanUtils.copyProperties(reqVO, device);
        device.setStatus(ScadaDeviceStatus.UNKNOWN.getState());
        deviceMapper.insert(device);
        connectionManager.refreshDevice(device);
        log.info("创建设备 {} id={}", device.getCode(), device.getId());
        return device.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDevice(ScadaDeviceSaveReqVO reqVO) {
        ScadaDeviceDO device = new ScadaDeviceDO();
        BeanUtils.copyProperties(reqVO, device);
        deviceMapper.updateById(device);
        // 重新加载完整 DO 喂给连接管理器
        ScadaDeviceDO full = deviceMapper.selectById(reqVO.getId());
        connectionManager.refreshDevice(full);
        log.info("更新设备 id={} code={}", device.getId(), device.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDevice(Long id) {
        ScadaDeviceDO device = deviceMapper.selectById(id);
        if (device == null) return;
        deviceMapper.deleteById(id);
        connectionManager.evictDevice(id);
        log.info("删除设备 id={} code={}", id, device.getCode());
    }

    @Override
    public ScadaDeviceDO getDevice(Long id) {
        return deviceMapper.selectById(id);
    }

    @Override
    public List<ScadaDeviceDO> getDeviceList(ScadaDevicePageReqVO reqVO) {
        return deviceMapper.selectList();
    }

    @Override
    public void toggleEnabled(Long id, Boolean enabled) {
        ScadaDeviceDO update = new ScadaDeviceDO();
        update.setId(id);
        update.setEnabled(enabled);
        update.setStatus(enabled ? ScadaDeviceStatus.UNKNOWN.getState()
                : ScadaDeviceStatus.DISABLED.getState());
        deviceMapper.updateById(update);
        if (Boolean.FALSE.equals(enabled)) {
            connectionManager.evictDevice(id);
        } else {
            connectionManager.refreshDevice(deviceMapper.selectById(id));
        }
        log.info("设备 {} 启停切换 enabled={}", id, enabled);
    }
}
