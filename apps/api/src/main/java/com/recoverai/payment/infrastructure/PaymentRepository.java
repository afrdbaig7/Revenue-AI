package com.recoverai.payment.infrastructure;

import com.recoverai.payment.domain.Payment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

  Optional<Payment> findByProviderAndProviderPaymentId(String provider, String providerPaymentId);

  Optional<Payment> findByOrgIdAndId(UUID orgId, UUID id);
}
