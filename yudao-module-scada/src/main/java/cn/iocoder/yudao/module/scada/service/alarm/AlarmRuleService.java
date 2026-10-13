package cn.iocoder.yudao.module.scada.service.alarm;

import cn.iocoder.yudao.module.scada.controller.admin.rule.vo.ScadaAlarmRulePageReqVO;
import cn.iocoder.yudao.module.scada.controller.admin.rule.vo.ScadaAlarmRuleSaveReqVO;
import cn.iocoder.yudao.module.scada.dal.dataobject.rule.ScadaAlarmRuleDO;

import java.util.List;

/**
 * 告警规则服务接口
 */
public interface AlarmRuleService {

    Long createRule(ScadaAlarmRuleSaveReqVO reqVO);

    void updateRule(ScadaAlarmRuleSaveReqVO reqVO);

    void deleteRule(Long id);

    ScadaAlarmRuleDO getRule(Long id);

    List<ScadaAlarmRuleDO> getRuleList(ScadaAlarmRulePageReqVO pageReqVO);
}
