package com.recoverai.merchant.infrastructure;

import com.recoverai.merchant.domain.MerchantIntegration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantIntegrationRepository extends JpaRepository<MerchantIntegration, UUID> {

  List<MerchantIntegration> findByOrgIdAndActiveTrue(UUID orgId);

  List<MerchantIntegration> findByProviderAndActiveTrue(String provider);

  Optional<MerchantIntegration> findByOrgIdAndProviderAndMode(UUID orgId, String provider, String mode);
}
