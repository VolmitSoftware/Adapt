package art.arcane.adapt.content.skill;

import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeWorld;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillStealthTest {
  private static final FakeWorld WORLD = new FakeWorld("world");

  @Test
  void unawareOpenerPaysTheBaseAndSneakingUnseenAddsTheBonus() {
    assertThat(SkillStealth.openerXp(150D, 200D, false)).isEqualTo(150D);
    assertThat(SkillStealth.openerXp(150D, 200D, true)).isEqualTo(350D);
  }

  @Test
  void negativeOpenerTuningIsClamped() {
    assertThat(SkillStealth.openerXp(-10D, -20D, true)).isZero();
  }

  @Test
  void aMobFacingThePlayerIsLookingAtThem() {
    Loc mob = new Loc(WORLD, 0D, 64D, 0D, 0F, 0F);
    assertThat(SkillStealth.isLookingAt(mob, new Loc(WORLD, 0D, 64D, 5D))).isTrue();
  }

  @Test
  void aPlayerBehindOrBesideTheMobIsUnseen() {
    Loc mob = new Loc(WORLD, 0D, 64D, 0D, 0F, 0F);
    assertThat(SkillStealth.isLookingAt(mob, new Loc(WORLD, 0D, 64D, -5D))).isFalse();
    assertThat(SkillStealth.isLookingAt(mob, new Loc(WORLD, 5D, 64D, 0D))).isFalse();
  }
}
