package art.arcane.adapt.content.skill;

import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.testkit.FakeItem;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SkillTuningRulesTest {
  @Test
  void netherMobsPayTwoXpPerMaxHealth() {
    assertThat(SkillNether.netherMobKillXp(2D, 20D)).isEqualTo(40D);
    assertThat(SkillNether.netherMobKillXp(2D, Double.NaN)).isZero();
    assertThat(SkillNether.netherMobKillXp(-2D, 20D)).isZero();
  }

  @Test
  void netherOresPayHalfThePickaxeOreBonus() {
    SkillNether.Config config = new SkillNether.Config();
    SkillPickaxes.Config pickaxes = new SkillPickaxes.Config();
    assertThat(config.getNetherQuartzOreXp()).isEqualTo(pickaxes.netherQuartzBonus / 2D);
    assertThat(config.getNetherGoldOreXp()).isEqualTo(pickaxes.netherGoldBonus / 2D);
    assertThat(config.getAncientDebrisXp()).isEqualTo(pickaxes.debrisBonus / 2D);
  }

  @Test
  void onlyPortalsCountAsPortalTrips() {
    assertThat(SkillRift.isPortalTrip(TeleportCause.NETHER_PORTAL)).isTrue();
    assertThat(SkillRift.isPortalTrip(TeleportCause.END_PORTAL)).isTrue();
    assertThat(SkillRift.isPortalTrip(TeleportCause.END_GATEWAY)).isTrue();
    assertThat(SkillRift.isPortalTrip(TeleportCause.ENDER_PEARL)).isFalse();
    assertThat(SkillRift.isPortalTrip(TeleportCause.CHORUS_FRUIT)).isFalse();
  }

  @Test
  void blockedHitsPaySixPerDamageWithAFloorOfTen() {
    assertThat(SkillBlocking.blockedXp(6D, 10D, 5D)).isEqualTo(30D);
    assertThat(SkillBlocking.blockedXp(6D, 10D, 1D)).isEqualTo(10D);
    assertThat(SkillBlocking.blockedXp(6D, 10D, 0D)).isZero();
  }

  @Test
  void breedingPairsAreOrderIndependent() {
    UUID mother = UUID.randomUUID();
    UUID father = UUID.randomUUID();
    assertThat(SkillTaming.pairKey(mother, father)).isEqualTo(SkillTaming.pairKey(father, mother));
  }

  @Test
  void petKillsPayABasePlusMaxHealth() {
    assertThat(SkillTaming.petKillXp(25D, 1D, 20D)).isEqualTo(45D);
    assertThat(SkillTaming.petKillXp(25D, 1D, Double.NaN)).isEqualTo(25D);
  }

  @Test
  void hunterKillsAddAFlatBonusToMaxHealth() {
    assertThat(SkillHunter.killXp(20D, 4D, 30D)).isEqualTo(110D);
  }

  @Test
  void treasureCatchesAreRecognised() {
    assertThat(SkillSeaborne.isTreasure(FakeItem.of("name_tag"))).isTrue();
    assertThat(SkillSeaborne.isTreasure(FakeItem.of("enchanted_book"))).isTrue();
    assertThat(SkillSeaborne.isTreasure(FakeItem.tool("bow", 384).enchant("power", 2))).isTrue();
    assertThat(SkillSeaborne.isTreasure(FakeItem.tool("fishing_rod", 64))).isFalse();
    assertThat(SkillSeaborne.isTreasure(FakeItem.of("cod"))).isFalse();
  }
}
