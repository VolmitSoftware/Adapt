package art.arcane.adapt.content.adaptation.agility.blocks;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.blocks.BlockSlot;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.engine.MutationBlocks;
import art.arcane.adapt.api.mutation.sound.SoundPalettes;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillAgility;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerJumpEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSprintEvent;
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

/** Agility with Swords in both orderings: each of Agility's six blocks through the five mutated adaptations. */
class AgilityBlocksRuntimeTest {
  private static final Id SPEED = Id.minecraft("speed");
  private static final Id JUMP_BOOST = Id.minecraft("jump_boost");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private MutationKit kit;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Runner", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, SkillAgility.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void graftAsLeadCutsGrassWhenTheRunnerSprintJumpsWithBothCues() {
    kit.mutate("agility", "swords");
    kit.learn("agility", "swords", Recipe.GRAFT, 1);
    platform.world().set(0, 61, 1, "short_grass");

    jump();
    assertThat(platform.world().blockAt(0, 61, 1).type().isAir()).isFalse();

    player.setSprinting(true);
    jump();
    platform.scheduler().advance(4);

    assertThat(platform.world().blockAt(0, 61, 1).type().isAir()).isTrue();
    assertThat(player.heardSounds()).contains(SoundPalettes.of("agility").work(), SoundPalettes.of("swords").work());
  }

  @Test
  void echoAsPartnerBleedsANearbyHostileOnASprintJump() {
    kit.mutate("swords", "agility");
    kit.learn("swords", "agility", Recipe.ECHO, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    player.setSprinting(true);

    jump();
    platform.scheduler().advance(40);

    assertThat(zombie.health()).isLessThan(zombie.maxHealth());
    assertThat(zombie.lastDamageType()).isEqualTo(Id.minecraft("magic"));
  }

  @Test
  void graftAsPartnerDashesThePlayerWhereTheyLookOnASwordCrit() {
    kit.mutate("swords", "agility");
    kit.learn("swords", "agility", Recipe.GRAFT, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 0.5D, 61D, 2.5D);

    kit.hit(player, zombie, 4D, true);

    Vec3 velocity = player.velocity();
    assertThat(Math.hypot(velocity.x(), velocity.z())).isCloseTo(0.3D, within(1.0E-6D));
    assertThat(velocity.y()).isPositive();
  }

  @Test
  void convergenceGivesSpeedTwoWhenNoHostileIsNearAndOnlyAfterASwordHit() {
    kit.mutate("agility", "swords");
    kit.learn("agility", "swords", Recipe.CONVERGENCE, 1);
    player.setSprinting(true);

    jump();
    assertThat(player.hasEffect(SPEED)).isFalse();

    FakeLiving far = MutationKit.zombie(platform.world(), 20.5D, 61D, 0.5D);
    kit.hit(player, far, 1D, false);
    jump();

    assertThat(player.effect(SPEED).amplifier()).isEqualTo(1);
    assertThat(player.effect(SPEED).durationTicks()).isEqualTo(80);
  }

  @Test
  void barterAsLeadSpendsSwordDurabilityForJumpBoostAndASafeLanding() {
    kit.mutate("agility", "swords");
    kit.learn("agility", "swords", Recipe.BARTER, 1);
    player.setSneaking(true);

    PlayerSwapHandItemsEvent swap = swap();

    assertThat(swap.isCancelled()).isTrue();
    assertThat(player.inventory().mainHand().damage()).isEqualTo(5);
    assertThat(player.hasEffect(JUMP_BOOST)).isTrue();
    player.setFallDistance(9F);
    EntityDamageEvent fall = adapt.post(new EntityDamageEvent(player, DamageCause.FALL, Id.minecraft("fall"), 6D,
        base -> base));
    assertThat(fall.isCancelled()).isTrue();
  }

  @Test
  void barterAsPartnerSpendsMomentumAndStopsThePlayerButNotWhenStandingStill() {
    kit.mutate("swords", "agility");
    kit.learn("swords", "agility", Recipe.BARTER, 1);
    player.setSneaking(true);

    assertThat(swap().isCancelled()).isFalse();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1D, within(1.0E-9D));

    player.setVelocity(new Vec3(0.2D, 0D, 0.1D));
    player.setFoodLevel(20);
    adapt.post(new PlayerMoveEvent(player, player.location(), player.location().add(0.1D, 0D, 0D)));
    PlayerSwapHandItemsEvent swap = swap();

    assertThat(swap.isCancelled()).isTrue();
    assertThat(player.foodLevel()).isEqualTo(18);
    assertThat(player.velocity()).isEqualTo(Vec3.ZERO);
    assertThat(player.isSprinting()).isFalse();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
  }

  @Test
  void onlyPlayersHoldingAMutationAreWalkedForAttunement() {
    assertThat(adapt.core().getAdaptServer().getOnlineMutationHolders()).isEmpty();

    kit.mutate("agility", "swords");
    kit.learn("agility", "swords", Recipe.ATTUNEMENT, 1);

    assertThat(adapt.core().getAdaptServer().getOnlineMutationHolders()).containsExactly(player.uuid());
  }

  @Test
  void attunementAsLeadHoldsOnlyWhileTheSprintKeepsGoing() {
    kit.mutate("agility", "swords");
    kit.learn("agility", "swords", Recipe.ATTUNEMENT, 1);
    state().getConfig().sprintMillis = 0L;

    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1D, within(1.0E-9D));

    player.setSprinting(true);
    adapt.post(new PlayerToggleSprintEvent(player, true));
    tickEngine();

    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
  }

  @Test
  void convergenceAsPartnerAsksForASprintThatEndedRecently() {
    kit.mutate("swords", "agility");
    kit.learn("swords", "agility", Recipe.CONVERGENCE, 1);
    state().getConfig().sprintMillis = 0L;
    FakeLiving zombie = MutationKit.zombie(platform.world(), 0.5D, 61D, 2.5D);

    kit.hit(player, zombie, 4D, true);
    assertThat(player.velocity()).isEqualTo(Vec3.ZERO);

    player.setSprinting(true);
    adapt.post(new PlayerToggleSprintEvent(player, true));
    player.setSprinting(false);
    adapt.post(new PlayerToggleSprintEvent(player, false));
    kit.hit(player, zombie, 4D, true);

    assertThat(Math.hypot(player.velocity().x(), player.velocity().z())).isPositive();
    platform.scheduler().advance(40);
    assertThat(zombie.lastDamageType()).isEqualTo(Id.minecraft("magic"));
  }

  @Test
  void nothingHappensWithoutTheMutation() {
    platform.world().set(0, 61, 1, "short_grass");
    player.setSprinting(true);

    jump();
    platform.scheduler().advance(4);

    assertThat(platform.world().blockAt(0, 61, 1).type().isAir()).isFalse();
    assertThat(player.hasEffect(SPEED)).isFalse();
    kit.mutate("agility", "swords");
    jump();
    assertThat(platform.world().blockAt(0, 61, 1).type().isAir()).isFalse();
  }

  private AgilityState state() {
    return (AgilityState) MutationBlocks.find(kit.registry().getSkill("agility"), BlockSlot.STATE);
  }

  private void jump() {
    adapt.post(new PlayerJumpEvent(player, player.location()));
  }

  private PlayerSwapHandItemsEvent swap() {
    return adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(), player.inventory().offHand()));
  }

  private void tickEngine() {
    adapt.core().getAdaptServer().getSkillRegistry().getMutationEngine().onTick();
    platform.scheduler().advance(1);
  }
}
