package cn.iocoder.yudao.module.scada.framework.scada.driver;

import cn.iocoder.yudao.module.scada.dal.dataobject.device.ScadaDeviceDO;
import cn.iocoder.yudao.module.scada.dal.dataobject.point.ScadaPointDO;
import cn.iocoder.yudao.module.scada.enums.ScadaProtocolType;
import cn.iocoder.yudao.module.scada.framework.scada.protocol.PointValue;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MQTT Sparkplug B 驱动实现（基于 Spring Integration MQTT + Eclipse Paho）
 *
 * <p>Sparkplug B 是工业 MQTT 标准化载荷格式：定义了 birth/death certificate、
 * data message、NData 等消息类型，让工业设备能"即插即用"接入 SCADA 平台。</p>
 *
 * <p>本驱动是订阅模式——不主动读，而是订阅 topic 后等设备推值。
 * 一个设备对应一个 edgeNode，订阅 topic：spBv1.0/{group}/{edgeNode}/NDATA/#</p>
 *
 * <p>为什么不用 Sparkplug B 完整规范？完整规范要处理 stateful metric、birth/death，
 * 实现复杂度 1000+ 行。这里简化为"topic 订阅 + JSON 载荷"，覆盖 80% 场景，
 * 商用版可在 v2 升级到完整 Sparkplug B。</p>
 *
 * <p>connParamsJson 示例：{brokerUrl: "tcp://192.168.1.40:1883",
 *   group: "factory-a", edgeNode: "line-1", clientId: "scada-sub-001"}</p>
 *
 * <p>载荷格式：{"tag1": 12.3, "tag2": 45.6, "_ts": 1697234567890}</p>
 */
@Slf4j
@Component
public class MqttSparkplugDriver implements ScadaPlcDriver {

    private final Gson gson = new Gson();

    @Override
    public Integer getProtocolType() {
        return ScadaProtocolType.MQTT_SPARKPLUG.getType();
    }

    @Override
    public Object connect(ScadaDeviceDO device) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = gson.fromJson(device.getConnParamsJson(), Map.class);
        String brokerUrl = (String) params.get("brokerUrl");
        String group = (String) params.get("group");
        String edgeNode = (String) params.get("edgeNode");
        String clientId = (String) params.getOrDefault("clientId", "scada-" + System.currentTimeMillis());

        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setAutomaticReconnect(true);
        opts.setCleanSession(false);
        opts.setConnectionTimeout(10);
        opts.setKeepAliveInterval(30);

        MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
        // 订阅缓存：tag → 最近一次值
        Map<String, BigDecimal> cache = new ConcurrentHashMap<>();
        Map<String, Long> tsMap = new ConcurrentHashMap<>();

        client.setCallback(new org.eclipse.paho.client.mqttv3.MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                // 重连后重新订阅
                String topic = String.format("spBv1.0/%s/%s/NDATA/+", group, edgeNode);
                try {
                    client.subscribe(topic, 1);
                    log.info("MQTT 已订阅 {}", topic);
                } catch (Exception e) {
                    log.error("MQTT 订阅失败 {}: {}", topic, e.getMessage());
                }
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = gson.fromJson(payload, Map.class);
                    long ts = data.containsKey("_ts")
                            ? ((Number) data.get("_ts")).longValue()
                            : System.currentTimeMillis();
                    for (Map.Entry<String, Object> e : data.entrySet()) {
                        if (e.getKey().startsWith("_")) continue;
                        if (e.getValue() instanceof Number n) {
                            cache.put(e.getKey(), BigDecimal.valueOf(n.doubleValue()));
                            tsMap.put(e.getKey(), ts);
                        }
                    }
                } catch (Exception e) {
                    log.warn("MQTT 解码失败 {}: {}", topic, e.getMessage());
                }
            }

            @Override public void connectionLost(Throwable cause) {
                log.warn("MQTT 连接丢失: {}", cause.getMessage());
            }
            @Override public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {}
        });

        client.connect(opts);
        // 不在这里 subscribe，等 connectComplete 回调里订阅，确保 session 建立后
        log.info("MQTT 已连接 broker={} client={}", brokerUrl, clientId);
        return new MqttConn(client, cache, tsMap);
    }

    @Override
    public void disconnect(Object connHandle) {
        if (connHandle instanceof MqttConn mc) {
            try {
                if (mc.client.isConnected()) mc.client.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public boolean isConnected(Object connHandle) {
        if (connHandle instanceof MqttConn mc) {
            return mc.client.isConnected();
        }
        return false;
    }

    @Override
    public List<PointValue> readBatch(Object connHandle, List<ScadaPointDO> points) throws Exception {
        MqttConn mc = (MqttConn) connHandle;
        List<PointValue> result = new ArrayList<>(points.size());
        long now = System.currentTimeMillis();
        for (ScadaPointDO p : points) {
            BigDecimal v = mc.cache.get(p.getTag());
            Long ts = mc.tsMap.get(p.getTag());
            if (v != null && ts != null && now - ts < 10_000) {
                // 10 秒内的缓存值视为有效，超时视为坏值
                result.add(PointValue.of(p.getId(), p.getTag(), v));
            } else {
                result.add(PointValue.bad(p.getId(), p.getTag()));
            }
        }
        return result;
    }
    // MQTT 订阅模式不支持反向写
    private record MqttConn(MqttClient client, Map<String, BigDecimal> cache, Map<String, Long> tsMap) {}
}
