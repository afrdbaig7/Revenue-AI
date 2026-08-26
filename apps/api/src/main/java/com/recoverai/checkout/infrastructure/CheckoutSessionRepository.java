package com.recoverai.checkout.infrastructure;

import com.recoverai.checkout.domain.CheckoutSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckoutSessionRepository extends JpaRepository<CheckoutSession, UUID> {

  Optional<CheckoutSession> findByProviderSessionId(String providerSessionId);

  Optional<CheckoutSession> findByOrgIdAndId(UUID orgId, UUID id);
}
