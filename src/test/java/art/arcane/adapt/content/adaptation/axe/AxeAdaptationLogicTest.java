package art.arcane.adapt.content.adaptation.axe;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AxeAdaptationLogicTest {
  @Test
  void meleeBaseDamageMatchesAxeTier() {
    assertThat(AxeThrowingAxe.meleeBaseDamage(Material.NETHERITE_AXE)).isEqualTo(10D);
    assertThat(AxeThrowingAxe.meleeBaseDamage(Material.DIAMOND_AXE)).isEqualTo(9D);
    assertThat(AxeThrowingAxe.meleeBaseDamage(Material.IRON_AXE)).isEqualTo(9D);
    assertThat(AxeThrowingAxe.meleeBaseDamage(Material.STONE_AXE)).isEqualTo(9D);
    assertThat(AxeThrowingAxe.meleeBaseDamage(Material.WOODEN_AXE)).isEqualTo(7D);
    assertThat(AxeThrowingAxe.meleeBaseDamage(Material.GOLDEN_AXE)).isEqualTo(7D);
  }

  @Test
  void configDefaultsAreSaneAndNonZero() {
    AxeThrowingAxe.Config throwing = new AxeThrowingAxe.Config();
    assertThat(throwing.damageMultiplierBase).isGreaterThan(0D);
    assertThat(throwing.throwSpeedBase).isGreaterThan(0D);
    assertThat(throwing.durabilityCost).isGreaterThan(0);
    assertThat(throwing.maxLevel).isEqualTo(4);

    AxeSunder.Config sunder = new AxeSunder.Config();
    assertThat(sunder.shredPerStackBase).isGreaterThan(0D);
    assertThat(sunder.maxStacksBase).isGreaterThanOrEqualTo(1);
    assertThat(sunder.durationTicks).isGreaterThan(0);

    AxeCleave.Config cleave = new AxeCleave.Config();
    assertThat(cleave.targetCapBase).isEqualTo(2);
    assertThat(cleave.targetCapMaxBonus).isEqualTo(2);
    assertThat(cleave.damageShareBase).isGreaterThan(0D);

    AxeBarkHide.Config bark = new AxeBarkHide.Config();
    assertThat(bark.absorptionCapBase).isGreaterThanOrEqualTo(1);
    assertThat(bark.gracePeriodTicksBase).isGreaterThan(0D);

    AxeShieldSplitter.Config splitter = new AxeShieldSplitter.Config();
    assertThat(splitter.disableTicksBase).isGreaterThan(0D);
    assertThat(splitter.bonusDamagePctBase).isGreaterThan(0D);
  }
}
