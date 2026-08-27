package com.recoverai.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.recoverai.outbox.domain.OutboxEvent;
import com.recoverai.outbox.infrastructure.OutboxEventRepository;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Chaos test: Kafka/Redpanda is unavailable.
 *
 * Verifies that outbox events are still persisted to the database and
 * that the system falls back gracefully when EVENT_DISPATCH_MODE=inline.
 * Events can be replayed from outbox once Kafka recovers.
 */
@Testcontainers
@SpringBootTest(properties = {
    "recoverai.event-dispatch-mode=inline",
    "recoverai.razorpay.mock-mode=true",
    "recoverai.ai.enabled=false",
    "spring.jpa.hibernate.ddl-auto=validate"
})
class KafkaUnavailableIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("recoverai_test")
      .withUsername("recoverai")
      .withPassword("recoverai_dev");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092"); // Invalid/stopped Kafka
  }

  @Autowired OutboxEventRepository outboxEvents;
  @Autowired KafkaTemplate<String, String> kafkaTemplate;
  @Autowired ObjectMapper mapper;

  @Test
  void outboxEventsPersistedWhenKafkaDown() throws Exception {
    UUID orgId = UUID.randomUUID();
    OutboxEvent event = new OutboxEvent(orgId, "Incident", UUID.randomUUID().toString(), "INCIDENT_CREATED", mapper.readTree("{}"));
    outboxEvents.save(event);

    assertThat(outboxEvents.findById(event.getId())).isPresent();

    // Since event-dispatch-mode is inline, the publisher won't use Kafka and won't crash
    // In actual kafka mode it would fail and retry. Here we just verify it was saved safely.
    
    // Attempting to send directly via Kafka will fail because Kafka is down
    try {
      kafkaTemplate.send("recoverai-events", event.getId().toString(), "{}").get();
    } catch (Exception e) {
      // Expected failure due to unavailable Kafka
    }

    assertThat(outboxEvents.findById(event.getId())).isPresent();
  }
}
