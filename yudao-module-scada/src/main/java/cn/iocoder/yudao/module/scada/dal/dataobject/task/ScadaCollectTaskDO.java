package cn.iocoder.yudao.module.scada.dal.dataobject.task;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * SCADA 采集任务实体
 *
 * <p>一个采集任务对应一台设备的一组点位的批量读取调度。
 * 不做"一个点位一个任务"的设计——OPC UA 订阅是天然批量的，
 * Modbus 一个报文也能读 125 个寄存器，按点位调度会浪费带宽。</p>
 *
 * <p>关键设计：</p>
 * <ul>
 *   <li>taskType=POLL 是轮询模式，按 intervalMs 周期性读</li>
 *   <li>taskType=SUBSCRIBE 是订阅模式，仅 OPC UA 支持，由设备推送</li>
 *   <li>pointIdsJson：点位 ID 数组，避免任务-点位关联表的 N+N 查询</li>
 *   <li>lastRunAt/LastError：调度器写入，用于看板与重试</li>
 *   <li>concurrent：是否允许多线程并发执行同一任务，默认 false，避免乱序</li>
 * </ul>
 *
 * <p>调度执行器 {@code CollectTaskExecutor} 每秒扫一次 enabled=true 且
 * (lastRunAt IS NULL OR lastRunAt + intervalMs/1000 &lt; now) 的任务，
 * 提交到线程池。具体线程池大小按 device 数量 × 0.5 估算。</p>
 */
@TableName("scada_collect_task")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ScadaCollectTaskDO extends BaseDO {

    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO)
    private Long id;

    /** 任务编码 */
    private String code;

    /** 任务名称 */
    private String name;

    /** 关联设备 ID */
    private Long deviceId;

    /** 任务类型：POLL 轮询 / SUBSCRIBE 订阅 */
    private String taskType;

    /** 采集周期毫秒，POLL 模式必填 */
    @Builder.Default
    private Integer intervalMs = 1000;

    /** 关联点位 ID 数组 JSON，如 [101,102,103] */
    private String pointIdsJson;

    /** 是否启用 */
    @Builder.Default
    private Boolean enabled = true;

    /** 是否允许并发执行 */
    @Builder.Default
    private Boolean concurrent = false;

    /** 上次执行时间 */
    private LocalDateTime lastRunAt;

    /** 上次执行耗时毫秒 */
    private Long lastDurationMs;

    /** 上次错误信息，null 表示成功 */
    private String lastError;

    /** 累计执行次数 */
    @Builder.Default
    private Long runCount = 0L;

    /** 累计错误次数 */
    @Builder.Default
    private Long errorCount = 0L;
}
