package cn.iocoder.yudao.module.scada.job;

import cn.iocoder.yudao.module.scada.framework.scada.config.ScadaConfiguration;
import cn.iocoder.yudao.module.scada.service.collect.CollectTaskExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * SCADA 调度入口
 *
 * <p>用 Spring Boot 的 ApplicationRunner 而不是 @PostConstruct——
 * 等所有 Bean 初始化完、连接池暖起来后再启动调度线程。</p>
 *
 * <p>商用版可加配置开关 scada.collect.enabled=false 来关闭（如开发环境），
 * 也可以增加延迟启动避免容器还在初始化连接池时被高频读冲击。</p>
 */
@Slf4j
@Component
public class ScadaCollectJob implements ApplicationRunner {

    private final CollectTaskExecutor executor;
    private final ScadaConfiguration scadaConfig;

    public ScadaCollectJob(CollectTaskExecutor executor, ScadaConfiguration scadaConfig) {
        this.executor = executor;
        this.scadaConfig = scadaConfig;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!scadaConfig.isCollectEnabled()) {
            log.info("SCADA 调度未启用（scada.collect.enabled=false）");
            return;
        }
        executor.start();
    }
}
