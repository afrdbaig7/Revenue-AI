package com.recoverai.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** Original topic/partition/offset and exception headers are retained by the recoverer. */
@Configuration
@ConditionalOnProperty(prefix = "recoverai", name = "event-dispatch-mode", havingValue = "kafka")
public class KafkaFailureConfig {
  @Bean
  public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka) {
    var recoverer = new DeadLetterPublishingRecoverer(kafka);
    recoverer.setFailIfSendResultIsError(true);
    var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000, 3));
    handler.setCommitRecovered(true);
    return handler;
  }
}
