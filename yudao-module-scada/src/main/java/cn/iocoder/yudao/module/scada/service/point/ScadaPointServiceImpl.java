package cn.iocoder.yudao.module.scada.service.point;

import cn.iocoder.yudao.module.scada.controller.admin.point.vo.ScadaPointPageReqVO;
import cn.iocoder.yudao.module.scada.controller.admin.point.vo.ScadaPointSaveReqVO;
import cn.iocoder.yudao.module.scada.dal.dataobject.point.ScadaPointDO;
import cn.iocoder.yudao.module.scada.dal.mysql.point.ScadaPointMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SCADA 点位服务实现
 *
 * <p>tag 是租户内唯一约束，新建/更新时校验。商用版应支持 Excel 批量导入点位，
 * 这里只提供单条 CRUD。</p>
 */
@Slf4j
@Service
public class ScadaPointServiceImpl implements ScadaPointService {

    private final ScadaPointMapper pointMapper;

    public ScadaPointServiceImpl(ScadaPointMapper pointMapper) {
        this.pointMapper = pointMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPoint(ScadaPointSaveReqVO reqVO) {
        // tag 唯一性校验
        if (reqVO.getTag() != null && pointMapper.selectByTag(reqVO.getTag()) != null) {
            throw new RuntimeException("tag 已存在: " + reqVO.getTag());
        }
        ScadaPointDO point = new ScadaPointDO();
        BeanUtils.copyProperties(reqVO, point);
        pointMapper.insert(point);
        return point.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePoint(ScadaPointSaveReqVO reqVO) {
        ScadaPointDO point = new ScadaPointDO();
        BeanUtils.copyProperties(reqVO, point);
        pointMapper.updateById(point);
    }

    @Override
    public void deletePoint(Long id) {
        pointMapper.deleteById(id);
    }

    @Override
    public ScadaPointDO getPoint(Long id) {
        return pointMapper.selectById(id);
    }

    @Override
    public List<ScadaPointDO> getPointList(ScadaPointPageReqVO reqVO) {
        if (reqVO.getDeviceId() != null) {
            return pointMapper.selectListByDevice(reqVO.getDeviceId());
        }
        return pointMapper.selectList();
    }

    @Override
    public List<ScadaPointDO> listByDevice(Long deviceId) {
        return pointMapper.selectListByDevice(deviceId);
    }
}
