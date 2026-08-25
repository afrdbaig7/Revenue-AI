package com.recoverai.communication.application;

import com.recoverai.communication.domain.Communication;
import com.recoverai.communication.domain.Communication.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Demo notification provider: renders messages into the in-app communication inbox.
 * Every send is marked SIMULATED — never mislabeled as a real channel delivery.
 */
@Component
@Slf4j
public class DemoNotificationProvider implements NotificationProvider {

  @Override
  public Channel channel() {
    return Channel.DEMO_INBOX;
  }

  @Override
  public String providerName() {
    return "demo-inbox";
  }

  @Override
  public SendResult send(Communication message, String idempotencyRef) {
    log.info(
        "DEMO_NOTIFICATION channel={} incident={} template={}",
        message.getChannel(),
        message.getIncidentId(),
        message.getTemplate());
    return new SendResult("demo-" + idempotencyRef, true, "rendered to demo inbox");
  }

  @Override
  public boolean isReal() {
    return false;
  }
}
