package com.recoverai.support;

import com.recoverai.incident.domain.IncidentStatus;
import com.recoverai.incident.domain.IncidentType;
import com.recoverai.incident.domain.RevenueIncident;
import com.recoverai.incident.infrastructure.RevenueIncidentRepository;
import com.recoverai.merchant.domain.Merchant;
import com.recoverai.merchant.infrastructure.MerchantRepository;
import com.recoverai.tenant.domain.Organization;
import com.recoverai.tenant.infrastructure.OrganizationRepository;
import java.util.UUID;

/** Creates the minimum valid tenant graph required by persistence integration tests. */
public final class PersistenceTestFixtures {

  private PersistenceTestFixtures() {}

  public static Organization organization(
      OrganizationRepository organizations, String prefix) {
    return organizations.save(
        new Organization(
            prefix + " Org",
            prefix.toLowerCase().replaceAll("[^a-z0-9]+", "-") + "-" + UUID.randomUUID()));
  }

  public static TenantIncident tenantIncident(
      OrganizationRepository organizations,
      MerchantRepository merchants,
      RevenueIncidentRepository incidents,
      String prefix) {
    Organization organization = organization(organizations, prefix);
    Merchant merchant = merchants.save(new Merchant(organization.getId(), prefix + " Merchant"));

    RevenueIncident incident = new RevenueIncident();
    incident.setOrgId(organization.getId());
    incident.setMerchantId(merchant.getId());
    incident.setIncidentType(IncidentType.PAYMENT_FAILURE);
    incident.setStatus(IncidentStatus.DETECTED);
    incident.setAmountMinor(10_000);
    incident.setCurrency("INR");
    incident.setFailureCategory("TEST_FAILURE");
    RevenueIncident saved = incidents.saveAndFlush(incident);

    return new TenantIncident(organization.getId(), saved.getId());
  }

  public record TenantIncident(UUID orgId, UUID incidentId) {}
}
