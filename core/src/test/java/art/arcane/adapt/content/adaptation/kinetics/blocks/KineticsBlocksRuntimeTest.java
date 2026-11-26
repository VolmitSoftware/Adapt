package art.arcane.adapt.content.adaptation.kinetics.blocks;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.blocks.BlockSlot;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.engine.MutationBlocks;
import art.arcane.adapt.api.mutation.sound.SoundPalettes;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.kinetics.KineticsJumpBoost;
import art.arcane.adapt.content.skill.SkillKinetics;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerJumpEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.Id;
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

/** Kinetics with Swords in both orderings: each of Kinetics' six blocks through the five mutated adaptations. */
class KineticsBlocksRuntimeTest {
  private static final Id MAGIC = Id.minecraft("magic");
  private static final Id SLOW_FALLING = Id.minecraft("slow_falling");

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
    player = platform.player("Meteor", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, SkillKinetics.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void graftAsLeadBleedsANearbyHostileOnlyAfterAFallOfFourBlocks() {
    kit.mutate("kinetics", "swords");
    kit.learn("kinetics", "swords", Recipe.GRAFT, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    land(3F);
    platform.scheduler().advance(40);
    assertThat(zombie.health()).isEqualTo(zombie.maxHealth());

    land(5F);
    platform.scheduler().advance(40);

    assertThat(zombie.lastDamageType()).isEqualTo(MAGIC);
    assertThat(player.heardSounds()).contains(SoundPalettes.of("kinetics").fight(), SoundPalettes.of("swords").fight());
  }

  @Test
  void echoAsPartnerCutsGrassWhenTheLandingIsFarFromHostiles() {
    kit.mutate("swords", "kinetics");
    kit.learn("swords", "kinetics", Recipe.ECHO, 1);
    platform.world().set(0, 61, 1, "short_grass");

    land(6F);

    assertThat(platform.world().blockAt(0, 61, 1).type().isAir()).isTrue();
  }

  @Test
  void graftAsPartnerLaunchesTheTargetOfASwordCrit() {
    kit.mutate("swords", "kinetics");
    kit.learn("swords", "kinetics", Recipe.GRAFT, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    kit.hit(player, zombie, 4D, true);

    assertThat(zombie.velocity().y()).isCloseTo(0.9D, within(1.0E-9D));
  }

  @Test
  void convergencePrimesTheNextJumpAndTheJumpSpendsIt() {
    kit.mutate("kinetics", "swords");
    kit.learn("kinetics", "swords", Recipe.CONVERGENCE, 1);
    double base = player.attributeValue(Attributes.JUMP_STRENGTH);
    FakeLiving far = MutationKit.zombie(platform.world(), 20.5D, 61D, 0.5D);

    land(6F);
    assertThat(player.attributeValue(Attributes.JUMP_STRENGTH)).isEqualTo(base);

    kit.hit(player, far, 1D, false);
    land(6F);
    assertThat(player.attributeValue(Attributes.JUMP_STRENGTH))
        .isCloseTo(base + KineticsJumpBoost.strengthBonusForHeight(3D), within(1.0E-9D));

    adapt.post(new PlayerJumpEvent(player, player.location()));
    assertThat(player.attributeValue(Attributes.JUMP_STRENGTH)).isEqualTo(base);
  }

  @Test
  void barterAsLeadSpendsSwordDurabilityForSlowFalling() {
    kit.mutate("kinetics", "swords");
    kit.learn("kinetics", "swords", Recipe.BARTER, 1);
    player.setSneaking(true);

    assertThat(barter()).isTrue();

    assertThat(player.inventory().mainHand().damage()).isEqualTo(5);
    assertThat(player.effect(SLOW_FALLING).durationTicks()).isEqualTo(60);
  }

  @Test
  void barterAsPartnerSpendsTheFallMidAirButNotOnTheGround() {
    kit.mutate("swords", "kinetics");
    kit.learn("swords", "kinetics", Recipe.BARTER, 1);
    player.setSneaking(true);
    player.setFallDistance(6F);
    player.setFoodLevel(1);

    assertThat(barter()).isFalse();

    player.setOnGround(false);
    assertThat(barter()).as("too hungry to pay").isFalse();

    player.setFoodLevel(20);
    assertThat(barter()).isTrue();
    assertThat(player.fallDistance()).isZero();
    assertThat(player.foodLevel()).isEqualTo(18);
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));

    assertThat(barter()).isFalse();
  }

  @Test
  void attunementAsLeadHoldsOnlyWhileAirborne() {
    kit.mutate("kinetics", "swords");
    kit.learn("kinetics", "swords", Recipe.ATTUNEMENT, 1);

    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1D, within(1.0E-9D));

    player.setOnGround(false);
    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
  }

  @Test
  void theRecentFormRemembersALandingForTheWindow() {
    kit.mutate("swords", "kinetics");
    kit.learn("swords", "kinetics", Recipe.CONVERGENCE, 1);
    KineticsState state = (KineticsState) MutationBlocks.find(kit.registry().getSkill("kinetics"), BlockSlot.STATE);
    assertThat(state.heldWithin(player, 4_000L)).isFalse();

    player.setOnGround(false);
    adapt.post(new PlayerMoveEvent(player, player.location(), player.location().add(0D, 0.4D, 0D)));
    platform.scheduler().advance(1);
    player.setOnGround(true);
    platform.scheduler().advance(2);

    assertThat(state.holds(player)).isFalse();
    assertThat(state.heldWithin(player, 4_000L)).isTrue();
    assertThat(state.heldWithin(player, -1L)).isFalse();
  }

  @Test
  void nothingHappensWithoutTheMutation() {
    platform.world().set(0, 61, 1, "short_grass");

    land(6F);

    assertThat(platform.world().blockAt(0, 61, 1).type().isAir()).isFalse();
    kit.mutate("kinetics", "swords");
    land(6F);
    assertThat(platform.world().blockAt(0, 61, 1).type().isAir()).isFalse();
  }

  private void land(float fallDistance) {
    player.setFallDistance(fallDistance);
    adapt.post(new EntityDamageEvent(player, DamageCause.FALL, Id.minecraft("fall"), Math.max(0D, fallDistance - 3D),
        base -> base));
    player.setFallDistance(0F);
  }

  /** Sneak and left-click the air, the Barter gesture of Swords pairs with this skill; true when it cast. */
  private boolean barter() {
    return adapt.post(new PlayerInteractEvent(player, InteractAction.LEFT_CLICK_AIR, Hand.MAIN_HAND,
        player.inventory().mainHand(), null, null, null, UseResult.DENY, UseResult.DEFAULT)).isUseDenied();
  }

  private void tickEngine() {
    adapt.core().getAdaptServer().getSkillRegistry().getMutationEngine().onTick();
    platform.scheduler().advance(1);
  }
}
