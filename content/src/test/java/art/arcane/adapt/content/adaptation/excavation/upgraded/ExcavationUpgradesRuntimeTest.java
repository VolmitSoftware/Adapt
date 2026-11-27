package art.arcane.adapt.content.adaptation.excavation.upgraded;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.content.adaptation.excavation.ExcavationEarthMover;
import art.arcane.adapt.content.skill.SkillExcavation;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.MobOrigin;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.engine.xp.XpProvenance;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDamageEvent;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.testkit.XpPaid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

class ExcavationUpgradesRuntimeTest {
  @TempDir
  Path data;

  private TestPlatform platform;
  private FakeWorld world;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private SkillRegistry registry;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    world = platform.world();
    player = platform.player("Dredger", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_shovel", 250));
    adapt = TestAdapt.start(platform, List.of(SkillExcavation.class, SkillStealth.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void upgradesDoNothingForAPlayerWhoHasNotMutated() {
    Gravekeeper gravekeeper = learn(Gravekeeper.class, 3);
    certainFinds(gravekeeper);
    Landshaper landshaper = learn(Landshaper.class, 3);
    world.set(0, 60, 2, "sand").set(1, 60, 2, "sand").set(-1, 60, 2, "sand");

    assertThat(landshaper.getLevel(player)).isZero();
    dig(world.blockAt(0, 60, 2));
    platform.scheduler().advance(5);

    assertThat(world.items()).isEmpty();
    assertThat(world.blockAt(1, 60, 2).type()).isEqualTo(Id.minecraft("sand"));
  }

  @Test
  void landshaperCarvesThePlaneAroundADig() {
    mutate();
    learn(Landshaper.class, 3);
    for (int x = -1; x <= 1; x++) {
      for (int y = 59; y <= 61; y++) {
        world.set(x, y, 2, "dirt");
      }
    }

    dig(world.blockAt(0, 60, 2));
    platform.scheduler().advance(2);

    assertThat(world.blockAt(1, 60, 2).isAir()).isTrue();
    assertThat(world.blockAt(-1, 59, 2).isAir()).isTrue();
    assertThat(profile.getData().getStat("excavation.landshaper.blocks-carved")).isPositive();
  }

  @Test
  void landshaperPaysOnlyForNaturalSoil() {
    mutate();
    Landshaper landshaper = learn(Landshaper.class, 3);
    for (int x = -1; x <= 1; x++) {
      for (int y = 59; y <= 61; y++) {
        world.set(x, y, 2, "dirt");
      }
    }

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(any(BlockRef.class))).thenReturn(true);
      dig(world.blockAt(0, 60, 2));
      platform.scheduler().advance(2);
    }
    assertThat(world.blockAt(1, 60, 2).isAir()).isTrue();
    assertThat(XpPaid.by(profile, landshaper)).isZero();

    for (int x = -1; x <= 1; x++) {
      for (int y = 59; y <= 61; y++) {
        world.set(x, y, 5, "dirt");
      }
    }
    dig(world.blockAt(0, 60, 5));
    platform.scheduler().advance(2);
    assertThat(XpPaid.by(profile, landshaper)).isPositive();
  }

  @Test
  void landshaperSinksAShaftWhileSneaking() {
    mutate();
    Landshaper landshaper = learn(Landshaper.class, 1);
    landshaper.getConfig().durabilityCostPerBlock = 0;
    for (int y = 50; y <= 60; y++) {
      world.set(0, y, 2, "dirt");
    }
    player.setSneaking(true);

    dig(world.blockAt(0, 60, 2));
    platform.scheduler().advance(40);

    assertThat(world.blockAt(0, 59, 2).isAir()).isTrue();
    assertThat(world.blockAt(0, 52, 2).isAir()).isTrue();
    assertThat(world.blockAt(0, 51, 2).type()).isEqualTo(Id.minecraft("dirt"));
  }

  @Test
  void landshaperGivesHasteWhileDigging() {
    mutate();
    learn(Landshaper.class, 2);
    BlockRef dirt = world.set(0, 60, 2, "dirt").blockAt(0, 60, 2);
    double before = player.attributeValue(Attributes.BLOCK_BREAK_SPEED);

    adapt.post(new BlockDamageEvent(player, dirt, player.inventory().mainHand(), false));
    adapt.awaitTicks(() -> player.attributeValue(Attributes.BLOCK_BREAK_SPEED) > before, 2_000L);

    assertThat(player.attributeValue(Attributes.BLOCK_BREAK_SPEED)).isGreaterThan(before);
  }

  @Test
  void deepSightRevealsOresAroundADigWithoutGlowBerries() {
    mutate();
    learn(DeepSight.class, 1);
    world.set(3, 58, 2, "diamond_ore").set(-2, 61, 4, "iron_ore");
    BlockRef dirt = world.set(0, 60, 2, "dirt").blockAt(0, 60, 2);

    adapt.post(new BlockBreakEvent(player, dirt, true, 0));
    adapt.awaitTicks(() -> profile.getData().getStat("excavation.deep-sight.ores-revealed") >= 2D, 5_000L);

    assertThat(profile.getData().getStat("excavation.deep-sight.ores-revealed")).isEqualTo(2D);
  }

  @Test
  void gravekeeperTurnsUpTreasureBonesAndBonusDrops() {
    mutate();
    certainFinds(learn(Gravekeeper.class, 1));

    dig(world.set(0, 60, 2, "sand").blockAt(0, 60, 2));
    dig(world.set(0, 60, 3, "dirt").blockAt(0, 60, 3));
    dig(world.set(0, 60, 4, "soul_sand").blockAt(0, 60, 4));

    assertThat(world.items()).hasSize(3);
    assertThat(world.items().get(2).item().type()).isEqualTo(Id.minecraft("soul_sand"));
    assertThat(profile.getData().getStat("excavation.gravekeeper.finds")).isEqualTo(3D);
  }

  @Test
  void gravekeeperFindsNothingInSoilThePlayerPlaced() {
    mutate();
    certainFinds(learn(Gravekeeper.class, 1));
    BlockRef soulSand = world.set(0, 60, 4, "soul_sand").blockAt(0, 60, 4);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(any(BlockRef.class))).thenReturn(true);
      dig(soulSand);
    }

    assertThat(world.items()).isEmpty();
  }

  @Test
  void landshaperSlamThrowsMobsAheadWithoutChangingTheGround() {
    mutate();
    Landshaper landshaper = learn(Landshaper.class, 1);
    FakeItem shovel = FakeItem.tool("iron_shovel", 250);
    player.holding(shovel);
    BlockRef ground = world.set(0, 60, 1, "grass_block").blockAt(0, 60, 1);
    FakeLiving ahead = MutationKit.zombie(world, 0.5D, 61D, 3.5D);
    FakeLiving behind = MutationKit.zombie(world, 0.5D, 61D, -3.5D);

    PlayerInteractEvent slam = slam(ground);

    assertThat(slam.isCancelled()).isTrue();
    assertThat(ahead.damageTaken()).isEqualTo(landshaper.getSlamDamage(1));
    assertThat(ahead.velocity().y()).isPositive();
    assertThat(ahead.effect(Id.minecraft("slowness"))).isNotNull();
    assertThat(behind.damageTaken()).isZero();
    assertThat(shovel.damage()).isEqualTo(landshaper.getConfig().slamDurability);
    assertThat(ground.type()).isEqualTo(Id.minecraft("grass_block"));
    assertThat(XpPaid.by(profile, landshaper)).isPositive();
  }

  @Test
  void landshaperSlamWaitsForItsCooldown() {
    mutate();
    learn(Landshaper.class, 3);
    BlockRef ground = world.set(0, 60, 1, "dirt").blockAt(0, 60, 1);
    FakeLiving ahead = MutationKit.zombie(world, 0.5D, 61D, 3.5D);

    slam(ground);
    double first = ahead.damageTaken();
    ahead.setNoDamageTicks(0);
    slam(ground);

    assertThat(first).isPositive();
    assertThat(profile.getData().getStat("excavation.landshaper.slams")).isEqualTo(1D);
  }

  @Test
  void landshaperSlamPaysNothingForSpawnerMobs() {
    mutate();
    Landshaper landshaper = learn(Landshaper.class, 2);
    BlockRef ground = world.set(0, 60, 1, "dirt").blockAt(0, 60, 1);
    FakeLiving farmed = MutationKit.zombie(world, 0.5D, 61D, 3.5D);
    MobOrigin.record(farmed, CreatureSpawnEvent.Reason.SPAWNER);

    slam(ground);

    assertThat(farmed.damageTaken()).isPositive();
    assertThat(XpPaid.by(profile, landshaper)).isZero();
  }

  @Test
  void landshaperSlamDoesNothingWithoutTheMutation() {
    learn(Landshaper.class, 3);
    BlockRef ground = world.set(0, 60, 1, "dirt").blockAt(0, 60, 1);
    FakeLiving ahead = MutationKit.zombie(world, 0.5D, 61D, 3.5D);

    PlayerInteractEvent slam = slam(ground);

    assertThat(slam.isCancelled()).isFalse();
    assertThat(ahead.damageTaken()).isZero();
  }

  @Test
  void landshaperSlamAnswersOnlyTheTopOfABlock() {
    mutate();
    learn(Landshaper.class, 1);
    FakeItem shovel = FakeItem.tool("iron_shovel", 250);
    player.holding(shovel);
    BlockRef campfire = world.set(0, 61, 1, "campfire").blockAt(0, 61, 1);

    PlayerInteractEvent side = slam(campfire, BlockFace.NORTH);

    assertThat(side.isCancelled()).isFalse();
    assertThat(profile.getData().getStat("excavation.landshaper.slams")).isZero();
    assertThat(shovel.damage()).isZero();
  }

  @Test
  void earthMoverAnswersTheGroundWhileTheSlamCoolsDown() {
    mutate();
    learn(Landshaper.class, 1);
    ExcavationEarthMover earthMover = registry.getSkill("excavation").getAdaptations().stream()
        .filter(ExcavationEarthMover.class::isInstance).map(ExcavationEarthMover.class::cast).findFirst().orElseThrow();
    profile.getData().getSkillLine("excavation").setAdaptation(earthMover, 1);
    player.setFoodLevel(20);
    BlockRef ground = world.set(0, 60, 1, "dirt").blockAt(0, 60, 1);

    slam(ground);
    double afterSlam = profile.getData().getStat("excavation.earth-mover.waves-unleashed");
    slam(ground);

    assertThat(afterSlam).isZero();
    assertThat(profile.getData().getStat("excavation.earth-mover.waves-unleashed")).isEqualTo(1D);
  }

  private void certainFinds(Gravekeeper gravekeeper) {
    gravekeeper.getConfig().treasureChanceBase = 1D;
    gravekeeper.getConfig().boneChanceBase = 1D;
    gravekeeper.getConfig().bonusChanceBase = 1D;
  }

  private PlayerInteractEvent slam(BlockRef ground) {
    return slam(ground, BlockFace.UP);
  }

  private PlayerInteractEvent slam(BlockRef ground, BlockFace face) {
    player.setSneaking(true);
    return adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND,
        player.inventory().mainHand(), ground, face, null, UseResult.DEFAULT, UseResult.DEFAULT));
  }

  private void dig(BlockRef block) {
    adapt.post(new BlockBreakEvent(player, block, true, 0));
    world.set(block.x(), block.y(), block.z(), "air");
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("excavation", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("excavation"), registry.getSkill("stealth"),
        "Dredger")).isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = SkillHost.of(registry.getSkill("excavation")).getUpgrades().stream().filter(type::isInstance).map(type::cast)
        .findFirst().orElseThrow();
    profile.getData().getSkillLine("excavation").setAdaptation(upgrade, level);
    return upgrade;
  }
}
