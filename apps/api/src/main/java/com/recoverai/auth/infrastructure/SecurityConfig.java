package com.recoverai.auth.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoverai.common.api.ApiError;
import com.recoverai.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Security configuration.
 *
 * <p>Browser flow: HttpOnly cookies + CSRF token header. API-client flow:
 * Authorization: Bearer (CSRF skipped). Webhooks authenticate by Razorpay signature, so
 * they are explicitly public to the session layer (verification happens in the handler).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter, ObjectMapper mapper)
      throws Exception {
    CsrfTokenRequestAttributeHandler csrfHandler = new BearerAwareCsrfHandler();

    http.csrf(csrf -> csrf
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            .csrfTokenRequestHandler(csrfHandler)
            // Signature-authenticated; JSON-body login/refresh are CSRF-resistant
            // (application/json + SameSite=Lax + CORS allowlist).
            .ignoringRequestMatchers(
                "/api/v1/webhooks/**", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/csrf"))
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.GET, "/actuator/health/**", "/actuator/prometheus", "/actuator/metrics").permitAll()
            .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error", "/favicon.ico").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/**").permitAll()
            .anyRequest().authenticated())
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint((req, res, e) ->
                BearerAwareCsrfHandler.writeJson(res, mapper, 401, ErrorCode.UNAUTHENTICATED.name(), "Authentication required"))
            .accessDeniedHandler((req, res, e) ->
                BearerAwareCsrfHandler.writeJson(res, mapper, 403, ErrorCode.FORBIDDEN.name(), "Insufficient permissions")))
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.addAllowedOrigin("http://localhost:3000");
    config.addAllowedOrigin("http://127.0.0.1:3000");
    config.addAllowedOriginPattern("https://*-*.e2b.app");
    config.setAllowCredentials(true);
    config.addAllowedHeader("*");
    config.addAllowedMethod("*");
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
