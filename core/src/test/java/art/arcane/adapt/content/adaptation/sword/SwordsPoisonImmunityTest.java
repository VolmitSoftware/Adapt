package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakeWorld;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SwordsPoisonImmunityTest {
  private static final FakeWorld WORLD = new FakeWorld("poison");

  @Test
  void zombieSkeletonAndSpiderFamiliesAreImmuneByTraitIncludingModdedTypes() {
    assertThat(SwordsPoisonedBlade.isPoisonImmune(living("othermod:bog_zombie", EntityTrait.ZOMBIE))).isTrue();
    assertThat(SwordsPoisonedBlade.isPoisonImmune(living("minecraft:parched", EntityTrait.SKELETON))).isTrue();
    assertThat(SwordsPoisonedBlade.isPoisonImmune(living("minecraft:cave_spider", EntityTrait.SPIDER))).isTrue();
  }

  @Test
  void singleClassMobsAreImmuneByType() {
    for (String type : new String[] {"skeleton_horse", "zombie_horse", "phantom", "wither", "zoglin", "giant"}) {
      assertThat(SwordsPoisonedBlade.isPoisonImmune(living("minecraft:" + type, null))).as(type).isTrue();
    }
  }

  @Test
  void otherUndeadAndOrdinaryMobsAreNotImmune() {
    assertThat(SwordsPoisonedBlade.isPoisonImmune(living("minecraft:pig", null))).isFalse();
    assertThat(SwordsPoisonedBlade.isPoisonImmune(living("minecraft:zombie_nautilus", EntityTrait.UNDEAD))).isFalse();
  }

  private static FakeLiving living(String type, EntityTrait trait) {
    FakeLiving living = new FakeLiving(Id.parse(type), new Loc(WORLD, 0.5D, 64D, 0.5D));
    if (trait != null) {
      living.trait(trait);
    }
    return living;
  }
}
