package com.recoverai.diagnosis.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.recoverai.recovery.domain.RecoveryDecision.CandidateView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies an AI-provided ranking to scored candidates. AI output is untrusted: the
 * response must list a permutation of the exact strategies we generated; any unknown
 * strategy, duplicate, or malformed structure is rejected and the deterministic order
 * stands.
 */
public final class AiRanking {

  private AiRanking() {}

  /**
   * @return re-ranked candidates or {@code null} when the AI response is unusable.
   */
  public static List<CandidateView> apply(JsonNode aiResult, List<CandidateView> scored) {
    if (aiResult == null || !aiResult.path("ranking").isArray()) {
      return null;
    }
    Map<String, CandidateView> byStrategy = new HashMap<>();
    for (CandidateView c : scored) {
      byStrategy.put(c.strategy(), c);
    }
    List<CandidateView> ranked = new ArrayList<>();
    for (JsonNode entry : aiResult.path("ranking")) {
      String strategy = entry.path("strategy").asText(null);
      if (strategy == null || !byStrategy.containsKey(strategy) || ranked.stream().anyMatch(r -> r.strategy().equals(strategy))) {
        return null; // unknown/duplicate strategy — reject the whole ranking
      }
      ranked.add(byStrategy.get(strategy));
    }
    if (ranked.size() != scored.size()) {
      return null;
    }
    return ranked;
  }
}
