package com.recoverai.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI documentation for the control-plane API. */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI recoverAiOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("RecoverAI API")
                .version("v1")
                .description(
                    "AI Revenue Recovery & Payment Reliability Engine — control plane. "
                        + "AI recommends; deterministic software authorizes and executes."))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearer",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList("bearer"));
  }
}
