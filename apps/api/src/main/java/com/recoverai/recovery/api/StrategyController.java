package com.recoverai.recovery.api;

import com.recoverai.recovery.domain.RecoveryStrategy;
import com.recoverai.recovery.infrastructure.RecoveryStrategyRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Strategy catalog (read-only) — the first-class strategies the engine may select. */
@RestController
@RequestMapping("/api/v1/strategies")
@RequiredArgsConstructor
public class StrategyController {

  private final RecoveryStrategyRepository strategies;

  @GetMapping
  public List<RecoveryStrategy> list() {
    return strategies.findByActiveTrueOrderByCostMinorBaseAsc();
  }
}
