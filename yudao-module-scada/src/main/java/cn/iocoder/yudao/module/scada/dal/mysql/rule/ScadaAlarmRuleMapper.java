package cn.iocoder.yudao.module.scada.dal.mysql.rule;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.scada.dal.dataobject.rule.ScadaAlarmRuleDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * SCADA 告警规则 Mapper
 *
 * <p>规则数量远小于点位（典型 1:50），全量加载进 AlarmEngine 内存即可，
 * 不需要复杂索引。规则变更走定时刷新而非实时订阅。</p>
 */
@Mapper
public interface ScadaAlarmRuleMapper extends BaseMapperX<ScadaAlarmRuleDO> {

    /** 列出某点位的所有启用规则 */
    default List<ScadaAlarmRuleDO> selectListByPoint(Long pointId) {
        return selectList(new LambdaQueryWrapperX<ScadaAlarmRuleDO>()
                .eq(ScadaAlarmRuleDO::getPointId, pointId)
                .eq(ScadaAlarmRuleDO::getEnabled, true));
    }

    /** 批量按点位加载规则，AlarmEngine 启动时调用 */
    default List<ScadaAlarmRuleDO> selectListByPoints(Collection<Long> pointIds) {
        return selectList(new LambdaQueryWrapperX<ScadaAlarmRuleDO>()
                .in(ScadaAlarmRuleDO::getPointId, pointIds)
                .eq(ScadaAlarmRuleDO::getEnabled, true));
    }

    /** 全量启用规则，AlarmEngine 全量重载 */
    default List<ScadaAlarmRuleDO> selectAllEnabled() {
        return selectList(new LambdaQueryWrapperX<ScadaAlarmRuleDO>()
                .eq(ScadaAlarmRuleDO::getEnabled, true));
    }
}
