package cn.iocoder.yudao.module.scada.service.push;

import cn.iocoder.yudao.module.scada.framework.scada.protocol.PointValue;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Set;

/**
 * 实时推送服务接口
 *
 * <p>对外暴露：① 接收 Driver 层批量值；② 客户端订阅点位列表。</p>
 */
public interface RealtimePushService {

    /** 由 CollectTaskExecutor 在每次读完后调用 */
    void submit(List<PointValue> values);

    /** 客户端订阅点位，返回 SseEmitter；客户端通过 EventSource 持有 */
    SseEmitter subscribe(String clientId, Set<Long> pointIds);

    /** 取消订阅 */
    void unsubscribe(String clientId);
}
