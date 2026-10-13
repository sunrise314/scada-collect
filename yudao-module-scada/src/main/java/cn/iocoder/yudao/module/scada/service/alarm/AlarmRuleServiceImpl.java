package cn.iocoder.yudao.module.scada.service.alarm;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.scada.controller.admin.rule.vo.ScadaAlarmRulePageReqVO;
import cn.iocoder.yudao.module.scada.controller.admin.rule.vo.ScadaAlarmRuleSaveReqVO;
import cn.iocoder.yudao.module.scada.dal.dataobject.rule.ScadaAlarmRuleDO;
import cn.iocoder.yudao.module.scada.dal.mysql.rule.ScadaAlarmRuleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 告警规则服务实现
 *
 * <p>规则变更后必须通知 {@link AlarmEngine#reloadRules()} 重新加载内存缓存，
 * 否则旧的判定逻辑还在用——这是常见的"配置生效延迟"陷阱。</p>
 */
@Slf4j
@Service
public class AlarmRuleServiceImpl implements AlarmRuleService {

    private final ScadaAlarmRuleMapper ruleMapper;
    private final AlarmEngine alarmEngine;

    public AlarmRuleServiceImpl(ScadaAlarmRuleMapper ruleMapper, AlarmEngine alarmEngine) {
        this.ruleMapper = ruleMapper;
        this.alarmEngine = alarmEngine;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRule(ScadaAlarmRuleSaveReqVO reqVO) {
        ScadaAlarmRuleDO rule = new ScadaAlarmRuleDO();
        BeanUtils.copyProperties(reqVO, rule);
        ruleMapper.insert(rule);
        alarmEngine.reloadRules();
        return rule.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRule(ScadaAlarmRuleSaveReqVO reqVO) {
        ScadaAlarmRuleDO rule = new ScadaAlarmRuleDO();
        BeanUtils.copyProperties(reqVO, rule);
        ruleMapper.updateById(rule);
        alarmEngine.reloadRules();
    }

    @Override
    public void deleteRule(Long id) {
        ruleMapper.deleteById(id);
        alarmEngine.reloadRules();
    }

    @Override
    public ScadaAlarmRuleDO getRule(Long id) {
        return ruleMapper.selectById(id);
    }

    @Override
    public List<ScadaAlarmRuleDO> getRuleList(ScadaAlarmRulePageReqVO pageReqVO) {
        return ruleMapper.selectList();
    }
}
