package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.BonusBlockBreaks;
import art.arcane.adapt.api.protection.ProtectionCheck;
import art.arcane.adapt.api.protection.ProtectorPort;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.axe.AxeThrow;
import art.arcane.adapt.content.skill.SkillExcavation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.world.ChunkEntitiesEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.AbilityHitLiving;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.testkit.XpPaid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

class ExcavationClodHurlerTest {
  private static final Id DIRT = Id.minecraft("dirt");
  private static final Id SNOWBALL = Id.minecraft("snowball");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakeWorld world;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private ExcavationClodHurler hurler;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    world = platform.world();
    player = platform.player("Digger", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_shovel", 250));
    adapt = TestAdapt.start(platform, List.of(SkillExcavation.class));
    profile = adapt.join(player);
    hurler = adaptation();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void shovelDamageFollowsTierAndSharpness() {
    assertThat(ExcavationClodHurler.shovelDamage(Id.minecraft("wooden_shovel"), 0)).isEqualTo(2.5D);
    assertThat(ExcavationClodHurler.shovelDamage(Id.minecraft("stone_shovel"), 0)).isEqualTo(3.5D);
    assertThat(ExcavationClodHurler.shovelDamage(Id.minecraft("iron_shovel"), 0)).isEqualTo(4.5D);
    assertThat(ExcavationClodHurler.shovelDamage(Id.minecraft("diamond_shovel"), 0)).isEqualTo(5.5D);
    assertThat(ExcavationClodHurler.shovelDamage(Id.minecraft("netherite_shovel"), 0)).isEqualTo(6.5D);
    assertThat(ExcavationClodHurler.shovelDamage(Id.minecraft("netherite_shovel"), 5)).isEqualTo(9.5D);
  }

  @Test
  void heavierSoftBlocksHitHarder() {
    double clay = ExcavationClodHurler.heft(Id.minecraft("clay"));
    double gravel = ExcavationClodHurler.heft(Id.minecraft("gravel"));
    double dirt = ExcavationClodHurler.heft(DIRT);
    double sand = ExcavationClodHurler.heft(Id.minecraft("sand"));
    double snow = ExcavationClodHurler.heft(Id.minecraft("snow_block"));

    assertThat(clay).isGreaterThan(gravel);
    assertThat(gravel).isGreaterThan(dirt);
    assertThat(dirt).isGreaterThan(sand);
    assertThat(sand).isGreaterThan(snow);
    for (String path : List.of("clay", "dirt", "coarse_dirt", "rooted_dirt", "farmland", "grass_block", "dirt_path",
        "gravel", "mycelium", "podzol", "sand", "red_sand", "soul_sand", "soul_soil", "snow", "snow_block", "mud",
        "muddy_mangrove_roots")) {
      assertThat(ExcavationBlocks.isShovelable(Id.minecraft(path))).isTrue();
      assertThat(ExcavationClodHurler.heft(Id.minecraft(path))).isBetween(0.4D, 1.25D);
    }
  }

  @Test
  void damageRisesWithDistanceToTheSweetSpotThenHolds() {
    ExcavationClodHurler.Config config = new ExcavationClodHurler.Config();

    assertThat(ExcavationClodHurler.distanceFactor(0D, config)).isEqualTo(0.6D);
    assertThat(ExcavationClodHurler.distanceFactor(4D, config)).isCloseTo(0.8D, within(1.0E-9D));
    assertThat(ExcavationClodHurler.distanceFactor(8D, config)).isEqualTo(1.0D);
    assertThat(ExcavationClodHurler.distanceFactor(20D, config)).isEqualTo(1.0D);
    assertThat(ExcavationClodHurler.damage(5.5D, 1.15D, 8D, 1D, config)).isCloseTo(8.855D, within(1.0E-9D));
    assertThat(ExcavationClodHurler.damage(2.5D, 0.7D, 2D, 0.2D, config)).isCloseTo(1.127D, within(1.0E-9D));
    assertThat(ExcavationClodHurler.damage(9.5D, 1.25D, 10D, 1D, config)).isEqualTo(config.maxDamage);
  }

  @Test
  void levelsWidenRangeAndShortenTheCooldown() {
    assertThat(hurler.range(hurler.getLevelPercent(1))).isLessThan(hurler.range(hurler.getLevelPercent(5)));
    assertThat(hurler.cooldownMillis(hurler.getLevelPercent(1))).isGreaterThan(hurler.cooldownMillis(hurler.getLevelPercent(5)));
    assertThat(hurler.damageMultiplier(hurler.getLevelPercent(1))).isLessThan(hurler.damageMultiplier(hurler.getLevelPercent(5)));
    assertThat(hurler.range(hurler.getLevelPercent(5))).isEqualTo(16D);
    assertThat(hurler.cooldownMillis(hurler.getLevelPercent(5))).isEqualTo(1200L);
  }

  @Test
  void aDigFlingsTheBlockAtTheNearestHostileAndItsDropFliesBackIntoTheInventory() {
    learn(5);
    FakeLiving far = zombie(0.5D, 61D, 12.5D);
    FakeLiving near = zombie(0.5D, 61D, 9.5D);

    dig(0, 60, 1);

    ProjectileRef ball = onlyBall();
    assertThat(ball.velocity().z()).isPositive();
    assertThat(world.items()).isEmpty();
    assertThat(count(DIRT)).isZero();
    assertThat(ball.isPersistent()).isFalse();
    assertThat(ball.data().has(AxeThrow.FLUNG_KEY)).isTrue();

    adapt.post(new ProjectileHitEvent(ball, near, null, null));
    platform.scheduler().advance(20);

    assertThat(near.damageTaken()).isCloseTo(4.5D * 0.8D * 1.4D, within(1.0E-6D));
    assertThat(far.damageTaken()).isZero();
    assertThat(count(DIRT)).isEqualTo(1);
    assertThat(world.items()).isEmpty();
    assertThat(profile.getData().getStat("excavation.clod-hurler.hits")).isEqualTo(1D);
    assertThat(XpPaid.by(profile, hurler)).isPositive();
  }

  @Test
  void withNobodyInRangeTheDigDropsAsUsual() {
    learn(5);
    zombie(0.5D, 61D, 40.5D);

    dig(0, 60, 1);

    assertThat(balls()).isEmpty();
    assertThat(world.items()).hasSize(1);
    assertThat(world.items().get(0).item().type()).isEqualTo(DIRT);
  }

  @Test
  void aMobOutOfSightIsNotATarget() {
    learn(5);
    zombie(0.5D, 61D, 6.5D);
    player.setLineOfSight(false);

    dig(0, 60, 1);

    assertThat(balls()).isEmpty();
    assertThat(world.items()).hasSize(1);
  }

  @Test
  void passiveMobsPlayersAndPetsAreNeverTargets() {
    learn(5);
    FakeLiving cow = world.add(new FakeLiving(Id.minecraft("cow"), new Loc(world, 0.5D, 61D, 5.5D)));
    platform.player("Friend", 0.5D, 61D, 6.5D);
    FakeLiving pet = zombie(0.5D, 61D, 7.5D);
    pet.setTameOwner(UUID.randomUUID());
    FakeLiving neutral = world.add(new FakeLiving(Id.minecraft("enderman"), new Loc(world, 0.5D, 61D, 8.5D)));
    neutral.trait(EntityTrait.ENEMY);

    dig(0, 60, 1);

    assertThat(balls()).isEmpty();
    assertThat(cow.damageTaken()).isZero();
    assertThat(world.items()).hasSize(1);
  }

  @Test
  void multiBlockDigsAndBuildBatchesNeverThrow() {
    learn(5);
    zombie(0.5D, 61D, 6.5D);
    BlockRef block = world.set(0, 60, 1, "dirt").blockAt(0, 60, 1);

    assertThat(BonusBlockBreaks.breakBlock(player, block, 0)).isTrue();

    assertThat(balls()).isEmpty();
    assertThat(world.items()).hasSize(1);
  }

  @Test
  void playerPlacedBlocksAreDugNormally() {
    learn(5);
    zombie(0.5D, 61D, 6.5D);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(any(BlockRef.class))).thenReturn(true);
      dig(0, 60, 1);
    }

    assertThat(balls()).isEmpty();
    assertThat(world.items()).hasSize(1);
  }

  @Test
  void itDoesNothingWithoutTheAdaptation() {
    zombie(0.5D, 61D, 6.5D);

    dig(0, 60, 1);

    assertThat(balls()).isEmpty();
    assertThat(world.items()).hasSize(1);
  }

  @Test
  void theCooldownHoldsTheNextDig() {
    learn(5);
    zombie(0.5D, 61D, 6.5D);

    dig(0, 60, 1);
    dig(1, 60, 1);

    assertThat(balls()).hasSize(1);
    assertThat(world.items()).hasSize(1);
  }

  @Test
  void theServerCapsBlocksInTheAir() {
    learn(5);
    hurler.getConfig().cooldownMillisBase = 0;
    hurler.getConfig().minCooldownMillis = 0;
    hurler.getConfig().maxInFlight = 1;
    zombie(0.5D, 61D, 6.5D);

    dig(0, 60, 1);
    dig(1, 60, 1);

    assertThat(balls()).hasSize(1);
    assertThat(world.items()).hasSize(1);
  }

  @Test
  void farmedMobsTakeTheHitButPayNoXp() {
    learn(5);
    FakeLiving zombie = zombie(0.5D, 61D, 6.5D);
    MobOrigin.record(zombie, CreatureSpawnEvent.Reason.SPAWNER);

    dig(0, 60, 1);
    adapt.post(new ProjectileHitEvent(onlyBall(), zombie, null, null));
    platform.scheduler().advance(20);

    assertThat(zombie.damageTaken()).isPositive();
    assertThat(XpPaid.by(profile, hurler)).isZero();
    assertThat(count(DIRT)).isEqualTo(1);
  }

  @Test
  void aMissComesBackOnceAndTouchesNoBlock() {
    learn(5);
    zombie(0.5D, 61D, 6.5D);
    BlockRef wall = world.set(0, 61, 4, "stone").blockAt(0, 61, 4);

    dig(0, 60, 1);
    ProjectileRef ball = onlyBall();
    adapt.post(new ProjectileHitEvent(ball, null, wall, BlockFace.NORTH));
    adapt.post(new ProjectileHitEvent(ball, null, wall, BlockFace.NORTH));
    platform.scheduler().advance(40);

    assertThat(count(DIRT)).isEqualTo(1);
    assertThat(world.items()).isEmpty();
    assertThat(wall.type()).isEqualTo(Id.minecraft("stone"));
    assertThat(world.blockAt(0, 61, 3).isAir()).isTrue();
  }

  @Test
  void aThrowThatTimesOutStillComesBackOnce() {
    learn(5);
    zombie(0.5D, 61D, 6.5D);

    dig(0, 60, 1);
    platform.scheduler().advance(200);

    assertThat(count(DIRT)).isEqualTo(1);
    assertThat(world.items()).isEmpty();
  }

  @Test
  void quittingMidFlightReturnsTheDropOnce() {
    learn(5);
    FakeLiving zombie = zombie(0.5D, 61D, 6.5D);

    dig(0, 60, 1);
    ProjectileRef ball = onlyBall();
    adapt.post(new PlayerQuitEvent(player));
    adapt.post(new ProjectileHitEvent(ball, zombie, null, null));
    platform.scheduler().advance(200);

    assertThat(count(DIRT)).isEqualTo(1);
    assertThat(world.items()).isEmpty();
    assertThat(ball.isValid()).isFalse();
  }

  @Test
  void aChunkUnloadReturnsTheDropOnce() {
    learn(5);
    zombie(0.5D, 61D, 6.5D);

    dig(0, 60, 1);
    ProjectileRef ball = onlyBall();
    adapt.post(new EntityRemoveEvent(ball, EntityRemoveEvent.Cause.UNLOAD));
    adapt.post(new ProjectileHitEvent(ball, null, null, null));
    platform.scheduler().advance(200);

    assertThat(count(DIRT)).isEqualTo(1);
    assertThat(world.items()).isEmpty();
  }

  @Test
  void stoppingTheServerReturnsTheDropOnce() {
    learn(5);
    zombie(0.5D, 61D, 6.5D);

    dig(0, 60, 1);
    hurler.unregister();
    platform.scheduler().advance(200);

    assertThat(count(DIRT)).isEqualTo(1);
    assertThat(world.items()).isEmpty();
  }

  @Test
  void anOwnerWhoDiedBeforeTheReturnFindsTheDropWhereTheBlockLanded() {
    learn(5);
    FakeLiving zombie = zombie(0.5D, 61D, 6.5D);

    dig(0, 60, 1);
    adapt.post(new ProjectileHitEvent(onlyBall(), zombie, null, null));
    player.setHealth(0D);
    platform.scheduler().advance(40);

    assertThat(count(DIRT)).isZero();
    assertThat(world.items()).hasSize(1);
    assertThat(world.items().get(0).item().type()).isEqualTo(DIRT);
  }

  @Test
  void aHitInsideProtectedLandDealsNothingButTheDropStillComesBack() {
    learn(5);
    FakeLiving zombie = zombie(0.5D, 61D, 6.5D);

    dig(0, 60, 1);
    AdaptPorts.install(ProtectorPort.class, new NoFighting());
    try {
      adapt.post(new ProjectileHitEvent(onlyBall(), zombie, null, null));
      platform.scheduler().advance(40);
    } finally {
      AdaptPorts.uninstall(ProtectorPort.class);
    }

    assertThat(zombie.damageTaken()).isZero();
    assertThat(count(DIRT)).isEqualTo(1);
    assertThat(world.items()).isEmpty();
  }

  @Test
  void strayFlungEntitiesFromACrashAreRemovedWhenTheirChunkLoads() {
    EntityRef stray = world.spawn(SNOWBALL, new Loc(world, 3D, 62D, 3D), (EntityRef entity) ->
        entity.data().set(AxeThrow.FLUNG_KEY, (byte) 1));
    EntityRef plain = world.spawn(SNOWBALL, new Loc(world, 4D, 62D, 3D), (EntityRef entity) -> {
    });

    adapt.post(new ChunkEntitiesEvent(world, new ChunkPos(0, 0), ChunkEntitiesEvent.Phase.LOAD, List.of(stray, plain)));

    assertThat(stray.isValid()).isFalse();
    assertThat(plain.isValid()).isTrue();
  }

  @Test
  void exploitDiggingForDuplicatesNeverYieldsMoreThanOneDropPerBlock() {
    learn(5);
    hurler.getConfig().cooldownMillisBase = 0;
    hurler.getConfig().minCooldownMillis = 0;
    FakeLiving zombie = zombie(0.5D, 61D, 6.5D);
    int dug = 0;
    for (int x = 0; x < 6; x++) {
      dig(x, 60, 1);
      dug++;
    }
    List<ProjectileRef> thrown = balls();
    assertThat(thrown).hasSize(6);
    adapt.post(new ProjectileHitEvent(thrown.get(0), zombie, null, null));
    adapt.post(new ProjectileHitEvent(thrown.get(0), zombie, null, null));
    adapt.post(new EntityRemoveEvent(thrown.get(1), EntityRemoveEvent.Cause.UNLOAD));
    adapt.post(new PlayerQuitEvent(player));
    adapt.post(new ProjectileHitEvent(thrown.get(2), null, null, null));
    platform.scheduler().advance(200);
    hurler.unregister();
    platform.scheduler().advance(20);

    int dropped = 0;
    for (ItemEntityRef item : world.items()) {
      dropped += item.item().amount();
    }
    assertThat(count(DIRT) + dropped).isEqualTo(dug);
  }

  /** A claim where nobody may hurt anything. */
  static final class NoFighting implements ProtectorPort {
    @Override
    public List<String> defaultProtectors() {
      return List.of("claims");
    }

    @Override
    public List<String> allProtectors() {
      return List.of("claims");
    }

    @Override
    public boolean allows(Set<String> protectors, ProtectionCheck check, PlayerRef player, Loc location, EntityRef victim,
                          Adaptation<?> adaptation) {
      return check != ProtectionCheck.PVE && check != ProtectionCheck.PVP;
    }
  }

  private void learn(int level) {
    profile.getData().getSkillLine("excavation").setAdaptation(hurler, level);
  }

  private ExcavationClodHurler adaptation() {
    Skill<?> skill = SkillRegistry.skills.get("excavation");
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (adaptation instanceof ExcavationClodHurler found) {
        return found;
      }
    }
    throw new AssertionError("Clod Hurler is not registered");
  }

  private FakeLiving zombie(double x, double y, double z) {
    AbilityHitLiving zombie = new AbilityHitLiving(adapt, hurler, Id.minecraft("zombie"), new Loc(world, x, y, z));
    zombie.trait(EntityTrait.MONSTER);
    zombie.trait(EntityTrait.ENEMY);
    return world.add(zombie);
  }

  private void dig(int x, int y, int z) {
    BlockRef block = world.set(x, y, z, "dirt").blockAt(x, y, z);
    platform.protection().attemptBreak(player, block);
  }

  private List<ProjectileRef> balls() {
    List<ProjectileRef> found = new ArrayList<>();
    for (EntityRef entity : world.entities()) {
      if (entity instanceof ProjectileRef ball && SNOWBALL.equals(ball.type()) && ball.isValid()) {
        found.add(ball);
      }
    }
    return found;
  }

  private ProjectileRef onlyBall() {
    List<ProjectileRef> found = balls();
    assertThat(found).hasSize(1);
    return found.get(0);
  }

  private int count(Id type) {
    int total = 0;
    for (ItemRef item : player.inventory().contents()) {
      if (item != null && type.equals(item.type())) {
        total += item.amount();
      }
    }
    return total;
  }
}
