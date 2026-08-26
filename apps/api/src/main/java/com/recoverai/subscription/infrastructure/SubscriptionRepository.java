package com.recoverai.subscription.infrastructure;

import com.recoverai.subscription.domain.Subscription;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

  Optional<Subscription> findByProviderSubscriptionId(String providerSubscriptionId);

  Optional<Subscription> findByOrgIdAndId(UUID orgId, UUID id);
}
