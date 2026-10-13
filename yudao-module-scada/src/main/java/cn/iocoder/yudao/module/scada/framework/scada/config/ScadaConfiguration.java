package cn.iocoder.yudao.module.scada.framework.scada.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SCADA 模块装配配置
 *
 * <p>负责：</p>
 * <ol>
 *   <li>把 {@link ScadaProperties} 注册为 {@code @ConfigurationProperties} bean</li>
 *   <li>构建采集任务执行线程池 {@link #collectExecutor}，
 *       供 {@code CollectTaskExecutor} 提交任务用</li>
 * </ol>
 *
 * <p>线程池参数取舍：</p>
 * <ul>
 *   <li>core=8 / max=32：单台中型制造工厂典型 4-16 台 PLC，按每台 2 个并行任务算 32 已够</li>
 *   <li>queue=200：调用方 {@code CallerRunsPolicy}，队列满后由调度线程自己跑，
 *       自然形成背压避免内存爆掉</li>
 *   <li>线程命名 {@code scada-collect-} 前缀，jstack 时一眼能找到</li>
 * </ul>
 *
 * <p>为什么不直接用 {@code yudao.threadPool} 全局池？因为 SCADA 的 IO 任务是阻塞型，
 * 跟 yudao 默认池里 CPU 密集型任务（excel 导出、邮件发送）放一起会互相拖慢。
 * 独立池更便于按业务限流。</p>
 */
@Configuration
@EnableConfigurationProperties(ScadaConfiguration.ScadaProperties.class)
public class ScadaConfiguration {

    @Bean(name = "scadaCollectExecutor")
    public ThreadPoolExecutor scadaCollectExecutor(ScadaProperties props) {
        ScadaProperties.ThreadPool tp = props.getThreadPool();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                tp.getCoreSize(),
                tp.getMaxSize(),
                60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(tp.getQueueSize()),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("scada-collect-" + t.getId());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        return executor;
    }

    @Data
    @ConfigurationProperties(prefix = "scada")
    public static class ScadaProperties {

        /** 采集总开关，false 时 {@code ScadaCollectJob} 不启动调度器 */
        private boolean collectEnabled = true;

        /** Replay 路径，断网补采的本地缓存 */
        private String replayPath = "./data/scada-replay";

        /** Replay 缓存上限条数，超过后旧数据被淘汰 */
        private int replayMaxKeys = 1_000_000;

        private final ThreadPool threadPool = new ThreadPool();

        @Data
        public static class ThreadPool {
            private int coreSize = 8;
            private int maxSize = 32;
            private int queueSize = 200;
        }
    }
}
