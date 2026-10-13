package cn.iocoder.yudao.module.scada.service.task;

import cn.iocoder.yudao.module.scada.controller.admin.task.vo.ScadaCollectTaskPageReqVO;
import cn.iocoder.yudao.module.scada.controller.admin.task.vo.ScadaCollectTaskSaveReqVO;
import cn.iocoder.yudao.module.scada.dal.dataobject.task.ScadaCollectTaskDO;

import java.util.List;

/**
 * SCADA 采集任务服务接口
 *
 * <p>任务表是调度器的输入，{@code CollectTaskExecutor} 每秒扫一次 enabled=true 的任务，
 * 因此 CRUD 完成后不需要主动 reload——下次扫描自动生效。</p>
 *
 * <p>启停切换 {@link #toggleEnabled} 直接 update enabled 字段即可，
 * 调度器下一秒就会跳过禁用的任务。</p>
 */
public interface ScadaCollectTaskService {

    Long createTask(ScadaCollectTaskSaveReqVO reqVO);

    void updateTask(ScadaCollectTaskSaveReqVO reqVO);

    void deleteTask(Long id);

    ScadaCollectTaskDO getTask(Long id);

    List<ScadaCollectTaskDO> getTaskList(ScadaCollectTaskPageReqVO reqVO);

    /** 启停切换，不重建任务对象，避免清空 runCount/errorCount 等统计字段 */
    void toggleEnabled(Long id, Boolean enabled);
}
