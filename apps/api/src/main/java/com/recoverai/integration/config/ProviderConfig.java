package com.recoverai.integration.config;

import com.recoverai.common.config.RecoverAiProperties;
import com.recoverai.integration.domain.PaymentProvider;
import com.recoverai.integration.razorpay.MockRazorpayProvider;
import com.recoverai.integration.razorpay.RazorpayPaymentProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Provider selection: real Razorpay adapter when test credentials exist and mock mode is
 * off; otherwise the fixture-based mock (clearly labeled SIMULATED).
 */
@Configuration
public class ProviderConfig {

  @Bean
  @Primary
  public PaymentProvider paymentProvider(
      RecoverAiProperties props, RazorpayPaymentProvider razorpay, MockRazorpayProvider mock,
      com.recoverai.merchant.infrastructure.MerchantIntegrationRepository integrations,
      com.recoverai.merchant.application.SecretCipher cipher) {
    return new TenantPaymentProvider(props, integrations, cipher, razorpay, mock);
  }
}
