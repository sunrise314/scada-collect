package cn.iocoder.yudao.module.scada.controller.admin.rule;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.scada.controller.admin.rule.vo.ScadaAlarmRulePageReqVO;
import cn.iocoder.yudao.module.scada.controller.admin.rule.vo.ScadaAlarmRuleSaveReqVO;
import cn.iocoder.yudao.module.scada.dal.dataobject.rule.ScadaAlarmRuleDO;
import cn.iocoder.yudao.module.scada.service.alarm.AlarmRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * SCADA 告警规则 Controller
 *
 * <p>规则增删改后由 {@link AlarmRuleService} 内部触发 {@code alarmEngine.reloadRules()}，
 * 不暴露单独的 reload 接口——避免前端绕过 CRUD 直接刷规则缓存。</p>
 */
@Tag(name = "管理后台 - SCADA 告警规则")
@RestController
@RequestMapping("/scada/rule")
public class ScadaAlarmRuleController {

    private final AlarmRuleService ruleService;

    public ScadaAlarmRuleController(AlarmRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @PostMapping("/create")
    @Operation(summary = "创建告警规则")
    public CommonResult<Long> create(@Valid @RequestBody ScadaAlarmRuleSaveReqVO reqVO) {
        return success(ruleService.createRule(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新告警规则")
    public CommonResult<Boolean> update(@Valid @RequestBody ScadaAlarmRuleSaveReqVO reqVO) {
        ruleService.updateRule(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除告警规则")
    @Parameter(name = "id", description = "规则 ID", required = true)
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        ruleService.deleteRule(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取规则详情")
    @Parameter(name = "id", description = "规则 ID", required = true)
    public CommonResult<ScadaAlarmRuleSaveReqVO> get(@RequestParam("id") Long id) {
        // 规则字段与 SaveReqVO 完全对齐，直接复用
        ScadaAlarmRuleDO rule = ruleService.getRule(id);
        return success(rule == null ? null : convertSave(rule));
    }

    @GetMapping("/list")
    @Operation(summary = "规则列表")
    public CommonResult<List<ScadaAlarmRuleSaveReqVO>> list(ScadaAlarmRulePageReqVO reqVO) {
        List<ScadaAlarmRuleDO> list = ruleService.getRuleList(reqVO);
        return success(list.stream().map(this::convertSave).toList());
    }

    private ScadaAlarmRuleSaveReqVO convertSave(ScadaAlarmRuleDO rule) {
        ScadaAlarmRuleSaveReqVO vo = new ScadaAlarmRuleSaveReqVO();
        BeanUtils.copyProperties(rule, vo);
        return vo;
    }
}
