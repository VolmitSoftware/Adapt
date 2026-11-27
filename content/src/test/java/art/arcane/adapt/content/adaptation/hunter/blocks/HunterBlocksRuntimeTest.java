package art.arcane.adapt.content.adaptation.hunter.blocks;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.content.adaptation.GlowLayers;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillHunter;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.engine.fx.ViewerGlowPort;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.viewer.GlowColor;
import art.arcane.adapt.platform.viewer.ViewerHost;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBlockState;
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
import org.mockito.InOrder;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Hunter paired with Swords (both orderings) and with Herbalism for the work Strike. */
class HunterBlocksRuntimeTest {
  private static final Id MAGIC = Id.minecraft("magic");
  private static final Id WHEAT = Id.minecraft("wheat");
  private static final Id SPEED = Id.minecraft("speed");

  @TempDir
  Path data;

  private TestPlatform platform;
  private ViewerHost viewers;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private MutationKit kit;

  @BeforeEach
  void start() {
    viewers = mock(ViewerHost.class);
    platform = TestPlatform.builder(data).viewers(viewers).build();
    player = platform.player("Predator", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));
    adapt = TestAdapt.start(platform, List.of(SkillHunter.class, SkillSwords.class, SkillHerbalism.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    ViewerGlowPort.current().discardViewer(player.uuid());
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void graftBleedsTheNearestOtherHostileWhenAMobIsKilled() {
    kit.mutate("hunter", "swords");
    kit.learn("hunter", "swords", Recipe.GRAFT, 1);
    FakeLiving victim = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    FakeLiving next = MutationKit.zombie(platform.world(), 3.5D, 61D, 2.5D);

    kill(victim);
    platform.scheduler().advance(10);

    assertThat(next.lastDamageType()).isEqualTo(MAGIC);
  }

  @Test
  void swordsLeadMarksACritTargetForExtraDamageAndBleedsTheNextHostileOnAKill() {
    kit.mutate("swords", "hunter");
    kit.learn("swords", "hunter", Recipe.GRAFT, 1);
    kit.learn("swords", "hunter", Recipe.ECHO, 1);
    FakeLiving marked = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    assertThat(kit.hit(player, marked, 4D, false).damage()).isEqualTo(4D);
    kit.hit(player, marked, 4D, true);
    assertThat(kit.hit(player, marked, 4D, false).damage()).isCloseTo(4.6D, within(1.0E-9D));
    FakeLiving other = MutationKit.zombie(platform.world(), 12.5D, 61D, 0.5D);
    assertThat(adapt.post(new EntityDamageByEntityEvent(marked, other, other, DamageCause.ENTITY_ATTACK,
        Id.minecraft("mob_attack"), 4D, false, FinalDamage.IDENTITY)).damage())
        .isEqualTo(4D);

    FakeLiving next = MutationKit.zombie(platform.world(), 0.5D, 61D, 4.5D);
    kill(marked);
    platform.scheduler().advance(10);
    assertThat(next.lastDamageType()).isEqualTo(MAGIC);
  }

  @Test
  void herbalismLeadOutlinesNearbyAnimalsAndMobsForThePlayerAfterAHarvest() {
    kit.mutate("herbalism", "hunter");
    kit.learn("herbalism", "hunter", Recipe.GRAFT, 1);
    FakeLiving cow = new FakeLiving(Id.minecraft("cow"), new Loc(platform.world(), 6.5D, 61D, 0.5D));
    cow.trait(EntityTrait.ANIMAL);
    platform.world().add(cow);
    FakeLiving far = MutationKit.zombie(platform.world(), 30.5D, 61D, 0.5D);

    adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2, 7), true, 0));

    verify(viewers).setGlow(player, cow, GlowColor.RED);
    verify(viewers, never()).setGlow(eq(player), eq(far), any(GlowColor.class));
    platform.scheduler().advance(101);
    verify(viewers).clearGlow(player, cow.entityId());
  }

  @Test
  void theOutlineSitsAboveAGolemancyGlowAndHandsItBackWhenItFades() {
    FakeLiving cow = outlinedCow();
    ViewerGlowPort.current().set(GlowLayers.GOLEMANCY, cow, player, GlowColor.WHITE);

    adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2, 7), true, 0));
    platform.scheduler().advance(101);

    InOrder order = inOrder(viewers);
    order.verify(viewers).setGlow(player, cow, GlowColor.WHITE);
    order.verify(viewers).setGlow(player, cow, GlowColor.RED);
    order.verify(viewers).setGlow(player, cow, GlowColor.WHITE);
    verify(viewers, never()).clearGlow(player, cow.entityId());
  }

  @Test
  void aGolemancyGlowEndingMidOutlineLeavesTheOutline() {
    FakeLiving cow = outlinedCow();
    ViewerGlowPort.current().set(GlowLayers.GOLEMANCY, cow, player, GlowColor.WHITE);
    adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2, 7), true, 0));

    ViewerGlowPort.current().unset(GlowLayers.GOLEMANCY, cow, player);
    verify(viewers, never()).clearGlow(player, cow.entityId());

    platform.scheduler().advance(101);
    verify(viewers).clearGlow(player, cow.entityId());
  }

  private FakeLiving outlinedCow() {
    kit.mutate("herbalism", "hunter");
    kit.learn("herbalism", "hunter", Recipe.GRAFT, 1);
    FakeLiving cow = new FakeLiving(Id.minecraft("cow"), new Loc(platform.world(), 6.5D, 61D, 0.5D));
    cow.trait(EntityTrait.ANIMAL);
    platform.world().add(cow);
    return cow;
  }

  @Test
  void barterTurnsSwordDurabilityIntoSpeed() {
    kit.mutate("hunter", "swords");
    kit.learn("hunter", "swords", Recipe.BARTER, 1);
    player.setSneaking(true);

    assertThat(swap().isCancelled()).isTrue();

    EffectInstance speed = player.effect(SPEED);
    assertThat(speed.durationTicks()).isEqualTo(100);
    assertThat(speed.amplifier()).isZero();
    assertThat(player.inventory().mainHand().damage()).isEqualTo(5);
  }

  @Test
  void barterRefusesWhenHungryAndOtherwiseSpendsTwoHunger() {
    kit.mutate("swords", "hunter");
    kit.learn("swords", "hunter", Recipe.BARTER, 1);
    player.setSneaking(true);
    player.setFoodLevel(1);

    assertThat(swap().isCancelled()).isFalse();
    assertThat(player.foodLevel()).isEqualTo(1);

    player.setFoodLevel(10);
    assertThat(swap().isCancelled()).isTrue();
    assertThat(player.foodLevel()).isEqualTo(8);
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
  }

  @Test
  void attunementHoldsBelowHalfHealth() {
    kit.mutate("hunter", "swords");
    kit.learn("hunter", "swords", Recipe.ATTUNEMENT, 1);

    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isEqualTo(1D);

    player.setHealth(8D);
    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
  }

  @Test
  void nothingHappensWithoutTheMutation() {
    FakeLiving victim = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    FakeLiving next = MutationKit.zombie(platform.world(), 3.5D, 61D, 2.5D);

    kill(victim);
    platform.scheduler().advance(10);

    assertThat(next.lastDamageType()).isNotEqualTo(MAGIC);
  }

  private void kill(FakeLiving victim) {
    victim.setDead(true);
    adapt.post(new EntityDeathEvent(victim, player, player, player, DamageCause.ENTITY_ATTACK, List.of(), 0));
  }

  private PlayerSwapHandItemsEvent swap() {
    return adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(), player.inventory().offHand()));
  }

  private BlockRef crop(int x, int y, int z, int age) {
    platform.world().set(new BlockPos(x, y, z), new FakeBlockState(WHEAT, Map.of("age", Integer.toString(age))));
    return platform.world().blockAt(x, y, z);
  }

  private void tickEngine() {
    adapt.core().getAdaptServer().getSkillRegistry().getMutationEngine().onTick();
    platform.scheduler().advance(1);
  }
}
