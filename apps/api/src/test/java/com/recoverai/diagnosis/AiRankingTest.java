package com.recoverai.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoverai.diagnosis.application.AiRanking;
import com.recoverai.recovery.domain.RecoveryDecision.CandidateView;
import java.util.List;
import org.junit.jupiter.api.Test;

/** AI output is untrusted input: rankings are validated or rejected. */
class AiRankingTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final CandidateView a = new CandidateView("PAYMENT_LINK", 0.4, 100, 100, 1, 0, 0, 0, 6, "a");
  private final CandidateView b = new CandidateView("DELAYED_RETRY", 0.3, 90, 90, 1, 0, 0, 0, 30, "b");
  private final CandidateView c = new CandidateView("NO_ACTION", 0.0, 0, 0, 0, 0, 0, 0, 72, "c");
  private final List<CandidateView> scored = List.of(a, b, c);

  @Test
  void validRankingIsApplied() throws Exception {
    var response = mapper.readTree(
        "{\"ranking\":[{\"strategy\":\"NO_ACTION\"},{\"strategy\":\"PAYMENT_LINK\"},{\"strategy\":\"DELAYED_RETRY\"}]}");
    List<CandidateView> ranked = AiRanking.apply(response, scored);
    assertThat(ranked).isNotNull();
    assertThat(ranked.get(0).strategy()).isEqualTo("NO_ACTION");
    assertThat(ranked.get(1).strategy()).isEqualTo("PAYMENT_LINK");
  }

  @Test
  void unknownStrategyRejectsWholeRanking() throws Exception {
    var response = mapper.readTree("{\"ranking\":[{\"strategy\":\"HACK_STRATEGY\"}]}");
    assertThat(AiRanking.apply(response, scored)).isNull();
  }

  @Test
  void duplicateStrategyRejectsWholeRanking() throws Exception {
    var response = mapper.readTree(
        "{\"ranking\":[{\"strategy\":\"PAYMENT_LINK\"},{\"strategy\":\"PAYMENT_LINK\"},{\"strategy\":\"NO_ACTION\"}]}");
    assertThat(AiRanking.apply(response, scored)).isNull();
  }

  @Test
  void partialRankingRejectsWholeRanking() throws Exception {
    var response = mapper.readTree("{\"ranking\":[{\"strategy\":\"PAYMENT_LINK\"}]}");
    assertThat(AiRanking.apply(response, scored)).isNull();
  }

  @Test
  void malformedResponseRejected() throws Exception {
    var response = mapper.readTree("{\"ranking\":\"not-an-array\"}");
    assertThat(AiRanking.apply(response, scored)).isNull();
    assertThat(AiRanking.apply(null, scored)).isNull();
  }
}
