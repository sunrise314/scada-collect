package cn.iocoder.yudao.module.scada.service.push;

import cn.iocoder.yudao.module.scada.framework.scada.protocol.PointValue;
import cn.iocoder.yudao.module.scada.framework.scada.sse.SsePushServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Set;

/**
 * 实时推送服务实现
 *
 * <p>只是 {@link SsePushServer} 的薄壳，未来要加 WebSocket / kafka 流式推送，
 * 直接在这里增加一个分发即可。</p>
 */
@Slf4j
@Service
public class RealtimePushServiceImpl implements RealtimePushService {

    private final SsePushServer sseServer;

    public RealtimePushServiceImpl(SsePushServer sseServer) {
        this.sseServer = sseServer;
    }

    @Override
    public void submit(List<PointValue> values) {
        sseServer.submitBatch(values);
    }

    @Override
    public SseEmitter subscribe(String clientId, Set<Long> pointIds) {
        return sseServer.subscribe(clientId, pointIds);
    }

    @Override
    public void unsubscribe(String clientId) {
        sseServer.unsubscribe(clientId);
    }
}
