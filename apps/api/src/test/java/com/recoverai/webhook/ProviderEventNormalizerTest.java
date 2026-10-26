package com.recoverai.webhook;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoverai.common.api.ApiException;
import com.recoverai.webhook.application.ProviderEventNormalizer;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProviderEventNormalizerTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final ProviderEventNormalizer normalizer = new ProviderEventNormalizer(mapper);

  @Test void unwrapsProviderEntityAndAllowsOnlyRequiredFields() throws Exception {
    UUID merchant = UUID.randomUUID();
    var result = normalizer.normalize(mapper.readTree("""
        {"event":"payment.failed","payload":{"payment":{"entity":{
          "id":"pay_test","amount":1000,"currency":"INR","status":"failed",
          "card":{"number":"sensitive"},"token":"secret","vpa":"private@upi",
          "notes":{"merchant_id":"attacker"},"error_code":"BANK_DECLINE"
        }}}}
        """), merchant).path("payload").path("payment");
    assertThat(result.path("id").asText()).isEqualTo("pay_test");
    assertThat(result.has("card")).isFalse();
    assertThat(result.has("token")).isFalse();
    assertThat(result.has("vpa")).isFalse();
    assertThat(result.path("notes").path("merchant_id").asText()).isEqualTo(merchant.toString());
  }

  @Test void rejectsMalformedPaymentInsteadOfDefaultingMoneyOrStatus() throws Exception {
    var event = mapper.readTree("{\"event\":\"payment.failed\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_test\"}}}}");
    assertThatThrownBy(() -> normalizer.normalize(event, UUID.randomUUID())).isInstanceOf(ApiException.class);
  }

  @Test void rejectsMissingPaymentEntity() throws Exception {
    var event = mapper.readTree("{\"event\":\"payment.failed\",\"payload\":{}}");
    assertThatThrownBy(() -> normalizer.normalize(event, UUID.randomUUID())).isInstanceOf(ApiException.class);
  }
}
