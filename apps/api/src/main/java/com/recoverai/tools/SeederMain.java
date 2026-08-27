package com.recoverai.tools;

import com.recoverai.seed.DemoDataSeeder;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;

/**
 * Standalone seeding entry point: {@code make seed} (via PropertiesLauncher). Idempotent
 * — skips when demo data already exists. The runner only activates when
 * {@code --recoverai.cli.seed=true} is passed, so this class never interferes with the
 * API or the experiment CLI.
 */
@Slf4j
@SpringBootApplication(scanBasePackages = "com.recoverai")
public class SeederMain {

  public static void main(String[] args) {
    List<String> effective = new ArrayList<>(List.of(args));
    if (effective.stream().noneMatch(a -> a.startsWith("--recoverai.cli.seed"))) {
      effective.add("--recoverai.cli.seed=true");
    }
    new SpringApplicationBuilder(SeederMain.class).run(effective.toArray(new String[0]));
  }

  @Bean
  @ConditionalOnProperty(prefix = "recoverai.cli", name = "seed", havingValue = "true")
  public ApplicationRunner seederRunner(DemoDataSeeder seeder) {
    return args -> {
      log.info("SEED_START");
      seeder.seed();
      log.info("SEED_DONE");
      System.exit(0);
    };
  }
}
