package com.recoverai.webhook.infrastructure;

import com.recoverai.webhook.domain.WebhookInbox;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookInboxRepository extends JpaRepository<WebhookInbox, UUID> {

  Optional<WebhookInbox> findByProviderAndProviderEventId(String provider, String providerEventId);

  Page<WebhookInbox> findByOrgIdOrderByReceivedAtDesc(UUID orgId, Pageable pageable);

  List<WebhookInbox> findByProcessingStatus(String processingStatus);

  long countByProcessingStatus(String processingStatus);
}
