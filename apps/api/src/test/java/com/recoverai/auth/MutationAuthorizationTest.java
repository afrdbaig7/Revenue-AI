package com.recoverai.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.recoverai.incident.api.IncidentController;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(MutationAuthorizationTest.Config.class)
class MutationAuthorizationTest {
  @Configuration @EnableMethodSecurity static class Config {
    @Bean IncidentController incidents() { return mock(IncidentController.class); }
  }
  @org.springframework.beans.factory.annotation.Autowired IncidentController incidents;
  @AfterEach void clear() { SecurityContextHolder.clearContext(); }

  @ParameterizedTest @ValueSource(strings = {"OWNER", "ADMIN", "OPERATOR", "ANALYST", "VIEWER"})
  void everyMutationEnforcesRole(String role) {
    var authentication = new UsernamePasswordAuthenticationToken("user", null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    SecurityContextHolder.getContext().setAuthentication(authentication);
    UUID id = UUID.randomUUID();
    if (role.equals("ANALYST") || role.equals("VIEWER")) {
      assertThatThrownBy(() -> incidents.cancel(authentication, id, null)).isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> incidents.reprocess(authentication, id)).isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> incidents.promise(authentication, id, null)).isInstanceOf(AccessDeniedException.class);
    } else {
      assertThatCode(() -> incidents.cancel(authentication, id, null)).doesNotThrowAnyException();
      assertThatCode(() -> incidents.reprocess(authentication, id)).doesNotThrowAnyException();
      assertThatCode(() -> incidents.promise(authentication, id, null)).doesNotThrowAnyException();
    }
  }
}
