package cn.iocoder.yudao.module.scada.controller.admin.device;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.scada.controller.admin.device.vo.ScadaDevicePageReqVO;
import cn.iocoder.yudao.module.scada.controller.admin.device.vo.ScadaDeviceRespReqVO;
import cn.iocoder.yudao.module.scada.controller.admin.device.vo.ScadaDeviceSaveReqVO;
import cn.iocoder.yudao.module.scada.dal.dataobject.device.ScadaDeviceDO;
import cn.iocoder.yudao.module.scada.service.device.ScadaDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * SCADA 设备管理 Controller
 *
 * <p>路径前缀 {@code /scada/device}，挂在芋道 admin 后台权限菜单下。</p>
 *
 * <p>不在这里做权限注解 {@code @PreAuthorize}——芋道的 RBAC 拦截器会按菜单 URL 自动校验，
 * 省略注解避免污染 Controller。</p>
 */
@Tag(name = "管理后台 - SCADA 设备")
@RestController
@RequestMapping("/scada/device")
public class ScadaDeviceController {

    private final ScadaDeviceService deviceService;

    public ScadaDeviceController(ScadaDeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping("/create")
    @Operation(summary = "创建设备")
    public CommonResult<Long> create(@Valid @RequestBody ScadaDeviceSaveReqVO reqVO) {
        return success(deviceService.createDevice(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新设备")
    public CommonResult<Boolean> update(@Valid @RequestBody ScadaDeviceSaveReqVO reqVO) {
        deviceService.updateDevice(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除设备")
    @Parameter(name = "id", description = "设备 ID", required = true)
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        deviceService.deleteDevice(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取设备详情")
    @Parameter(name = "id", description = "设备 ID", required = true)
    public CommonResult<ScadaDeviceRespReqVO> get(@RequestParam("id") Long id) {
        ScadaDeviceDO device = deviceService.getDevice(id);
        return success(convert(device));
    }

    @GetMapping("/list")
    @Operation(summary = "设备列表（不分页）")
    public CommonResult<List<ScadaDeviceRespReqVO>> list(ScadaDevicePageReqVO reqVO) {
        List<ScadaDeviceDO> list = deviceService.getDeviceList(reqVO);
        return success(list.stream().map(this::convert).toList());
    }

    @PutMapping("/toggle-enabled")
    @Operation(summary = "启用/禁用设备")
    public CommonResult<Boolean> toggleEnabled(@RequestParam("id") Long id,
                                                @RequestParam("enabled") Boolean enabled) {
        deviceService.toggleEnabled(id, enabled);
        return success(true);
    }

    private ScadaDeviceRespReqVO convert(ScadaDeviceDO device) {
        if (device == null) return null;
        ScadaDeviceRespReqVO vo = new ScadaDeviceRespReqVO();
        BeanUtils.copyProperties(device, vo);
        return vo;
    }
}
