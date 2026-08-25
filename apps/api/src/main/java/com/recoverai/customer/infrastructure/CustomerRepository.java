package com.recoverai.customer.infrastructure;

import com.recoverai.customer.domain.Customer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

  Optional<Customer> findByOrgIdAndCustomerRef(UUID orgId, String customerRef);

  Optional<Customer> findByOrgIdAndEmail(UUID orgId, String email);
}
