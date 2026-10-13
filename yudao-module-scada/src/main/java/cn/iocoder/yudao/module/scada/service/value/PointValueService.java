package cn.iocoder.yudao.module.scada.service.value;

import cn.iocoder.yudao.module.scada.dal.dataobject.value.PointValueDO;
import cn.iocoder.yudao.module.scada.framework.scada.protocol.PointValue;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 点位值服务接口
 *
 * <p>职责：把 Driver 层的 PointValue 写入 TDengine，
 * 并提供历史曲线查询、降采样查询供前端调用。</p>
 *
 * <p>注意：写入失败抛异常，由 {@code CollectTaskExecutor} 决定走 ReplayBuffer 补采。</p>
 */
public interface PointValueService {

    /** 单个写 */
    void write(PointValue value);

    /** 批量写：主路径，性能关键 */
    void writeBatch(List<PointValue> values);

    /** 查询某 tag 的历史曲线，原始值 */
    List<PointValueDO> queryHistory(String tag, LocalDateTime from, LocalDateTime to, int limit);

    /** 查询降采样曲线，按 intervalMinutes 分钟聚合均值 */
    List<PointValueDO> queryDownsample(String tag, LocalDateTime from, LocalDateTime to, int intervalMinutes);
}
