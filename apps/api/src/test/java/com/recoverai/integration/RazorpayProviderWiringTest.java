package com.recoverai.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoverai.common.config.RecoverAiProperties;
import com.recoverai.integration.razorpay.RazorpayPaymentProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RazorpayProviderWiringTest {
  @Test void springUsesTheDependencyConstructorWhenCredentialCopyConstructorExists() {
    var props = mock(RecoverAiProperties.class, RETURNS_DEEP_STUBS);
    when(props.razorpay().connectTimeoutMs()).thenReturn(3000);
    new ApplicationContextRunner()
        .withBean(RecoverAiProperties.class, () -> props)
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .withUserConfiguration(RazorpayPaymentProvider.class)
        .run(context -> assertThat(context).hasNotFailed().hasSingleBean(RazorpayPaymentProvider.class));
  }
}
