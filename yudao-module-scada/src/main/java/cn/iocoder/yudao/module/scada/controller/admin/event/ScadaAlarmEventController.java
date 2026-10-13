package cn.iocoder.yudao.module.scada.controller.admin.event;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.scada.controller.admin.event.vo.ScadaAlarmEventPageReqVO;
import cn.iocoder.yudao.module.scada.controller.admin.event.vo.ScadaAlarmEventRespReqVO;
import cn.iocoder.yudao.module.scada.dal.dataobject.event.ScadaAlarmEventDO;
import cn.iocoder.yudao.module.scada.service.alarm.AlarmEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * SCADA 告警事件 Controller
 *
 * <p>ack/clear 操作走状态机校验，不能跳级（已 ACK 的不能再 ACK），
 * 也不能反向（已复归的不能再 ACK）——这些约束在 Service 层做。</p>
 *
 * <p>operator 取当前登录用户 ID 字符串，由芋道 SecurityUtils 提供；
 * 这里简化为请求头透传，方便联调阶段先跑通。</p>
 */
@Tag(name = "管理后台 - SCADA 告警事件")
@RestController
@RequestMapping("/scada/event")
public class ScadaAlarmEventController {

    private final AlarmEventService eventService;

    public ScadaAlarmEventController(AlarmEventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/list")
    @Operation(summary = "事件列表")
    public CommonResult<List<ScadaAlarmEventRespReqVO>> list(ScadaAlarmEventPageReqVO reqVO) {
        List<ScadaAlarmEventDO> list = eventService.getEventList(reqVO);
        return success(list.stream().map(this::convert).toList());
    }

    @GetMapping("/get")
    @Operation(summary = "事件详情")
    @Parameter(name = "id", description = "事件 ID", required = true)
    public CommonResult<ScadaAlarmEventRespReqVO> get(@RequestParam("id") Long id) {
        return success(convert(eventService.getEvent(id)));
    }

    @PutMapping("/ack")
    @Operation(summary = "人工确认")
    @Parameter(name = "id", description = "事件 ID", required = true)
    public CommonResult<Boolean> ack(@RequestParam("id") Long id,
                                     @RequestHeader(value = "X-Operator", required = false) String operator) {
        eventService.ackEvent(id, operator == null ? "system" : operator);
        return success(true);
    }

    @PutMapping("/clear")
    @Operation(summary = "人工复归")
    @Parameter(name = "id", description = "事件 ID", required = true)
    public CommonResult<Boolean> clear(@RequestParam("id") Long id,
                                        @RequestHeader(value = "X-Operator", required = false) String operator) {
        eventService.clearEvent(id, operator == null ? "system" : operator);
        return success(true);
    }

    @GetMapping("/count-unacked")
    @Operation(summary = "当前未确认告警计数")
    public CommonResult<Long> countUnacked() {
        return success(eventService.countUnacked());
    }

    private ScadaAlarmEventRespReqVO convert(ScadaAlarmEventDO event) {
        if (event == null) return null;
        ScadaAlarmEventRespReqVO vo = new ScadaAlarmEventRespReqVO();
        BeanUtils.copyProperties(event, vo);
        return vo;
    }
}
