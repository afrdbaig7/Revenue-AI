package com.recoverai.payment.infrastructure;

import com.recoverai.payment.domain.PaymentAttempt;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

  long countByPaymentId(UUID paymentId);
}
