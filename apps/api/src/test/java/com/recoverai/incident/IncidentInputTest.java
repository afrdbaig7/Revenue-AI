package com.recoverai.incident;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.recoverai.common.api.GlobalExceptionHandler;
import com.recoverai.common.tenant.CurrentUser;
import com.recoverai.incident.api.IncidentController;
import com.recoverai.incident.infrastructure.RevenueIncidentRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class IncidentInputTest {
  @Mock RevenueIncidentRepository incidents;
  @InjectMocks IncidentController controller;
  MockMvc mvc;
  final UUID id = UUID.randomUUID();
  final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
      new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), "operator@example.test", "Operator", "OPERATOR"), null);

  @BeforeEach void setup() { mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build(); }

  @Test void rejectsUnknownSortAndReversedDatesBeforeQuerying() throws Exception {
    mvc.perform(get("/api/v1/incidents").principal(auth).param("sort", "arbitraryField")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/incidents").principal(auth).param("createdFrom", "2026-10-26T00:00:00Z").param("createdBefore", "2026-10-25T00:00:00Z")).andExpect(status().isBadRequest());
    verifyNoInteractions(incidents);
  }

  @Test void usesAllowlistedSortWithStableTieBreaker() throws Exception {
    when(incidents.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
    mvc.perform(get("/api/v1/incidents").principal(auth).param("sort", "amount_desc")).andExpect(status().isOk());
    var paging = ArgumentCaptor.forClass(Pageable.class);
    verify(incidents).findAll(any(Specification.class), paging.capture());
    assertThat(paging.getValue().getSort().getOrderFor("amountMinor").getDirection()).isEqualTo(Sort.Direction.DESC);
    assertThat(paging.getValue().getSort().getOrderFor("id")).isNotNull();
  }

  @Test void rejectsInvalidPromisesAndEmptyCancellationBeforeSideEffects() throws Exception {
    String path = "/api/v1/incidents/" + id;
    mvc.perform(post(path + "/promise").principal(auth).contentType("application/json")
        .content("{\"text\":\"confirmed\",\"amountMinor\":0,\"channel\":\"UNSUPPORTED\",\"promisedAt\":\"2000-01-01T00:00:00Z\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(post(path + "/cancel").principal(auth).contentType("application/json").content("{\"reason\":\" \"}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(incidents);
  }
}
