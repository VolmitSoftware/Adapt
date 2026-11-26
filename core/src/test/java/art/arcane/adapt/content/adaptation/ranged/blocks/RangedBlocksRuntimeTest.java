package art.arcane.adapt.content.adaptation.ranged.blocks;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillRanged;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.platform.entity.ArrowPickup;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityShootBowEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
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

/** Ranged paired with Swords (both orderings) and with Herbalism for the work Strike. */
class RangedBlocksRuntimeTest {
  private static final Id MAGIC = Id.minecraft("magic");
  private static final Id ARROW = Id.minecraft("arrow");
  private static final Id WHEAT = Id.minecraft("wheat");
  private static final Id SPECTRAL_ARROW = Id.minecraft("spectral_arrow");

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
    player = platform.player("Marksman", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));
    adapt = TestAdapt.start(platform, List.of(SkillRanged.class, SkillSwords.class, SkillHerbalism.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void graftBleedsATargetShotFromFarButNotANearOne() {
    kit.mutate("ranged", "swords");
    kit.learn("ranged", "swords", Recipe.GRAFT, 1);
    FakeLiving near = MutationKit.zombie(platform.world(), 6.5D, 61D, 0.5D);
    FakeLiving far = MutationKit.zombie(platform.world(), 20.5D, 61D, 0.5D);

    shoot(near, arrow(ARROW));
    platform.scheduler().advance(10);
    assertThat(near.lastDamageType()).isNotEqualTo(MAGIC);

    shoot(far, arrow(ARROW));
    platform.scheduler().advance(10);
    assertThat(far.lastDamageType()).isEqualTo(MAGIC);
  }

  @Test
  void swordsLeadFiresASpectralArrowOnACritAndBleedsAFarShotTargetButNotForItsOwnArrow() {
    kit.mutate("swords", "ranged");
    kit.learn("swords", "ranged", Recipe.GRAFT, 1);
    kit.learn("swords", "ranged", Recipe.ECHO, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 3.5D, 61D, 0.5D);

    kit.hit(player, zombie, 4D, true);

    assertThat(player.launched()).hasSize(1);
    FakeProjectile spectral = player.launched().get(0);
    assertThat(spectral.type()).isEqualTo(SPECTRAL_ARROW);
    assertThat(spectral.pickupRule()).isEqualTo(ArrowPickup.DISALLOWED);
    assertThat(spectral.velocity().x()).isGreaterThan(2.5D);
    assertThat(RangedFightStrike.fired(spectral)).isTrue();

    FakeLiving far = MutationKit.zombie(platform.world(), 20.5D, 61D, 0.5D);
    shoot(far, spectral);
    platform.scheduler().advance(10);
    assertThat(far.lastDamageType()).isNotEqualTo(MAGIC);

    shoot(far, arrow(ARROW));
    platform.scheduler().advance(10);
    assertThat(far.lastDamageType()).isEqualTo(MAGIC);
  }

  @Test
  void herbalismLeadPullsNearbyDropsIntoTheInventoryOnAHarvest() {
    kit.mutate("herbalism", "ranged");
    kit.learn("herbalism", "ranged", Recipe.GRAFT, 1);
    platform.world().dropItem(new Vec3(4.5D, 61D, 3.5D), new FakeItem(Id.minecraft("bone"), 3));
    platform.world().dropItem(new Vec3(30.5D, 61D, 0.5D), new FakeItem(Id.minecraft("feather"), 2));

    adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2, 7), true, 0));
    platform.scheduler().advance(20);

    assertThat(player.inventory().contains(Id.minecraft("bone"), 3)).isTrue();
    assertThat(player.inventory().contains(Id.minecraft("feather"), 1)).isFalse();
  }

  @Test
  void thePullCentresOnTheHarvestedCropNotOnThePlayer() {
    kit.mutate("herbalism", "ranged");
    kit.learn("herbalism", "ranged", Recipe.GRAFT, 1);
    platform.world().dropItem(new Vec3(16.5D, 61D, 15.5D), new FakeItem(Id.minecraft("bone"), 3));
    platform.world().dropItem(new Vec3(1.5D, 61D, 1.5D), new FakeItem(Id.minecraft("feather"), 2));

    adapt.post(new BlockBreakEvent(player, crop(14, 61, 14, 7), true, 0));
    platform.scheduler().advance(20);

    assertThat(player.inventory().contains(Id.minecraft("bone"), 3)).isTrue();
    assertThat(player.inventory().contains(Id.minecraft("feather"), 1)).isFalse();
  }

  @Test
  void barterGivesTheNextShotAFullDrawForSwordDurability() {
    kit.mutate("ranged", "swords");
    kit.learn("ranged", "swords", Recipe.BARTER, 1);
    player.setSneaking(true);

    assertThat(swap().isCancelled()).isTrue();
    assertThat(player.inventory().mainHand().damage()).isEqualTo(5);

    FakeProjectile half = arrow(ARROW);
    half.setVelocity(new Vec3(1D, 0D, 0D));
    adapt.post(new EntityShootBowEvent(player, FakeItem.of("bow"), new FakeItem(ARROW, 1), half, Hand.MAIN_HAND,
        0.5F, true));
    assertThat(half.velocity().x()).isCloseTo(2D, within(1.0E-9D));
    assertThat(half.isCritical()).isTrue();

    FakeProjectile next = arrow(ARROW);
    next.setVelocity(new Vec3(1D, 0D, 0D));
    adapt.post(new EntityShootBowEvent(player, FakeItem.of("bow"), new FakeItem(ARROW, 1), next, Hand.MAIN_HAND,
        0.5F, true));
    assertThat(next.velocity().x()).isCloseTo(1D, within(1.0E-9D));
  }

  @Test
  void barterRefusesWithoutAnArrowAndSpendsOneForAttackSpeed() {
    kit.mutate("swords", "ranged");
    kit.learn("swords", "ranged", Recipe.BARTER, 1);
    player.setSneaking(true);

    assertThat(swap().isCancelled()).isFalse();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isEqualTo(1D);

    player.inventory().addItem(new FakeItem(ARROW, 2));
    assertThat(swap().isCancelled()).isTrue();
    assertThat(player.inventory().contains(ARROW, 2)).isFalse();
    assertThat(player.inventory().contains(ARROW, 1)).isTrue();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
  }

  @Test
  void attunementGrantsAttackSpeedOnlyAtFullDraw() {
    kit.mutate("ranged", "swords");
    kit.learn("ranged", "swords", Recipe.ATTUNEMENT, 1);
    state().getConfig().fullDrawMillis = 0L;

    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isEqualTo(1D);

    FakeItem bow = FakeItem.of("bow");
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND, bow, null, null, null,
        UseResult.DEFAULT, UseResult.DEFAULT));
    player.setHandRaised(true);
    player.setActiveItem(bow);
    tickEngine();

    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
  }

  @Test
  void convergenceNeedsAFullyDrawnShotInTheLastFourSeconds() {
    kit.mutate("swords", "ranged");
    kit.learn("swords", "ranged", Recipe.CONVERGENCE, 1);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 3.5D, 61D, 0.5D);

    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(10);
    assertThat(zombie.lastDamageType()).isNotEqualTo(MAGIC);
    assertThat(player.launched()).isEmpty();

    adapt.post(new EntityShootBowEvent(player, FakeItem.of("bow"), new FakeItem(ARROW, 1), arrow(ARROW),
        Hand.MAIN_HAND, 1F, true));
    assertThat(state().holds(player)).isFalse();
    assertThat(state().heldWithin(player, 4_000L)).isTrue();
    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(10);

    assertThat(zombie.lastDamageType()).isEqualTo(MAGIC);
    assertThat(player.launched()).extracting(FakeProjectile::type).containsExactly(SPECTRAL_ARROW);
  }

  @Test
  void nothingHappensWithoutTheMutation() {
    FakeLiving far = MutationKit.zombie(platform.world(), 20.5D, 61D, 0.5D);

    shoot(far, arrow(ARROW));
    kit.hit(player, far, 4D, true);
    platform.scheduler().advance(10);

    assertThat(far.lastDamageType()).isNotEqualTo(MAGIC);
    assertThat(player.launched()).isEmpty();
  }

  private FakeProjectile arrow(Id type) {
    FakeProjectile arrow = new FakeProjectile(type, player.eyeLocation());
    arrow.setShooter(player);
    return arrow;
  }

  private void shoot(FakeLiving target, FakeProjectile arrow) {
    adapt.post(new EntityDamageByEntityEvent(target, arrow, player, DamageCause.PROJECTILE, Id.minecraft("arrow"),
        4D, false, FinalDamage.IDENTITY));
  }

  private PlayerSwapHandItemsEvent swap() {
    return adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(), player.inventory().offHand()));
  }

  private RangedState state() {
    for (MutationBlock<?> block : kit.registry().getSkill("ranged").getBlocks()) {
      if (block instanceof RangedState state) {
        return state;
      }
    }
    throw new AssertionError("No Ranged State");
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
