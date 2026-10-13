package cn.iocoder.yudao.module.scada.service.value;

import cn.iocoder.yudao.module.scada.dal.dataobject.value.PointValueDO;
import cn.iocoder.yudao.module.scada.framework.scada.protocol.PointValue;
import cn.iocoder.yudao.module.scada.framework.scada.tdengine.TDengineSqlMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 点位值服务实现
 *
 * <p>启动时触发 TDengine schema 初始化，确保超表 scada_point_value 就绪。</p>
 *
 * <p>批量写是热路径：调用方 {@code CollectTaskExecutor} 在每个任务执行末尾一次调用，
 * 写入频率取决于任务调度间隔，1Hz 任务每秒一次写。</p>
 *
 * <p>查询路径：从 SSE 推送的"最近值"不会走 TDengine（性能浪费），
 * 历史曲线走 TDengine，降采样走 INTERVAL 聚合。</p>
 */
@Slf4j
@Service
public class PointValueServiceImpl implements PointValueService {

    private final TDengineSqlMapper sqlMapper;

    public PointValueServiceImpl(TDengineSqlMapper sqlMapper) {
        this.sqlMapper = sqlMapper;
    }

    @PostConstruct
    public void initSchema() {
        try {
            sqlMapper.initSchema();
        } catch (Exception e) {
            log.error("TDengine schema 初始化失败，写入功能将不可用: {}", e.getMessage());
        }
    }

    @Override
    public void write(PointValue value) {
        writeBatch(List.of(value));
    }

    @Override
    public void writeBatch(List<PointValue> values) {
        if (values == null || values.isEmpty()) return;
        sqlMapper.batchInsert(values);
    }

    @Override
    public List<PointValueDO> queryHistory(String tag, LocalDateTime from, LocalDateTime to, int limit) {
        List<Object[]> rows = sqlMapper.queryHistory(tag, from, to, limit);
        List<PointValueDO> out = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            out.add(PointValueDO.builder()
                    .tag(tag)
                    .ts((LocalDateTime) row[0])
                    .valueNum(java.math.BigDecimal.valueOf((Double) row[1]))
                    .quality(PointValue.QUALITY_GOOD)
                    .build());
        }
        return out;
    }

    @Override
    public List<PointValueDO> queryDownsample(String tag, LocalDateTime from, LocalDateTime to, int intervalMinutes) {
        List<Object[]> rows = sqlMapper.queryDownsample(tag, from, to, intervalMinutes);
        List<PointValueDO> out = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            out.add(PointValueDO.builder()
                    .tag(tag)
                    .ts((LocalDateTime) row[0])
                    .valueNum(java.math.BigDecimal.valueOf((Double) row[1]))
                    .quality(PointValue.QUALITY_GOOD)
                    .build());
        }
        return out;
    }
}
