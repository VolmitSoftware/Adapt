package art.arcane.adapt.content.adaptation.blocking.blocks;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.content.skill.SkillBlocking;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.engine.mutation.engine.MutationEngine;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeProjectile;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** The Blocking blocks paired with Swords, and with Herbalism for the work Strike. */
class BlockingBlocksRuntimeTest {
  private static final Id WHEAT = Id.minecraft("wheat");
  private static final Id MAGIC = Id.minecraft("magic");
  private static final Id SHIELD = Id.minecraft("shield");
  private static final Id SLOWNESS = Id.minecraft("slowness");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private MutationKit kit;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Bulwark", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));
    player.inventory().setOffHand(FakeItem.tool("shield", 336));
    adapt = TestAdapt.start(platform, List.of(SkillBlocking.class, SkillSwords.class, SkillHerbalism.class));
    AdaptPlayer profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void graftBleedsTheAttackerOfABlockedHitWithBothCues() {
    kit.mutate("blocking", "swords");
    kit.learn("blocking", "swords", Recipe.GRAFT, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    struck(zombie);
    platform.scheduler().advance(30);
    assertThat(zombie.lastDamageType()).isNotEqualTo(MAGIC);

    player.setBlocking(true);
    struck(zombie);
    platform.scheduler().advance(30);
    assertThat(zombie.lastDamageType()).isEqualTo(MAGIC);
    assertThat(player.heardSounds()).containsSubsequence(MutationEngine.palette(Id.adapt("blocking")).fight(),
        MutationEngine.palette(Id.adapt("swords")).fight());
  }

  @Test
  void echoAsPartnerFiresFromABlockedHit() {
    kit.mutate("swords", "blocking");
    kit.learn("swords", "blocking", Recipe.ECHO, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    player.setBlocking(true);

    struck(zombie);
    platform.scheduler().advance(30);

    assertThat(zombie.lastDamageType()).isEqualTo(MAGIC);
  }

  @Test
  void aSwordCritStaggersTheTargetAndLocksAPlayersShield() {
    kit.mutate("swords", "blocking");
    kit.learn("swords", "blocking", Recipe.GRAFT, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(2);

    assertThat(zombie.effect(SLOWNESS)).isNotNull();
    assertThat(zombie.effect(SLOWNESS).durationTicks()).isEqualTo(40);
    assertThat(zombie.velocity().x()).isGreaterThan(0D);
  }

  @Test
  void aStaggeredPlayerCannotRaiseTheirShield() {
    kit.mutate("swords", "blocking");
    kit.learn("swords", "blocking", Recipe.GRAFT, 1);
    FakePlayer rival = platform.player("Rival", 2.5D, 61D, 0.5D);
    adapt.join(rival);

    kit.hit(player, rival, 4D, true);
    platform.scheduler().advance(2);

    assertThat(rival.cooldownTicks(SHIELD)).isEqualTo(40);
  }

  @Test
  void aHarvestHalvesTheNextHitWithinTenSeconds() {
    kit.mutate("herbalism", "blocking");
    kit.learn("herbalism", "blocking", Recipe.GRAFT, 1);

    adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2), true, 0));

    assertThat(hurt(6D).damage()).isEqualTo(3D);
    assertThat(hurt(6D).damage()).isEqualTo(6D);
  }

  @Test
  void barterSpendsSwordDurabilityForKnockbackAndProjectileResistance() {
    kit.mutate("blocking", "swords");
    kit.learn("blocking", "swords", Recipe.BARTER, 1);
    player.setSneaking(true);

    assertThat(swap().isCancelled()).isTrue();

    assertThat(player.attributeValue(Attributes.KNOCKBACK_RESISTANCE)).isCloseTo(1.3D, within(1.0E-9D));
    FakeProjectile arrow = new FakeProjectile(Id.minecraft("arrow"), new Loc(platform.world(), 3D, 61D, 0.5D));
    EntityDamageByEntityEvent shot = adapt.post(new EntityDamageByEntityEvent(player, arrow, arrow,
        DamageCause.PROJECTILE, Id.minecraft("arrow"), 10D, false, FinalDamage.IDENTITY));
    assertThat(shot.damage()).isCloseTo(7D, within(1.0E-9D));
  }

  @Test
  void theShieldPaysTenDurabilityAndRefusesWhenWorn() {
    kit.mutate("swords", "blocking");
    kit.learn("swords", "blocking", Recipe.BARTER, 1);
    player.setSneaking(true);
    player.inventory().setOffHand(FakeItem.tool("shield", 336).withDamage(330));

    assertThat(swap().isCancelled()).isFalse();
    assertThat(player.inventory().offHand().damage()).isEqualTo(330);

    player.inventory().setOffHand(FakeItem.tool("shield", 336));
    assertThat(swap().isCancelled()).isTrue();
    assertThat(player.inventory().offHand().damage()).isEqualTo(10);
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
  }

  @Test
  void attunementHoldsWhileTheShieldIsRaised() {
    kit.mutate("blocking", "swords");
    kit.learn("blocking", "swords", Recipe.ATTUNEMENT, 1);

    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isEqualTo(1D);

    player.setBlocking(true);
    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
  }

  @Test
  void convergenceAsksForABlockedHitInTheLastFourSeconds() {
    kit.mutate("swords", "blocking");
    kit.learn("swords", "blocking", Recipe.CONVERGENCE, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    player.setBlocking(true);

    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(2);
    assertThat(zombie.effect(SLOWNESS)).isNull();

    struck(zombie);
    player.setBlocking(false);
    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(2);
    assertThat(zombie.effect(SLOWNESS)).isNotNull();
  }

  @Test
  void nothingHappensWithoutTheMutation() {
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    player.setBlocking(true);

    struck(zombie);
    platform.scheduler().advance(30);

    assertThat(zombie.lastDamageType()).isNotEqualTo(MAGIC);
    assertThat(hurt(6D).damage()).isEqualTo(6D);
  }

  private void struck(FakeLiving attacker) {
    adapt.post(new EntityDamageByEntityEvent(player, attacker, attacker, DamageCause.ENTITY_ATTACK,
        Id.minecraft("mob_attack"), 4D, false, FinalDamage.IDENTITY, (double base) -> base));
  }

  private EntityDamageEvent hurt(double damage) {
    return adapt.post(new EntityDamageEvent(player, DamageCause.FALL, Id.minecraft("fall"), damage,
        FinalDamage.IDENTITY));
  }

  private PlayerSwapHandItemsEvent swap() {
    return adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(), player.inventory().offHand()));
  }

  private BlockRef crop(int x, int y, int z) {
    platform.world().set(new BlockPos(x, y, z), new FakeBlockState(WHEAT, Map.of("age", "7")));
    return platform.world().blockAt(x, y, z);
  }

  private void tickEngine() {
    adapt.core().getAdaptServer().getSkillRegistry().getMutationEngine().onTick();
    platform.scheduler().advance(1);
  }
}
