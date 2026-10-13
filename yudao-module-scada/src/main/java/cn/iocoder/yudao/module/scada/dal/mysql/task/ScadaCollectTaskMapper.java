package cn.iocoder.yudao.module.scada.dal.mysql.task;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.scada.dal.dataobject.task.ScadaCollectTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SCADA 采集任务 Mapper
 *
 * <p>{@code selectPendingTasks} 是调度核心查询：每秒扫一次启用且到点的任务，
 * 走索引 idx_collect_task_due (enabled, last_run_at)。</p>
 */
@Mapper
public interface ScadaCollectTaskMapper extends BaseMapperX<ScadaCollectTaskDO> {

    /** 调度器使用：列出所有启用且到点要执行的任务 */
    default List<ScadaCollectTaskDO> selectEnabledTasks() {
        return selectList(new LambdaQueryWrapperX<ScadaCollectTaskDO>()
                .eq(ScadaCollectTaskDO::getEnabled, true));
    }

    default List<ScadaCollectTaskDO> selectListByDevice(Long deviceId) {
        return selectList(new LambdaQueryWrapperX<ScadaCollectTaskDO>()
                .eq(ScadaCollectTaskDO::getDeviceId, deviceId));
    }

    /**
     * 任务执行后回写运行时统计：原子 +1 runCount/errorCount，覆盖 lastRunAt/lastDurationMs/lastError。
     * 用 @Update 而不是 updateById，避免乐观锁版本号冲突。
     */
    @Update("UPDATE scada_collect_task SET last_run_at = #{runAt}, last_duration_ms = #{durMs}, " +
            "last_error = #{error}, run_count = run_count + 1, " +
            "error_count = error_count + CASE WHEN #{error} IS NULL THEN 0 ELSE 1 END " +
            "WHERE id = #{id} AND deleted = 0")
    int updateRunStat(@Param("id") Long id, @Param("runAt") LocalDateTime runAt,
                      @Param("durMs") Long durMs, @Param("error") String error);
}
