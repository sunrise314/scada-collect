package cn.iocoder.yudao.module.scada.service.alarm;

import cn.iocoder.yudao.module.scada.dal.dataobject.event.ScadaAlarmEventDO;
import cn.iocoder.yudao.module.scada.dal.dataobject.rule.ScadaAlarmRuleDO;
import cn.iocoder.yudao.module.scada.enums.ScadaAlarmSeverity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 告警通知路由（按严重度路由到不同渠道）
 *
 * <p>路由规则：</p>
 * <ul>
 *   <li>LOW（1）：仅记日志，不主动通知</li>
 *   <li>MEDIUM（2）：写入通知表，运维登录后看</li>
 *   <li>HIGH（3）：站内推送 + 短信</li>
 *   <li>CRITICAL（4）：站内 + 短信 + 电话语音 + 微信公众号</li>
 * </ul>
 *
 * <p>商用版应集成 yudao-module-system 的 SmsService / MailService，
 * 这里简化为日志打印 + 留接口，方便后续接 yudao 站内信通道。</p>
 *
 * <p>电话通知是关键场景——核反应堆/电力调度场景，凌晨告警必须电话叫人，
 * 不能依赖 APP 推送（用户夜间不开手机）。这要接阿里云语音 / 容联云通讯。</p>
 */
@Slf4j
@Component
public class AlarmNotifyRouter {

    public void notify(ScadaAlarmEventDO event, ScadaAlarmRuleDO rule) {
        Integer severity = event.getSeverity() == null ? rule.getSeverity() : event.getSeverity();
        if (severity == null) severity = ScadaAlarmSeverity.MEDIUM.getSeverity();

        if (severity.equals(ScadaAlarmSeverity.LOW.getSeverity())) {
            log.info("告警通知[LOW] event={} rule={}: 仅记日志", event.getId(), rule.getCode());
            return;
        }

        if (severity.equals(ScadaAlarmSeverity.MEDIUM.getSeverity())) {
            // 商用版：写入 notify_channel 表，等运维登录查看
            log.info("告警通知[MEDIUM] event={} rule={}: 写入通知表", event.getId(), rule.getCode());
            return;
        }

        if (severity.equals(ScadaAlarmSeverity.HIGH.getSeverity())) {
            // 商用版：站内信 + 短信，调用 yudao SmsService
            log.warn("告警通知[HIGH] event={} rule={} msg={}: 站内+短信",
                    event.getId(), rule.getCode(), event.getMessage());
            return;
        }

        if (severity.equals(ScadaAlarmSeverity.CRITICAL.getSeverity())) {
            // 最高优先级：全渠道通知
            log.error("告警通知[CRITICAL] event={} rule={} msg={}: 站内+短信+电话+微信",
                    event.getId(), rule.getCode(), event.getMessage());
            // 真实实现示例：
            // smsService.sendSms(onCallPhone, "SCADA严重告警：" + event.getMessage());
            // voiceService.call(onCallPhone, event.getMessage());
            // wechatService.push(openId, "告警", event.getMessage());
            return;
        }
    }
}
