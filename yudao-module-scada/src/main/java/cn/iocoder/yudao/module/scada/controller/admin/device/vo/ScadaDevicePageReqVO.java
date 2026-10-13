package cn.iocoder.yudao.module.scada.controller.admin.device.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 设备分页查询 VO
 *
 * <p>所有字段都是可选的——不填则列出当前租户下所有设备。
 * code/name 是 LIKE 模糊匹配，protocolType/status/enabled 是精确匹配。</p>
 */
@Schema(description = "管理后台 - SCADA 设备分页 Request VO")
@Data
public class ScadaDevicePageReqVO {

    @Schema(description = "设备编码 LIKE")
    private String code;

    @Schema(description = "设备名称 LIKE")
    private String name;

    @Schema(description = "协议类型")
    private Integer protocolType;

    @Schema(description = "运行状态 0=禁用/1=未知/2=连接中/3=已连接/4=重连中/5=错误")
    private Integer status;

    @Schema(description = "是否启用")
    private Boolean enabled;
}
