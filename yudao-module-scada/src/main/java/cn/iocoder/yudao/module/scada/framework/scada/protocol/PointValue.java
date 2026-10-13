package cn.iocoder.yudao.module.scada.framework.scada.protocol;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SCADA 点位实时值载体
 *
 * <p>这是一个不可变 record，承载 Driver 层一次读取/订阅回调产生的点位值。
 * 从 Driver 层输出，流向三个消费方：</p>
 * <ol>
 *   <li>{@code PointValueService#write}：写入 TDengine 超表</li>
 *   <li>{@code AlarmEngine#onValue}：进入告警判定管线</li>
 *   <li>{@code RealtimePushService#submit}：进入 SSE 推送聚合队列</li>
 * </ol>
 *
 * <p>为什么用 record 而不是 DO：DO 是 ORM 映射对象，带 {@code @TableName} 等注解，
 * 会被 MyBatis-Plus 拦截器扫描；而 PointValue 是 Driver 层的"裸数据"，
 * 不该绑死到任何持久层。record 既轻量又语义明确。</p>
 *
 * <p>quality 字段沿用 OPC UA 的 Good/Bad/Uncertain 三态简化版：
 * 1=Good，2=Bad，3=Uncertain。Driver 层在读取失败时塞 Bad，让告警与推送都跳过。</p>
 */
public record PointValue(
        Long pointId,
        String tag,
        LocalDateTime ts,
        BigDecimal valueNum,
        String valueStr,
        int quality
) {

    /** 质量常量：好 / 坏 / 不确定 */
    public static final int QUALITY_GOOD = 1;
    public static final int QUALITY_BAD = 2;
    public static final int QUALITY_UNCERTAIN = 3;

    /** 工厂方法：数值型点位 */
    public static PointValue of(Long pointId, String tag, BigDecimal value) {
        return new PointValue(pointId, tag, LocalDateTime.now(), value, null, QUALITY_GOOD);
    }

    /** 工厂方法：字符串型点位（很少用，主要给批次号/状态字符串） */
    public static PointValue ofStr(Long pointId, String tag, String value) {
        return new PointValue(pointId, tag, LocalDateTime.now(), null, value, QUALITY_GOOD);
    }

    /** 工厂方法：质量为 Bad 的失败值，仅供上游标记 */
    public static PointValue bad(Long pointId, String tag) {
        return new PointValue(pointId, tag, LocalDateTime.now(), null, null, QUALITY_BAD);
    }

    public boolean isGood() {
        return quality == QUALITY_GOOD;
    }
}
