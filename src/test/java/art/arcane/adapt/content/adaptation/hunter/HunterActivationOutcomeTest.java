package art.arcane.adapt.content.adaptation.hunter;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HunterActivationOutcomeTest {
  private static final Path COMPONENT_SOURCE = Path.of("src/main/java/art/arcane/adapt/api/Component.java");
  private static final Path SOURCE_ROOT = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/hunter");
  private static final List<String> REACTIVE_BUFFS = List.of(
      "HunterJumpBoost",
      "HunterLuck",
      "HunterStrength",
      "HunterInvis",
      "HunterRegen",
      "HunterResistance"
  );

  @Test
  void trophyAndHeadChancesAreIndependent() {
    assertThat(HunterTrophySkinner.decideDrops(0.8D, 0.01D, 0.5D, 0.1D))
        .isEqualTo(new HunterTrophySkinner.DropOutcome(false, true));
    assertThat(HunterTrophySkinner.decideDrops(0.1D, 0.8D, 0.5D, 0.1D))
        .isEqualTo(new HunterTrophySkinner.DropOutcome(true, false));
    assertThat(HunterTrophySkinner.decideDrops(0.1D, 0.01D, 0.5D, 0.1D))
        .isEqualTo(new HunterTrophySkinner.DropOutcome(true, true));
  }

  @Test
  void reactiveBuffHandlersUseOneActiveLevelAndGateRewardsOnApplication() throws Exception {
    for (String className : REACTIVE_BUFFS) {
      String source = Files.readString(SOURCE_ROOT.resolve(className + ".java"));

      assertThat(source)
          .as(className)
          .contains(
              "@EventHandler(ignoreCancelled = true)",
              "int level = getActiveLevel(p);",
              "if (level <= 0",
              "private void recordActivation(Player p)",
              "AbilityCharge charge = payItemCostDeferred(",
              "AtomicBoolean defaultApplied = new AtomicBoolean();",
              "refundCost(charge.activationId(), AbilityRefundReason.ACTIVATION_FAILED);"
          )
          .doesNotContain("hasActiveAdaptation(p)", "getLevel(p)");
      assertThat(source.indexOf("recordActivation(p);"))
          .as(className)
          .isGreaterThan(source.indexOf("apply"));
    }
  }

  @Test
  void synchronousPotionStackingReportsTheActualApplicationResult() throws Exception {
    String source = Files.readString(COMPONENT_SOURCE);
    int start = source.indexOf("default boolean addPotionStacksNow(");
    int end = source.indexOf("default double blockXP", start);
    String method = source.substring(start, end);

    assertThat(method)
        .contains(
            "if (activeEffect != null && !overlap)",
            "boolean applied = p.addPotionEffect(",
            "return applied;"
        )
        .doesNotContain("J.runEntity(");
  }
}
