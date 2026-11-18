package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryNamesTest {
  @Test
  void vanillaIdsKeepTheirBukkitEnumNames() {
    assertThat(DiscoveryNames.enumName(Id.minecraft("diamond_sword"))).isEqualTo("DIAMOND_SWORD");
    assertThat(DiscoveryNames.enumName(Id.minecraft("zombie_villager"))).isEqualTo("ZOMBIE_VILLAGER");
    assertThat(DiscoveryNames.enumName(Id.of("example", "ruby"))).isEqualTo("example:ruby");
  }

  @Test
  void enchantmentsKeepTheLegacyBukkitNames() {
    assertThat(DiscoveryNames.enchantment(Id.minecraft("protection"))).isEqualTo("PROTECTION_ENVIRONMENTAL");
    assertThat(DiscoveryNames.enchantment(Id.minecraft("unbreaking"))).isEqualTo("DURABILITY");
    assertThat(DiscoveryNames.enchantment(Id.minecraft("luck_of_the_sea"))).isEqualTo("LUCK");
    assertThat(DiscoveryNames.enchantment(Id.minecraft("mending"))).isEqualTo("MENDING");
    assertThat(DiscoveryNames.enchantment(Id.of("example", "soulbound"))).isEqualTo("example:soulbound");
  }

  @Test
  void effectsKeepTheLegacyBukkitNamesAndFallBackToTheKey() {
    assertThat(DiscoveryNames.effect(Id.minecraft("slowness"))).isEqualTo("SLOW");
    assertThat(DiscoveryNames.effect(Id.minecraft("strength"))).isEqualTo("INCREASE_DAMAGE");
    assertThat(DiscoveryNames.effect(Id.minecraft("darkness"))).isEqualTo("DARKNESS");
    assertThat(DiscoveryNames.effect(Id.minecraft("trial_omen"))).isEqualTo("minecraft:trial_omen");
  }
}
