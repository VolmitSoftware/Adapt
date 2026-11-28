package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedIronFists;
import art.arcane.adapt.content.skill.SkillNether;
import art.arcane.adapt.content.skill.SkillSeaborne;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.content.skill.SkillUnarmed;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Assassinate deals the held weapon's own attack damage times its level multiplier (1.7 + levelPercent * 1.2, so 2.0,
 * 2.3, 2.6 and 2.9), at most 30, and nothing else scales it: a netherite sword (8) strikes for 16 at level 1 and 23.2
 * at level 4, whatever the incoming hit was.
 */
class StealthAssassinateStrikeTest {
  private static final Id BASE_ATTACK_DAMAGE = Id.minecraft("base_attack_damage");
  private static final double TOLERANCE = 1.0E-9D;

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Shade", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillStealth.class, SkillUnarmed.class, SkillSwords.class, SkillSeaborne.class,
        SkillNether.class));
    profile = adapt.join(player);
    player.setAttributeBase(Attributes.ATTACK_DAMAGE, 1D);
    player.setSneaking(true);
    learn("stealth", StealthCore.class, 1);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aBareHandStrikesForTwiceItsOnePoint() {
    learn("stealth", StealthAssassinate.class, 1);

    assertThat(strike(9D).damage()).isCloseTo(2D, within(TOLERANCE));
  }

  @Test
  void anIronSwordStrikesForTwiceItsSixPoints() {
    learn("stealth", StealthAssassinate.class, 1);
    wield("iron_sword", 5D);

    assertThat(strike(9D).damage()).isCloseTo(12D, within(TOLERANCE));
  }

  @Test
  void aNetheriteSwordScalesFrom16To23_2AcrossLevels() {
    double[] expected = {16D, 18.4D, 20.8D, 23.2D};
    for (int level = 1; level <= 4; level++) {
      double multiplier = StealthAssassinate.computeMultiplier(1.7D, 1.2D, level / 4D);
      assertThat(StealthAssassinate.computeStrike(8D, multiplier, 30D)).as("level %s", level)
          .isCloseTo(expected[level - 1], within(TOLERANCE));
    }
  }

  @Test
  void aNetheriteSwordAtLevelFourStrikesFor23_2() {
    learn("stealth", StealthAssassinate.class, 4);
    wield("netherite_sword", 7D);

    assertThat(strike(9D).damage()).isCloseTo(23.2D, within(TOLERANCE));
  }

  @Test
  void aModdedTwentyPointWeaponStopsAtTheThirtyCap() {
    learn("stealth", StealthAssassinate.class, 1);
    wield("cobblestone", 19D);

    assertThat(strike(9D).damage()).isCloseTo(30D, within(TOLERANCE));
  }

  @Test
  void strengthAndTheCritDoNotScaleTheStrike() {
    learn("stealth", StealthAssassinate.class, 1);
    wield("netherite_sword", 7D);
    player.setModifier(Attributes.ATTACK_DAMAGE, new AttributeMod(Id.minecraft("effect.strength"), 6D,
        AttributeOperation.ADD_VALUE));

    EntityDamageByEntityEvent crit = adapt.post(new EntityDamageByEntityEvent(target(), player, player,
        DamageCause.ENTITY_ATTACK, Id.minecraft("player_attack"), (8D + 6D) * 1.5D, true, FinalDamage.IDENTITY));

    assertThat(crit.damage()).isCloseTo(16D, within(TOLERANCE));
  }

  @Test
  void everyMaxedUnarmedAdaptationLeavesABareHandStrikeAt2_9() {
    learn("stealth", StealthCore.class, 3);
    learn("stealth", StealthAssassinate.class, 4);
    learnAll("unarmed");
    player.setSprinting(true);

    assertThat(strike(40D).damage()).isCloseTo(2.9D, within(TOLERANCE));
  }

  @Test
  void everyMaxedSwordsAdaptationLeavesANetheriteStrikeAt23_2() {
    learn("stealth", StealthCore.class, 3);
    learn("stealth", StealthAssassinate.class, 4);
    learnAll("swords");
    wield("netherite_sword", 7D);

    assertThat(strike(40D).damage()).isCloseTo(23.2D, within(TOLERANCE));
  }

  @Test
  void stormStrikeInWaterDoesNotAddToTheStrike() {
    learn("stealth", StealthCore.class, 3);
    learn("stealth", StealthAssassinate.class, 4);
    learnAll("seaborne");
    wield("netherite_sword", 7D);
    player.setInWater(true);

    assertThat(strike(40D).damage()).isCloseTo(23.2D, within(TOLERANCE));
  }

  @Test
  void magmaSkinOnFireDoesNotAddToTheStrike() {
    learn("stealth", StealthCore.class, 3);
    learn("stealth", StealthAssassinate.class, 4);
    learnAll("nether");
    wield("netherite_sword", 7D);
    player.setFireTicks(100);

    assertThat(strike(40D).damage()).isCloseTo(23.2D, within(TOLERANCE));
  }

  @Test
  void theVictimsArmorStillReducesTheStrike() {
    learn("stealth", StealthAssassinate.class, 1);
    wield("netherite_sword", 7D);

    EntityDamageByEntityEvent hit = adapt.post(new EntityDamageByEntityEvent(target(), player, player,
        DamageCause.ENTITY_ATTACK, Id.minecraft("player_attack"), 9D, false, (double base) -> base * 0.6D));

    assertThat(hit.ignoreReductions()).isFalse();
    assertThat(hit.finalDamage()).isCloseTo(16D * 0.6D, within(TOLERANCE));
  }

  @Test
  void theNextHitInTheCooldownIsAnOrdinaryHit() {
    learn("stealth", StealthAssassinate.class, 1);
    learn("unarmed", UnarmedIronFists.class, 7);
    strike(9D);

    assertThat(strike(1D).damage()).isGreaterThan(1D);
  }

  private void wield(String item, double attackDamage) {
    player.holding(FakeItem.tool(item, 100));
    player.setModifier(Attributes.ATTACK_DAMAGE, new AttributeMod(BASE_ATTACK_DAMAGE, attackDamage,
        AttributeOperation.ADD_VALUE));
  }

  private EntityDamageByEntityEvent strike(double incoming) {
    return adapt.post(new EntityDamageByEntityEvent(target(), player, player, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), incoming, false, FinalDamage.IDENTITY));
  }

  /** A zombie of 20 health a block ahead, looking away from the player. */
  private FakeLiving target() {
    FakeLiving zombie = new FakeLiving(Id.minecraft("zombie"), new Loc(platform.world(), 1.5D, 61D, 0.5D, -90F, 0F));
    zombie.trait(EntityTrait.MONSTER);
    zombie.setAttributeBase(Attributes.MAX_HEALTH, 20D);
    zombie.setHealth(20D);
    return zombie;
  }

  private void learnAll(String skillId) {
    Skill<?> skill = AdaptCore.get().getAdaptServer().getSkillRegistry().getSkill(skillId);
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      profile.getData().getSkillLine(skillId).setAdaptation(adaptation, adaptation.getMaxLevel());
    }
  }

  private <A extends Adaptation<?>> void learn(String skillId, Class<A> type, int level) {
    Skill<?> skill = AdaptCore.get().getAdaptServer().getSkillRegistry().getSkill(skillId);
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (type.isInstance(adaptation)) {
        profile.getData().getSkillLine(skillId).setAdaptation(adaptation, level);
        return;
      }
    }
    throw new AssertionError("No adaptation " + type.getSimpleName());
  }
}
