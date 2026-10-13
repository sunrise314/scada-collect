package cn.iocoder.yudao.module.scada.dal.mysql.point;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.scada.dal.dataobject.point.ScadaPointDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * SCADA 点位 Mapper
 *
 * <p>点位查询是采集执行器最高频的路径，避免 N+1：
 * 一次任务执行前按 pointIds 批量加载，缓存进 CollectContext。</p>
 */
@Mapper
public interface ScadaPointMapper extends BaseMapperX<ScadaPointDO> {

    /** 按设备 ID 列启用点位，Driver 重连后预加载 */
    default List<ScadaPointDO> selectListByDevice(Long deviceId) {
        return selectList(new LambdaQueryWrapperX<ScadaPointDO>()
                .eq(ScadaPointDO::getDeviceId, deviceId)
                .eq(ScadaPointDO::getEnabled, true));
    }

    /** 批量按 ID 加载，避免 N+1，采集任务执行时用 */
    default List<ScadaPointDO> selectListByIds(Collection<Long> ids) {
        return selectList(new LambdaQueryWrapperX<ScadaPointDO>()
                .in(ScadaPointDO::getId, ids));
    }

    /** tag 唯一性校验（同租户内） */
    default ScadaPointDO selectByTag(String tag) {
        return selectOne(new LambdaQueryWrapperX<ScadaPointDO>()
                .eq(ScadaPointDO::getTag, tag));
    }
}
