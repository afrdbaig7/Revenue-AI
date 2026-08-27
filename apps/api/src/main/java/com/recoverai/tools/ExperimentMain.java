package com.recoverai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recoverai.experiment.application.ExperimentService;
import com.recoverai.tenant.domain.Organization;
import com.recoverai.tenant.infrastructure.OrganizationRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;

/**
 * Standalone experiment runner: {@code make experiment} — runs the canonical seeded batch
 * (baseline vs RecoverAI) and prints the synthetic report as JSON. Active only when
 * {@code --recoverai.cli.experiment=true} is passed.
 */
@Slf4j
@SpringBootApplication(scanBasePackages = "com.recoverai")
public class ExperimentMain {

  public static void main(String[] args) {
    List<String> effective = new ArrayList<>(List.of(args));
    if (effective.stream().noneMatch(a -> a.startsWith("--recoverai.cli.experiment"))) {
      effective.add("--recoverai.cli.experiment=true");
    }
    new SpringApplicationBuilder(ExperimentMain.class).run(effective.toArray(new String[0]));
  }

  @Bean
  @ConditionalOnProperty(prefix = "recoverai.cli", name = "experiment", havingValue = "true")
  public ApplicationRunner experimentRunner(
      ExperimentService experiments, OrganizationRepository organizations, ObjectMapper mapper) {
    return args -> {
      Organization org = organizations.findAll().stream().findFirst().orElseGet(() -> {
        throw new IllegalStateException("No organization found — run `make seed` first");
      });
      var experiment = experiments.run(
          org.getId(),
          "CLI experiment — Baseline vs RecoverAI (seed 42, 10,000 incidents)",
          "Reproducible synthetic comparison",
          42,
          10_000,
          null,
          null);
      log.info("EXPERIMENT_RESULT\n{}", mapper.writerWithDefaultPrettyPrinter().writeValueAsString(experiment.getResults()));
      System.exit(0);
    };
  }
}
