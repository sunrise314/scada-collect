package cn.iocoder.yudao.module.scada.controller.admin.value;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.scada.controller.admin.value.vo.ScadaPointValueHistoryReqVO;
import cn.iocoder.yudao.module.scada.dal.dataobject.value.PointValueDO;
import cn.iocoder.yudao.module.scada.service.push.RealtimePushService;
import cn.iocoder.yudao.module.scada.service.value.PointValueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * SCADA 点位值 Controller
 *
 * <p>两个端点：</p>
 * <ul>
 *   <li>{@code /history}：历史曲线，直接查 TDengine，同步返回 JSON 数组</li>
 *   <li>{@code /stream}：实时流，返回 SseEmitter，由 {@link RealtimePushService#subscribe} 注册</li>
 * </ul>
 *
 * <p>SSE 不在芋道的统一返回包装——SseEmitter 是长连接对象，包装 CommonResult 没意义。
 * 数据帧格式由 {@code SsePushServer} 内部定义。</p>
 */
@Tag(name = "管理后台 - SCADA 点位值")
@RestController
@RequestMapping("/scada/value")
public class ScadaPointValueController {

    private final PointValueService valueService;
    private final RealtimePushService pushService;

    public ScadaPointValueController(PointValueService valueService,
                                     RealtimePushService pushService) {
        this.valueService = valueService;
        this.pushService = pushService;
    }

    @GetMapping("/history")
    @Operation(summary = "历史曲线查询")
    public CommonResult<List<PointValueDO>> history(@Valid ScadaPointValueHistoryReqVO reqVO) {
        int limit = reqVO.getLimit() == null ? 5000 : reqVO.getLimit();
        return success(valueService.queryHistory(reqVO.getTag(), reqVO.getFrom(), reqVO.getTo(), limit));
    }

    @GetMapping("/downsample")
    @Operation(summary = "降采样曲线")
    public CommonResult<List<PointValueDO>> downsample(@Valid ScadaPointValueHistoryReqVO reqVO,
                                                       @RequestParam(value = "intervalMinutes", defaultValue = "5") int intervalMinutes) {
        return success(valueService.queryDownsample(reqVO.getTag(), reqVO.getFrom(), reqVO.getTo(), intervalMinutes));
    }

    @GetMapping(value = "/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "实时值流订阅（SSE）")
    public SseEmitter stream(@RequestParam("pointIds") String pointIds) {
        Set<Long> idSet = parseIds(pointIds);
        String clientId = UUID.randomUUID().toString();
        return pushService.subscribe(clientId, idSet);
    }

    @DeleteMapping("/stream-close")
    @Operation(summary = "取消订阅")
    public CommonResult<Boolean> close(@RequestParam("clientId") String clientId) {
        pushService.unsubscribe(clientId);
        return success(true);
    }

    private Set<Long> parseIds(String pointIds) {
        if (pointIds == null || pointIds.isBlank()) return new HashSet<>();
        return Arrays.stream(pointIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toSet());
    }
}
