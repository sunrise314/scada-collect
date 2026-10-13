package cn.iocoder.yudao.module.scada.controller.admin.point;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.scada.controller.admin.point.vo.ScadaPointPageReqVO;
import cn.iocoder.yudao.module.scada.controller.admin.point.vo.ScadaPointRespReqVO;
import cn.iocoder.yudao.module.scada.controller.admin.point.vo.ScadaPointSaveReqVO;
import cn.iocoder.yudao.module.scada.dal.dataobject.point.ScadaPointDO;
import cn.iocoder.yudao.module.scada.service.point.ScadaPointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * SCADA 点位管理 Controller
 */
@Tag(name = "管理后台 - SCADA 点位")
@RestController
@RequestMapping("/scada/point")
public class ScadaPointController {

    private final ScadaPointService pointService;

    public ScadaPointController(ScadaPointService pointService) {
        this.pointService = pointService;
    }

    @PostMapping("/create")
    @Operation(summary = "创建点位")
    public CommonResult<Long> create(@Valid @RequestBody ScadaPointSaveReqVO reqVO) {
        return success(pointService.createPoint(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新点位")
    public CommonResult<Boolean> update(@Valid @RequestBody ScadaPointSaveReqVO reqVO) {
        pointService.updatePoint(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除点位")
    @Parameter(name = "id", description = "点位 ID", required = true)
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        pointService.deletePoint(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取点位详情")
    @Parameter(name = "id", description = "点位 ID", required = true)
    public CommonResult<ScadaPointRespReqVO> get(@RequestParam("id") Long id) {
        return success(convert(pointService.getPoint(id)));
    }

    @GetMapping("/list")
    @Operation(summary = "点位列表")
    public CommonResult<List<ScadaPointRespReqVO>> list(ScadaPointPageReqVO reqVO) {
        List<ScadaPointDO> list = pointService.getPointList(reqVO);
        return success(list.stream().map(this::convert).toList());
    }

    @GetMapping("/list-by-device")
    @Operation(summary = "按设备列出点位")
    @Parameter(name = "deviceId", description = "设备 ID", required = true)
    public CommonResult<List<ScadaPointRespReqVO>> listByDevice(@RequestParam("deviceId") Long deviceId) {
        List<ScadaPointDO> list = pointService.listByDevice(deviceId);
        return success(list.stream().map(this::convert).toList());
    }

    private ScadaPointRespReqVO convert(ScadaPointDO point) {
        if (point == null) return null;
        ScadaPointRespReqVO vo = new ScadaPointRespReqVO();
        BeanUtils.copyProperties(point, vo);
        return vo;
    }
}
