package com.recoverai.integration.config;

import com.recoverai.common.config.RecoverAiProperties;
import com.recoverai.common.tenant.TenantContext;
import com.recoverai.integration.domain.PaymentProvider;
import com.recoverai.integration.razorpay.MockRazorpayProvider;
import com.recoverai.integration.razorpay.RazorpayPaymentProvider;
import com.recoverai.merchant.application.SecretCipher;
import com.recoverai.merchant.infrastructure.MerchantIntegrationRepository;
import lombok.RequiredArgsConstructor;

/** Immutable request-bound adapters share transport, never merchant credentials. */
@RequiredArgsConstructor
public class TenantPaymentProvider implements PaymentProvider {
  private final RecoverAiProperties props;
  private final MerchantIntegrationRepository integrations;
  private final SecretCipher cipher;
  private final RazorpayPaymentProvider real;
  private final MockRazorpayProvider mock;

  private PaymentProvider resolve() throws ProviderException {
    var orgId = TenantContext.orgId();
    if (orgId == null) throw new ProviderException("AUTHENTICATION", "Organization context required");
    var configured = integrations.findByOrgIdAndProviderAndMode(orgId, "razorpay", "TEST");
    if (configured.isPresent() && !configured.get().isActive()) throw new ProviderException("AUTHENTICATION", "Integration disabled");
    if (props.razorpay().mockMode()) return mock;
    var integration = configured.orElseThrow(() -> new ProviderException("AUTHENTICATION", "Configure a test integration"));
    if (integration.getKeyIdEncrypted() == null || integration.getKeySecretEncrypted() == null) throw new ProviderException("AUTHENTICATION", "Test credentials missing");
    return real.withCredentials(cipher.decrypt(integration.getKeyIdEncrypted()), cipher.decrypt(integration.getKeySecretEncrypted()));
  }

  public String name() { return "razorpay"; }
  public boolean isMock() { return props.razorpay().mockMode(); }
  public ProviderPayment fetchPayment(String id) throws ProviderException { return resolve().fetchPayment(id); }
  public ProviderOrder createOrder(long amount, String currency, String receipt) throws ProviderException { return resolve().createOrder(amount, currency, receipt); }
  public PaymentLink createPaymentLink(long amount, String currency, String description, String email, String phone, String notes) throws ProviderException {
    return resolve().createPaymentLink(amount, currency, description, email, phone, notes);
  }
}
