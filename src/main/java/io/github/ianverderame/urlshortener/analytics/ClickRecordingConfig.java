package io.github.ianverderame.urlshortener.analytics;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The click recorder's bounded pool. It stays private to the recorder rather than being an {@code
 * Executor} bean, which would make Boot's own task executor back off. A full queue rejects (the
 * default abort policy), so a slow database costs counted clicks, never redirect latency.
 */
@Configuration(proxyBeanMethods = false)
class ClickRecordingConfig {

  @Bean(destroyMethod = "shutdown")
  ClickRecorder clickRecorder(
      ClickEventWriter writer,
      Clock clock,
      MeterRegistry meterRegistry,
      @Value("${app.clicks.threads}") int threads,
      @Value("${app.clicks.queue-capacity}") int queueCapacity) {
    ThreadPoolExecutor pool =
        new ThreadPoolExecutor(
            threads,
            threads,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(queueCapacity),
            Thread.ofPlatform().name("click-recorder-", 0).factory());
    return new ClickRecorder(pool, writer, clock, meterRegistry);
  }
}
