package cn.iocoder.yudao.module.scada.service.point;

import cn.iocoder.yudao.module.scada.controller.admin.point.vo.ScadaPointPageReqVO;
import cn.iocoder.yudao.module.scada.controller.admin.point.vo.ScadaPointSaveReqVO;
import cn.iocoder.yudao.module.scada.dal.dataobject.point.ScadaPointDO;

import java.util.List;

/**
 * SCADA 点位服务接口
 */
public interface ScadaPointService {

    Long createPoint(ScadaPointSaveReqVO reqVO);

    void updatePoint(ScadaPointSaveReqVO reqVO);

    void deletePoint(Long id);

    ScadaPointDO getPoint(Long id);

    List<ScadaPointDO> getPointList(ScadaPointPageReqVO reqVO);

    /** 列出某设备所有点位，Driver 重连后预加载 */
    List<ScadaPointDO> listByDevice(Long deviceId);
}
