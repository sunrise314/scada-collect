package cn.iocoder.yudao.module.scada.dal.dataobject.value;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 点位值 DO（DTO，不入主库）
 *
 * <p>这个 DO 不是 MySQL 表的映射——它只是为了给前端响应、内部传输用，
 * 真正的存储在 TDengine {@code scada_point_value} 超表中。</p>
 *
 * <p>不写 @TableName 注解，避免被 MyBatis-Plus 扫描。仅作 DTO。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointValueDO {

    private Long pointId;
    private String tag;
    private LocalDateTime ts;
    private BigDecimal valueNum;
    private String valueStr;
    private Integer quality;
}
