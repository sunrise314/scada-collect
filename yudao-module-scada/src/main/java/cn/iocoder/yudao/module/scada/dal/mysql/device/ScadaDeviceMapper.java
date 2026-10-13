package cn.iocoder.yudao.module.scada.dal.mysql.device;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.scada.dal.dataobject.device.ScadaDeviceDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * SCADA 设备 Mapper
 *
 * <p>沿用芋道 {@code BaseMapperX}，自带 selectPage / insertBatch 等增强方法，
 * 不用为分页写单独 SQL。复杂查询用 LambdaQueryWrapperX 链式构造。</p>
 */
@Mapper
public interface ScadaDeviceMapper extends BaseMapperX<ScadaDeviceDO> {

    /** 按协议类型列出设备，Driver 启动初始化连接池时用 */
    default List<ScadaDeviceDO> selectListByProtocol(Integer protocolType) {
        return selectList(new LambdaQueryWrapperX<ScadaDeviceDO>()
                .eq(ScadaDeviceDO::getProtocolType, protocolType)
                .eq(ScadaDeviceDO::getEnabled, true));
    }

    /** 设备编码唯一性校验（编辑时排除自己） */
    default ScadaDeviceDO selectByCode(String code) {
        return selectOne(new LambdaQueryWrapperX<ScadaDeviceDO>()
                .eq(ScadaDeviceDO::getCode, code));
    }

    /** Driver 心跳更新，不走全 DO update 避免覆盖并发字段 */
    int updateHeartbeat(@Param("id") Long id, @Param("status") Integer status,
                        @Param("lastError") String lastError);
}
