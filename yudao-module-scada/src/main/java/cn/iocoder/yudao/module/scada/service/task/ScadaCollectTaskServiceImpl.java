package cn.iocoder.yudao.module.scada.service.task;

import cn.iocoder.yudao.module.scada.controller.admin.task.vo.ScadaCollectTaskPageReqVO;
import cn.iocoder.yudao.module.scada.controller.admin.task.vo.ScadaCollectTaskSaveReqVO;
import cn.iocoder.yudao.module.scada.dal.dataobject.task.ScadaCollectTaskDO;
import cn.iocoder.yudao.module.scada.dal.mysql.task.ScadaCollectTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SCADA 采集任务服务实现
 *
 * <p>CRUD 不触碰调度器内部状态——调度器是只读消费者，
 * 任何 enabled 字段的变化会在下一秒扫描周期反映出来。</p>
 *
 * <p>注意：updateTask 用 {@code updateById}，会保留 lastRunAt/runCount 等统计字段，
 * 因为这些字段不在 SaveReqVO 里，MyBatis-Plus 默认 null 字段不更新。</p>
 */
@Slf4j
@Service
public class ScadaCollectTaskServiceImpl implements ScadaCollectTaskService {

    private final ScadaCollectTaskMapper taskMapper;

    public ScadaCollectTaskServiceImpl(ScadaCollectTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTask(ScadaCollectTaskSaveReqVO reqVO) {
        ScadaCollectTaskDO task = new ScadaCollectTaskDO();
        BeanUtils.copyProperties(reqVO, task);
        taskMapper.insert(task);
        log.info("创建采集任务 {} id={} device={}", task.getCode(), task.getId(), task.getDeviceId());
        return task.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTask(ScadaCollectTaskSaveReqVO reqVO) {
        ScadaCollectTaskDO task = new ScadaCollectTaskDO();
        BeanUtils.copyProperties(reqVO, task);
        taskMapper.updateById(task);
        log.info("更新采集任务 id={} code={}", task.getId(), task.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long id) {
        taskMapper.deleteById(id);
        log.info("删除采集任务 id={}", id);
    }

    @Override
    public ScadaCollectTaskDO getTask(Long id) {
        return taskMapper.selectById(id);
    }

    @Override
    public List<ScadaCollectTaskDO> getTaskList(ScadaCollectTaskPageReqVO reqVO) {
        if (reqVO != null && reqVO.getDeviceId() != null) {
            return taskMapper.selectListByDevice(reqVO.getDeviceId());
        }
        return taskMapper.selectList();
    }

    @Override
    public void toggleEnabled(Long id, Boolean enabled) {
        ScadaCollectTaskDO update = new ScadaCollectTaskDO();
        update.setId(id);
        update.setEnabled(enabled);
        taskMapper.updateById(update);
        log.info("采集任务 {} 启停切换 enabled={}", id, enabled);
    }
}
