package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.stealth.upgraded.PhantomDecoy;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
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

/** Stealth leading Swords replaces Convergence with Shadow Riposte. */
class ShadowRiposteTest {
  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private AdaptPlayer profile;
  private MutationKit kit;
  private StealthSignatureMob attacker;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Shade", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillStealth.class, SkillSwords.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    StealthSignatureMob.floor(platform.world());
    attacker = StealthSignatureMob.at(platform.world(), 2.5D, 61D, 0.5D);
    attacker.setTarget(player);
    player.setSneaking(true);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aHitTakenWhileSneakingIsHalfParriedAndStepsYouBehindTheAttacker() {
    kit.mutate("stealth", "swords");
    kit.learn("stealth", "swords", Recipe.CONVERGENCE, 1);
    decoy(1);

    EntityDamageByEntityEvent hit = struck(10D);
    platform.scheduler().advance(2);

    assertThat(kit.adaptation("stealth", "swords", Recipe.CONVERGENCE).getSignature()).isInstanceOf(ShadowRiposte.class);
    assertThat(hit.damage()).isCloseTo(5D, within(1.0E-9D));
    assertThat(player.location().x()).isCloseTo(2.5D, within(1.0E-9D));
    assertThat(player.location().z()).isLessThan(attacker.location().z());
  }

  @Test
  void theTopLevelParriesMore() {
    kit.mutate("stealth", "swords");
    kit.learn("stealth", "swords", Recipe.CONVERGENCE, 3);
    decoy(1);

    assertThat(struck(10D).damage()).isCloseTo(3D, within(1.0E-9D));
  }

  @Test
  void swordsLeadingStealthAndAPlayerWithoutTheMutationTakeTheFullHit() {
    assertThat(struck(10D).damage()).isEqualTo(10D);
    kit.mutate("swords", "stealth");
    kit.learn("swords", "stealth", Recipe.CONVERGENCE, 3);

    assertThat(struck(10D).damage()).isEqualTo(10D);
    assertThat(player.location().x()).isCloseTo(0.5D, within(1.0E-9D));
  }

  @Test
  void itIsAShadowStepAndWaitsOutTheSharedStepCooldown() {
    kit.mutate("stealth", "swords");
    kit.learn("stealth", "swords", Recipe.CONVERGENCE, 1);
    decoy(1);

    struck(10D);
    platform.scheduler().advance(2);
    player.moveTo(new Loc(platform.world(), 0.5D, 61D, 0.5D));

    assertThat(struck(10D).damage()).isEqualTo(10D);
  }

  @Test
  void itNeedsToBeUnhuntedSneakingAndRoomBehindTheAttacker() {
    kit.mutate("stealth", "swords");
    kit.learn("stealth", "swords", Recipe.CONVERGENCE, 3);
    decoy(1);

    StealthSignatureMob second = StealthSignatureMob.at(platform.world(), -2.5D, 61D, 0.5D);
    second.setTarget(player);
    assertThat(struck(10D).damage()).as("hunted by another").isEqualTo(10D);
    second.setTarget(null);

    player.setSneaking(false);
    assertThat(struck(10D).damage()).as("standing").isEqualTo(10D);
    player.setSneaking(true);

    platform.world().set(2, 61, -1, "stone").set(2, 62, -1, "stone").set(2, 62, 0, "stone");
    assertThat(struck(10D).damage()).as("wall").isEqualTo(10D);
    assertThat(player.location().x()).isCloseTo(0.5D, within(1.0E-9D));
  }

  @Test
  void withoutPhantomDecoyThereIsNoRiposte() {
    kit.mutate("stealth", "swords");
    kit.learn("stealth", "swords", Recipe.CONVERGENCE, 3);

    EntityDamageByEntityEvent hit = struck(10D);
    platform.scheduler().advance(2);

    assertThat(hit.damage()).isEqualTo(10D);
    assertThat(player.location().x()).isCloseTo(0.5D, within(1.0E-9D));
  }

  @Test
  void theStepUsesThePhantomDecoyLevelNotTheSignatureLevel() {
    kit.mutate("stealth", "swords");
    kit.learn("stealth", "swords", Recipe.CONVERGENCE, 3);
    PhantomDecoy decoy = decoy(1);
    attacker.moveTo(new Loc(platform.world(), decoy.getStepRange(1) + 1.5D, 61D, 0.5D));

    EntityDamageByEntityEvent hit = struck(10D);
    platform.scheduler().advance(2);

    assertThat(decoy.getStepRange(3)).isGreaterThan(decoy.getStepRange(1) + 1D);
    assertThat(hit.damage()).isEqualTo(10D);
    assertThat(player.location().x()).isCloseTo(0.5D, within(1.0E-9D));
  }

  @Test
  void aHitALaterHandlerCancelsNeitherStepsNorSpendsTheCooldown() {
    kit.mutate("stealth", "swords");
    kit.learn("stealth", "swords", Recipe.CONVERGENCE, 1);
    decoy(1);
    Object guard = new Object();
    adapt.bus().register(guard, EntityDamageByEntityEvent.class, EventPriority.HIGHEST,
        (EntityDamageByEntityEvent e) -> e.setCancelled(true));

    struck(10D);
    platform.scheduler().advance(2);
    assertThat(player.location().x()).isCloseTo(0.5D, within(1.0E-9D));

    adapt.bus().unregisterAll(guard);
    EntityDamageByEntityEvent hit = struck(10D);
    platform.scheduler().advance(2);

    assertThat(hit.damage()).isCloseTo(5D, within(1.0E-9D));
    assertThat(player.location().x()).isCloseTo(2.5D, within(1.0E-9D));
  }

  private PhantomDecoy decoy(int level) {
    PhantomDecoy decoy = SkillHost.of(kit.registry().getSkill("stealth")).getUpgrades().stream()
        .filter(PhantomDecoy.class::isInstance).map(PhantomDecoy.class::cast).findFirst().orElseThrow();
    profile.getData().getSkillLine("stealth").setAdaptation(decoy, level);
    return decoy;
  }

  private EntityDamageByEntityEvent struck(double damage) {
    return adapt.post(new EntityDamageByEntityEvent(player, attacker, attacker, DamageCause.ENTITY_ATTACK,
        Id.minecraft("mob_attack"), damage, false, FinalDamage.IDENTITY));
  }
}
