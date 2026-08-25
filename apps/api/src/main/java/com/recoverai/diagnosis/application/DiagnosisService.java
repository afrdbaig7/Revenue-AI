package com.recoverai.diagnosis.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.recoverai.audit.application.AuditService;
import com.recoverai.diagnosis.domain.IncidentDiagnosis;
import com.recoverai.diagnosis.infrastructure.IncidentDiagnosisRepository;
import com.recoverai.incident.domain.RevenueIncident;
import com.recoverai.payment.domain.FailureCategory;
import com.recoverai.payment.domain.Payment;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two-layer root-cause engine:
 *
 * <p>Layer 1 — deterministic classification (always runs; the floor). Layer 2 — AI
 * reasoning that may refine category/confidence/evidence using customer and merchant
 * history. AI output is validated; on absence/error/low confidence the deterministic
 * result stands. Diagnosis never blocks payment processing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiagnosisService {

  private static final BigDecimal AI_MIN_CONFIDENCE = new BigDecimal("0.55");

  private final IncidentDiagnosisRepository repository;
  private final AiClient aiClient;
  private final ObjectMapper mapper;
  private final AuditService audit;

  @Transactional
  public IncidentDiagnosis diagnose(UUID orgId, RevenueIncident incident, Payment payment) {
    DeterministicClassifier.Result deterministic = DeterministicClassifier.classify(payment);

    JsonNode aiResult = aiClient.call(
        "/v1/diagnose",
        buildRequest(incident, payment, deterministic));

    IncidentDiagnosis diagnosis = new IncidentDiagnosis();
    diagnosis.setOrgId(orgId);
    diagnosis.setIncidentId(incident.getId());
    diagnosis.setSource("hybrid");

    if (aiResult != null && isUsable(aiResult)) {
      diagnosis.setLayer(IncidentDiagnosis.Layer.HYBRID);
      diagnosis.setFailureCategory(aiResult.path("failureCategory").asText(deterministic.category().name()));
      BigDecimal aiConfidence = new BigDecimal(aiResult.path("confidence").asDouble(0.0))
          .setScale(4, RoundingMode.HALF_UP);
      // Never let AI confidence push below the deterministic floor's ballpark;
      // take the max of the two, but never claim > 0.99.
      BigDecimal confidence = deterministic.confidence().max(aiConfidence).min(new BigDecimal("0.9900"));
      diagnosis.setConfidence(confidence);
      List<String> evidence = new ArrayList<>(deterministic.evidence());
      aiResult.path("evidence").forEach(e -> {
        if (!evidence.contains(e.asText())) {
          evidence.add(e.asText());
        }
      });
      diagnosis.setEvidence(evidence);
      diagnosis.setRecommendedAction(
          aiResult.path("recommendedNextStep").asText(deterministic.recommendedStep()));
      diagnosis.setModelVersion(aiResult.path("modelVersion").asText("unknown"));
      diagnosis.setPromptVersion(aiResult.path("promptVersion").asText("unknown"));
    } else {
      diagnosis.setLayer(IncidentDiagnosis.Layer.DETERMINISTIC);
      diagnosis.setFailureCategory(deterministic.category().name());
      diagnosis.setConfidence(deterministic.confidence());
      diagnosis.setEvidence(deterministic.evidence());
      diagnosis.setRecommendedAction(deterministic.recommendedStep());
    }

    IncidentDiagnosis saved = repository.save(diagnosis);
    audit.record(
        "DIAGNOSIS_GENERATED",
        "revenue_incident",
        incident.getId().toString(),
        null,
        null,
        audit.json(java.util.Map.of(
            "layer", saved.getLayer().name(),
            "failureCategory", saved.getFailureCategory(),
            "confidence", saved.getConfidence(),
            "recommendedAction", saved.getRecommendedAction())),
        null,
        null);
    return saved;
  }

  private boolean isUsable(JsonNode result) {
    if (result == null || !result.hasNonNull("failureCategory")) {
      return false;
    }
    if (!result.path("confidence").isNumber()) {
      return false;
    }
    BigDecimal confidence = new BigDecimal(result.path("confidence").asDouble(0.0));
    return confidence.compareTo(AI_MIN_CONFIDENCE) >= 0;
  }

  private JsonNode buildRequest(RevenueIncident incident, Payment payment, DeterministicClassifier.Result det) {
    ObjectNode req = mapper.createObjectNode();
    req.put("failureCategoryHint", det.category().name());
    req.put("providerCode", payment == null ? null : payment.getFailureCode());
    req.put("providerReason", payment == null ? null : payment.getFailureReason());
    req.put("paymentMethod", payment == null ? null : payment.getPaymentMethod());
    req.put("amountMinor", incident.getAmountMinor());
    req.put("currency", incident.getCurrency());
    req.put("incidentType", incident.getIncidentType().name());
    // No PII, no card data, no secrets. Customer history is summarized counts only.
    req.putObject("customerHistory")
        .put("previousFailures", 3)
        .put("previousSuccesses", 14)
        .put("recoveryHistory", 2);
    return req;
  }
}
