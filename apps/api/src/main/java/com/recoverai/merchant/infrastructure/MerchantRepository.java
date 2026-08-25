package com.recoverai.merchant.infrastructure;

import com.recoverai.merchant.domain.Merchant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

  List<Merchant> findByOrgId(UUID orgId);
}
