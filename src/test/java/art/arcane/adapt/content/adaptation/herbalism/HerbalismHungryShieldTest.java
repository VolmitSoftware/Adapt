package art.arcane.adapt.content.adaptation.herbalism;

import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HerbalismHungryShieldTest {
  private static final HerbalismHungryShield.Config CFG = new HerbalismHungryShield.Config();

  @Test
  void absorbedDamageIsPartialWhenHungerCannotCoverTheWholeHit() {
    assertThat(HerbalismHungryShield.absorbedDamage(20D, 0.5D, 10D, 6)).isEqualTo(4D);
    assertThat(HerbalismHungryShield.absorbedDamage(20D, 0.5D, 20D, 6)).isEqualTo(10D);
  }

  @Test
  void absorbedDamageIsZeroWithoutSpareHunger() {
    assertThat(HerbalismHungryShield.absorbedDamage(20D, 0.5D, 6D, 6)).isZero();
    assertThat(HerbalismHungryShield.absorbedDamage(20D, 0.5D, 2D, 6)).isZero();
  }

  @Test
  void absorbedDamageRejectsInvalidInput() {
    assertThat(HerbalismHungryShield.absorbedDamage(0D, 0.5D, 20D, 6)).isZero();
    assertThat(HerbalismHungryShield.absorbedDamage(-4D, 0.5D, 20D, 6)).isZero();
    assertThat(HerbalismHungryShield.absorbedDamage(20D, 0D, 20D, 6)).isZero();
    assertThat(HerbalismHungryShield.absorbedDamage(Double.NaN, 0.5D, 20D, 6)).isZero();
    assertThat(HerbalismHungryShield.absorbedDamage(20D, Double.POSITIVE_INFINITY, 20D, 6)).isZero();
    assertThat(HerbalismHungryShield.absorbedDamage(20D, 0.5D, Double.NaN, 6)).isZero();
  }

  @Test
  void levelOneCoversOnlyTheBasicCauses() {
    assertThat(HerbalismHungryShield.covers(DamageCause.CONTACT, 1, CFG)).isTrue();
    assertThat(HerbalismHungryShield.covers(DamageCause.CRAMMING, 1, CFG)).isTrue();
    assertThat(HerbalismHungryShield.covers(DamageCause.DROWNING, 1, CFG)).isTrue();
    assertThat(HerbalismHungryShield.covers(DamageCause.SUFFOCATION, 1, CFG)).isTrue();
    assertThat(HerbalismHungryShield.covers(DamageCause.FLY_INTO_WALL, 1, CFG)).isTrue();
    assertThat(HerbalismHungryShield.covers(DamageCause.HOT_FLOOR, 1, CFG)).isTrue();
    assertThat(HerbalismHungryShield.covers(DamageCause.FREEZE, 1, CFG)).isTrue();

    assertThat(HerbalismHungryShield.covers(DamageCause.ENTITY_ATTACK, 1, CFG)).isFalse();
    assertThat(HerbalismHungryShield.covers(DamageCause.FIRE, 1, CFG)).isFalse();
    assertThat(HerbalismHungryShield.covers(DamageCause.PROJECTILE, 1, CFG)).isFalse();
    assertThat(HerbalismHungryShield.covers(DamageCause.MAGIC, 1, CFG)).isFalse();
  }

  @Test
  void levelTwoUnlocksMelee() {
    assertThat(HerbalismHungryShield.covers(DamageCause.ENTITY_ATTACK, 2, CFG)).isTrue();
    assertThat(HerbalismHungryShield.covers(DamageCause.ENTITY_SWEEP_ATTACK, 2, CFG)).isTrue();
    assertThat(HerbalismHungryShield.covers(DamageCause.THORNS, 2, CFG)).isTrue();
    assertThat(HerbalismHungryShield.covers(DamageCause.FIRE, 2, CFG)).isFalse();
  }

  @Test
  void levelThreeUnlocksFire() {
    assertThat(HerbalismHungryShield.covers(DamageCause.FIRE, 3, CFG)).isTrue();
    assertThat(HerbalismHungryShield.covers(DamageCause.FIRE_TICK, 3, CFG)).isTrue();
    assertThat(HerbalismHungryShield.covers(DamageCause.LAVA, 3, CFG)).isTrue();
    assertThat(HerbalismHungryShield.covers(DamageCause.CAMPFIRE, 3, CFG)).isTrue();
    assertThat(HerbalismHungryShield.covers(DamageCause.PROJECTILE, 3, CFG)).isFalse();
  }

  @Test
  void levelFourUnlocksBurst() {
    assertThat(HerbalismHungryShield.covers(DamageCause.PROJECTILE, 4, CFG)).isTrue();
    assertThat(HerbalismHungryShield.covers(DamageCause.BLOCK_EXPLOSION, 4, CFG)).isTrue();
    assertThat(HerbalismHungryShield.covers(DamageCause.ENTITY_EXPLOSION, 4, CFG)).isTrue();
    assertThat(HerbalismHungryShield.covers(DamageCause.FALLING_BLOCK, 4, CFG)).isTrue();
    assertThat(HerbalismHungryShield.covers(DamageCause.LIGHTNING, 4, CFG)).isTrue();
    assertThat(HerbalismHungryShield.covers(DamageCause.MAGIC, 4, CFG)).isFalse();
  }

  @Test
  void levelFiveUnlocksMagic() {
    assertThat(HerbalismHungryShield.covers(DamageCause.MAGIC, 5, CFG)).isTrue();
    assertThat(HerbalismHungryShield.covers(DamageCause.POISON, 5, CFG)).isTrue();
    assertThat(HerbalismHungryShield.covers(DamageCause.WITHER, 5, CFG)).isTrue();
    assertThat(HerbalismHungryShield.covers(DamageCause.DRAGON_BREATH, 5, CFG)).isTrue();
    assertThat(HerbalismHungryShield.covers(DamageCause.SONIC_BOOM, 5, CFG)).isTrue();
  }

  @Test
  void fallAndUnavoidableCausesAreNeverCovered() {
    for (int level = 1; level <= 5; level++) {
      assertThat(HerbalismHungryShield.covers(DamageCause.FALL, level, CFG)).isFalse();
      assertThat(HerbalismHungryShield.covers(DamageCause.VOID, level, CFG)).isFalse();
      assertThat(HerbalismHungryShield.covers(DamageCause.KILL, level, CFG)).isFalse();
      assertThat(HerbalismHungryShield.covers(DamageCause.SUICIDE, level, CFG)).isFalse();
      assertThat(HerbalismHungryShield.covers(DamageCause.WORLD_BORDER, level, CFG)).isFalse();
      assertThat(HerbalismHungryShield.covers(DamageCause.CUSTOM, level, CFG)).isFalse();
      assertThat(HerbalismHungryShield.covers(DamageCause.STARVATION, level, CFG)).isFalse();
    }
  }

  @Test
  void coversRejectsMissingInput() {
    assertThat(HerbalismHungryShield.covers(null, 5, CFG)).isFalse();
    assertThat(HerbalismHungryShield.covers(DamageCause.CONTACT, 0, CFG)).isFalse();
    assertThat(HerbalismHungryShield.covers(DamageCause.CONTACT, 5, null)).isFalse();
  }

  @Test
  void damageOverTimeCausesAreRateLimited() {
    assertThat(HerbalismHungryShield.isDamageOverTime(DamageCause.FIRE)).isTrue();
    assertThat(HerbalismHungryShield.isDamageOverTime(DamageCause.FIRE_TICK)).isTrue();
    assertThat(HerbalismHungryShield.isDamageOverTime(DamageCause.LAVA)).isTrue();
    assertThat(HerbalismHungryShield.isDamageOverTime(DamageCause.CAMPFIRE)).isTrue();
    assertThat(HerbalismHungryShield.isDamageOverTime(DamageCause.HOT_FLOOR)).isTrue();
    assertThat(HerbalismHungryShield.isDamageOverTime(DamageCause.POISON)).isTrue();
    assertThat(HerbalismHungryShield.isDamageOverTime(DamageCause.WITHER)).isTrue();
    assertThat(HerbalismHungryShield.isDamageOverTime(DamageCause.DROWNING)).isTrue();
    assertThat(HerbalismHungryShield.isDamageOverTime(DamageCause.FREEZE)).isTrue();

    assertThat(HerbalismHungryShield.isDamageOverTime(DamageCause.ENTITY_ATTACK)).isFalse();
    assertThat(HerbalismHungryShield.isDamageOverTime(DamageCause.PROJECTILE)).isFalse();
    assertThat(HerbalismHungryShield.isDamageOverTime(null)).isFalse();
  }
}
