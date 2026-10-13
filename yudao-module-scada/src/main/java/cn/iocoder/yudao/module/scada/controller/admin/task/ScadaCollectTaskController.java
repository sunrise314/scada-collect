package cn.iocoder.yudao.module.scada.controller.admin.task;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.scada.controller.admin.task.vo.ScadaCollectTaskPageReqVO;
import cn.iocoder.yudao.module.scada.controller.admin.task.vo.ScadaCollectTaskRespReqVO;
import cn.iocoder.yudao.module.scada.controller.admin.task.vo.ScadaCollectTaskSaveReqVO;
import cn.iocoder.yudao.module.scada.dal.dataobject.task.ScadaCollectTaskDO;
import cn.iocoder.yudao.module.scada.service.task.ScadaCollectTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * SCADA 采集任务 Controller
 *
 * <p>启停切换走单独的 {@code toggle-enabled} 接口，不与 update 混用——
 * 避免前端误把 lastRunAt 等统计字段一起覆盖。</p>
 */
@Tag(name = "管理后台 - SCADA 采集任务")
@RestController
@RequestMapping("/scada/task")
public class ScadaCollectTaskController {

    private final ScadaCollectTaskService taskService;

    public ScadaCollectTaskController(ScadaCollectTaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/create")
    @Operation(summary = "创建采集任务")
    public CommonResult<Long> create(@Valid @RequestBody ScadaCollectTaskSaveReqVO reqVO) {
        return success(taskService.createTask(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新采集任务")
    public CommonResult<Boolean> update(@Valid @RequestBody ScadaCollectTaskSaveReqVO reqVO) {
        taskService.updateTask(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除采集任务")
    @Parameter(name = "id", description = "任务 ID", required = true)
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        taskService.deleteTask(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获取任务详情")
    @Parameter(name = "id", description = "任务 ID", required = true)
    public CommonResult<ScadaCollectTaskRespReqVO> get(@RequestParam("id") Long id) {
        return success(convert(taskService.getTask(id)));
    }

    @GetMapping("/list")
    @Operation(summary = "任务列表")
    public CommonResult<List<ScadaCollectTaskRespReqVO>> list(ScadaCollectTaskPageReqVO reqVO) {
        List<ScadaCollectTaskDO> list = taskService.getTaskList(reqVO);
        return success(list.stream().map(this::convert).toList());
    }

    @PutMapping("/toggle-enabled")
    @Operation(summary = "启用/禁用任务")
    public CommonResult<Boolean> toggleEnabled(@RequestParam("id") Long id,
                                               @RequestParam("enabled") Boolean enabled) {
        taskService.toggleEnabled(id, enabled);
        return success(true);
    }

    private ScadaCollectTaskRespReqVO convert(ScadaCollectTaskDO task) {
        if (task == null) return null;
        ScadaCollectTaskRespReqVO vo = new ScadaCollectTaskRespReqVO();
        BeanUtils.copyProperties(task, vo);
        return vo;
    }
}
