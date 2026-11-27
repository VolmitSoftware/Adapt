package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.content.adaptation.kinetics.upgraded.Comet;
import art.arcane.adapt.content.skill.SkillAgility;
import art.arcane.adapt.content.skill.SkillKinetics;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityAttemptSmashAttackEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.PlayerToggleSprintEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
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

/** Agility and Kinetics replace Convergence with Momentum Bank: run speed carried into Comet's smash. */
class MomentumBankTest {
  private static final Id SLIPSTREAM = Id.adapt("test-slipstream");
  private static final double COMET_WAVE = 4D;

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private MutationKit kit;
  private AdaptPlayer profile;
  private FakeLiving target;
  private FakeLiving bystander;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Comet", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("mace", 500));
    player.setAttributeBase(Attributes.MOVEMENT_SPEED, 0.1D);
    adapt = TestAdapt.start(platform, List.of(SkillAgility.class, SkillKinetics.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    target = MutationKit.zombie(platform.world(), 1.5D, 61D, 0.5D);
    bystander = MutationKit.zombie(platform.world(), 0.5D, 61D, 3D);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void bankedRunSpeedGrowsCometsShockwaveUpToHalfAgain() {
    kit.mutate("agility", "kinetics");
    kit.learn("agility", "kinetics", Recipe.CONVERGENCE, 3);
    assertThat(kit.adaptation("agility", "kinetics", Recipe.CONVERGENCE).getSignature()).isInstanceOf(MomentumBank.class);
    learnComet();

    run(0.8D);
    smash();

    assertThat(bystander.damageTaken()).isCloseTo(COMET_WAVE * 1.5D, within(1.0E-9D));
    assertThat(player.heardSounds()).isNotEmpty();
  }

  @Test
  void aSmallerBankAddsLess() {
    kit.mutate("agility", "kinetics");
    kit.learn("agility", "kinetics", Recipe.CONVERGENCE, 1);
    learnComet();

    run(0.2D);
    smash();

    assertThat(bystander.damageTaken()).isCloseTo(COMET_WAVE * 1.2D, within(1.0E-9D));
  }

  @Test
  void theFirstLevelCapsTheBonusLower() {
    kit.mutate("agility", "kinetics");
    kit.learn("agility", "kinetics", Recipe.CONVERGENCE, 1);
    learnComet();

    run(0.8D);
    smash();

    assertThat(bystander.damageTaken()).isCloseTo(COMET_WAVE * 1.3D, within(1.0E-9D));
  }

  @Test
  void withoutCometTheSmashReleasesNoWaveButTheSpeedCarriesOn() {
    kit.mutate("agility", "kinetics");
    kit.learn("agility", "kinetics", Recipe.CONVERGENCE, 1);

    run(0.4D);
    smash();

    assertThat(bystander.damageTaken()).isZero();
    assertThat(player.attributeValue(Attributes.MOVEMENT_SPEED)).isCloseTo(0.14D, within(1.0E-9D));
  }

  @Test
  void theCarriedSpeedIsNotBankedAgain() {
    kit.mutate("agility", "kinetics");
    kit.learn("agility", "kinetics", Recipe.CONVERGENCE, 1);
    MomentumBank bank = (MomentumBank) kit.adaptation("agility", "kinetics", Recipe.CONVERGENCE).getSignature();
    bank.getConfig().cooldownMillisAtFirstLevel = 0;
    run(0.4D);
    smash();
    assertThat(player.attributeValue(Attributes.MOVEMENT_SPEED)).isCloseTo(0.14D, within(1.0E-9D));

    run(0.2D);
    smash();

    assertThat(player.attributeValue(Attributes.MOVEMENT_SPEED)).isCloseTo(0.12D, within(1.0E-9D));
  }

  @Test
  void eitherSkillMayLead() {
    kit.mutate("kinetics", "agility");
    kit.learn("kinetics", "agility", Recipe.CONVERGENCE, 1);
    learnComet();

    run(0.2D);
    smash();

    assertThat(bystander.damageTaken()).isCloseTo(COMET_WAVE * 1.2D, within(1.0E-9D));
  }

  @Test
  void plainSprintingBanksNothing() {
    kit.mutate("agility", "kinetics");
    kit.learn("agility", "kinetics", Recipe.CONVERGENCE, 3);
    learnComet();
    player.setSprinting(true);
    player.setModifier(Attributes.MOVEMENT_SPEED, new AttributeMod(SLIPSTREAM, 0.3D, AttributeOperation.ADD_MULTIPLIED_TOTAL));

    adapt.post(new PlayerToggleSprintEvent(player, false));
    smash();

    assertThat(bystander.damageTaken()).isCloseTo(COMET_WAVE, within(1.0E-9D));
  }

  @Test
  void theNextSmashWaitsForTheCooldown() {
    kit.mutate("agility", "kinetics");
    kit.learn("agility", "kinetics", Recipe.CONVERGENCE, 1);
    run(0.4D);
    smash();
    platform.scheduler().advance(200);

    run(0.4D);
    smash();

    assertThat(player.attributeValue(Attributes.MOVEMENT_SPEED)).isCloseTo(0.1D, within(1.0E-9D));
  }

  @Test
  void nothingIsBankedWithoutTheLearnedAdaptation() {
    run(0.8D);
    smash();
    kit.mutate("agility", "kinetics");
    run(0.8D);
    smash();

    assertThat(player.attributeValue(Attributes.MOVEMENT_SPEED)).isCloseTo(0.1D, within(1.0E-9D));
  }

  @Test
  void theLandingAfterABankedSmashDoesNotSetOffTheKineticsTrigger() {
    kit.mutate("agility", "kinetics");
    kit.learn("agility", "kinetics", Recipe.ECHO, 1);
    land();
    assertThat(player.velocity().lengthSquared()).isPositive();
    player.setVelocity(Vec3.ZERO);
    kit.learn("agility", "kinetics", Recipe.CONVERGENCE, 1);
    platform.scheduler().advance(200);

    run(0.4D);
    smash();
    land();

    assertThat(player.velocity().lengthSquared()).isZero();
  }

  private void learnComet() {
    Comet comet = (Comet) adapt.core().getAdaptServer().getSkillRegistry().getAdaptation(Id.adapt("kinetics/upgrade/comet"));
    profile.getData().getSkillLine("kinetics").setAdaptation(comet, 1);
  }

  private void land() {
    player.setFallDistance(6F);
    adapt.post(new EntityDamageEvent(player, DamageCause.FALL, Id.minecraft("fall"), 3D, FinalDamage.IDENTITY));
    platform.scheduler().advance(2);
  }

  private void run(double bonus) {
    player.setSprinting(false);
    player.setModifier(Attributes.MOVEMENT_SPEED, new AttributeMod(SLIPSTREAM, bonus, AttributeOperation.ADD_MULTIPLIED_TOTAL));
    adapt.post(new PlayerToggleSprintEvent(player, false));
    player.removeModifier(Attributes.MOVEMENT_SPEED, SLIPSTREAM);
  }

  private void smash() {
    player.setFallDistance(6F);
    adapt.post(new EntityAttemptSmashAttackEvent(player, target, player.inventory().mainHand(), true, UseResult.DEFAULT));
    platform.scheduler().advance(2);
  }
}
