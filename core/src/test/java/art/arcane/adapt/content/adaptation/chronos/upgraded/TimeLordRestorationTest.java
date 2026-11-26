package art.arcane.adapt.content.adaptation.chronos.upgraded;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.protection.ProtectionProbePort;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.item.ChronoTimeBottle;
import art.arcane.adapt.content.skill.SkillChronos;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockBurnEvent;
import art.arcane.adapt.platform.event.entity.EntityChangeBlockEvent;
import art.arcane.adapt.platform.event.entity.EntityExplodeEvent;
import art.arcane.adapt.platform.event.entity.ItemSpawnEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeItemEntity;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/** Time Lord's restoration brings back only what explosions, fire and endermen destroyed, and never for free twice. */
class TimeLordRestorationTest {
  private static final Id STONE = Id.minecraft("stone");
  private static final Id OAK = Id.minecraft("oak_planks");
  private static final Id CLOCK = Id.minecraft("clock");
  private static final Id AIR = Id.minecraft("air");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakeWorld world;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private SkillRegistry registry;
  private TimeLord lord;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    world = platform.world();
    player = platform.player("Timekeeper", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillChronos.class, SkillStealth.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    lord = registry.getSkill("chronos").getUpgrades().stream().filter(TimeLord.class::isInstance)
        .map(TimeLord.class::cast).findFirst().orElseThrow();
    for (int x = -10; x <= 10; x++) {
      for (int z = -10; z <= 10; z++) {
        world.set(x, 59, z, "stone");
      }
    }
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aCraterWithNothingDroppedHealsForOneChargeAndCountsAsPlaced() {
    List<BlockRef> crater;
    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class)) {
      mutateAndLearn(1);
      crater = crater(STONE, 3);
      explode(crater);
      bottle(100D);
      restoreAt(world.blockAt(4, 59, 0));
      platform.scheduler().advance(3);
      for (BlockRef block : crater) {
        provenance.verify(() -> XpProvenance.recordPlacement(block));
      }
    }

    for (BlockRef block : crater) {
      assertThat(block.type()).isEqualTo(STONE);
    }
    assertThat(stored()).isEqualTo(40D);
  }

  @Test
  void restoringTwiceNeverBringsTheSameBlockBackAgain() {
    mutateAndLearn(1);
    List<BlockRef> crater = crater(STONE, 3);
    explode(crater);
    bottle(200D);
    restoreAt(world.blockAt(4, 59, 0));
    platform.scheduler().advance(3);
    for (BlockRef block : crater) {
      block.setType(AIR, false);
    }

    restoreAt(world.blockAt(4, 59, 0));
    platform.scheduler().advance(3);

    for (BlockRef block : crater) {
      assertThat(block.isAir()).isTrue();
    }
    assertThat(stored()).isEqualTo(140D);
  }

  @Test
  void dropsLyingThereAreTakenBack() {
    mutateAndLearn(1);
    List<BlockRef> crater = crater(STONE, 2);
    explode(crater);
    ItemEntityRef first = spawnDrop(crater.getFirst(), STONE, 1);
    ItemEntityRef second = spawnDrop(crater.getLast(), STONE, 1);
    bottle(100D);

    restoreAt(world.blockAt(4, 59, 0));
    platform.scheduler().advance(3);

    for (BlockRef block : crater) {
      assertThat(block.type()).isEqualTo(STONE);
    }
    assertThat(world.items()).doesNotContain((FakeItemEntity) first, (FakeItemEntity) second);
  }

  @Test
  void eachBlockNeedsOnlyItsOwnDrop() {
    mutateAndLearn(1);
    List<BlockRef> crater = crater(STONE, 2);
    explode(crater);
    ItemEntityRef first = spawnDrop(crater.getFirst(), STONE, 1);
    spawnDrop(crater.getLast(), STONE, 1).remove();
    bottle(100D);

    restoreAt(world.blockAt(4, 59, 0));
    platform.scheduler().advance(3);

    assertThat(crater.getFirst().type()).isEqualTo(STONE);
    assertThat(crater.getLast().isAir()).isTrue();
    assertThat(world.items()).doesNotContain((FakeItemEntity) first);
  }

  @Test
  void itemsThatAreNotTheBlocksOwnDropStayOnTheGround() {
    mutateAndLearn(1);
    List<BlockRef> crater = crater(STONE, 2);
    explode(crater);
    ItemEntityRef loot = spawnDrop(crater.getFirst(), OAK, 3);
    FakeItem named = new FakeItem(STONE, 1);
    named.setDisplayName("Keepsake");
    ItemEntityRef keepsake = world.dropItem(new Vec3(crater.getLast().x() + 0.5D, 60.4D, 0.5D), named);
    adapt.post(new ItemSpawnEvent(keepsake, keepsake.location()));
    ItemEntityRef outside = spawnDrop(world.blockAt(5, 62, 0), STONE, 1);
    bottle(100D);

    restoreAt(world.blockAt(4, 59, 0));
    platform.scheduler().advance(3);

    for (BlockRef block : crater) {
      assertThat(block.type()).isEqualTo(STONE);
    }
    assertThat(world.items()).contains((FakeItemEntity) loot, (FakeItemEntity) keepsake, (FakeItemEntity) outside);
    assertThat(keepsake.item().displayName()).isEqualTo("Keepsake");
  }

  @Test
  void aCraterHealsOneLayerPerTickFromTheBottom() {
    mutateAndLearn(1);
    List<BlockRef> crater = new ArrayList<>(crater(STONE, 2));
    world.set(5, 61, 0, "stone");
    world.set(6, 61, 0, "stone");
    crater.add(world.blockAt(5, 61, 0));
    crater.add(world.blockAt(6, 61, 0));
    explode(crater);
    bottle(100D);

    restoreAt(world.blockAt(4, 59, 0));
    platform.scheduler().advance(1);

    assertThat(world.blockAt(5, 60, 0).type()).isEqualTo(STONE);
    assertThat(world.blockAt(6, 60, 0).type()).isEqualTo(STONE);
    assertThat(world.blockAt(5, 61, 0).isAir()).isTrue();
    assertThat(world.blockAt(6, 61, 0).isAir()).isTrue();

    platform.scheduler().advance(4);

    for (BlockRef block : crater) {
      assertThat(block.type()).isEqualTo(STONE);
    }
  }

  @Test
  void aProtectedBlockIsSkippedAndTheRestStillComeBack() {
    mutateAndLearn(1);
    List<BlockRef> crater = crater(STONE, 3);
    explode(crater);
    bottle(100D);
    BlockRef guarded = crater.get(1);
    AdaptPorts.install(ProtectionProbePort.class, new ProtectionProbePort() {
      @Override
      public boolean probePlace(PlayerRef actor, BlockRef block) {
        return !block.pos().equals(guarded.pos());
      }
    });
    try {
      restoreAt(world.blockAt(4, 59, 0));
      platform.scheduler().advance(6);
    } finally {
      AdaptPorts.uninstall(ProtectionProbePort.class);
    }

    assertThat(crater.getFirst().type()).isEqualTo(STONE);
    assertThat(guarded.isAir()).isTrue();
    assertThat(crater.getLast().type()).isEqualTo(STONE);
    assertThat(stored()).isEqualTo(40D);
  }

  @Test
  void aVetoedBlockGivesItsDropBack() {
    mutateAndLearn(1);
    List<BlockRef> crater = crater(STONE, 1);
    explode(crater);
    spawnDrop(crater.getFirst(), STONE, 1);
    bottle(100D);
    AdaptPorts.install(ProtectionProbePort.class, new ProtectionProbePort() {
      @Override
      public boolean callPlace(PlayerRef actor, BlockRef block, BlockStateRef replaced, BlockRef against, ItemRef item) {
        return false;
      }
    });
    try {
      restoreAt(world.blockAt(4, 59, 0));
      platform.scheduler().advance(3);
    } finally {
      AdaptPorts.uninstall(ProtectionProbePort.class);
    }

    assertThat(crater.getFirst().isAir()).isTrue();
    assertThat(world.items()).hasSize(1);
    assertThat(world.items().getFirst().item().type()).isEqualTo(STONE);
    assertThat(world.items().getFirst().item().amount()).isEqualTo(1);
  }

  @Test
  void pickingUpTheDropsFirstLeavesTheCraterOpen() {
    mutateAndLearn(1);
    List<BlockRef> crater = crater(STONE, 2);
    explode(crater);
    spawnDrop(crater.getFirst(), STONE, 1).remove();
    spawnDrop(crater.getLast(), STONE, 1).remove();
    bottle(100D);

    restoreAt(world.blockAt(4, 59, 0));
    platform.scheduler().advance(3);

    for (BlockRef block : crater) {
      assertThat(block.isAir()).isTrue();
    }
    assertThat(stored()).isEqualTo(100D);
  }

  @Test
  void blocksAPlayerBrokeAreNeverRestored() {
    mutateAndLearn(1);
    BlockRef block = world.blockAt(5, 60, 0);
    world.set(5, 60, 0, "stone");
    adapt.post(new BlockBreakEvent(player, block, true, 0));
    block.setType(AIR, false);
    bottle(100D);

    restoreAt(world.blockAt(5, 59, 0));
    platform.scheduler().advance(3);

    assertThat(block.isAir()).isTrue();
    assertThat(stored()).isEqualTo(100D);
  }

  @Test
  void burnedBlocksComeBack() {
    mutateAndLearn(1);
    world.set(5, 60, 0, "oak_planks");
    BlockRef plank = world.blockAt(5, 60, 0);
    lord.onTick();
    adapt.post(new BlockBurnEvent(plank, null));
    plank.setType(AIR, false);
    bottle(100D);

    restoreAt(world.blockAt(5, 59, 0));
    platform.scheduler().advance(3);

    assertThat(plank.type()).isEqualTo(OAK);
  }

  @Test
  void anEndermansBlockNeedsItsItemOnTheGround() {
    mutateAndLearn(1);
    world.set(5, 60, 0, "stone");
    BlockRef taken = world.blockAt(5, 60, 0);
    lord.onTick();
    FakeLiving enderman = world.add(new FakeLiving(Id.minecraft("enderman"), new Loc(world, 5.5D, 61D, 0.5D)));
    adapt.post(new EntityChangeBlockEvent(enderman, taken, new FakeBlockState(AIR)));
    taken.setType(AIR, false);
    bottle(100D);

    restoreAt(world.blockAt(5, 59, 0));
    platform.scheduler().advance(3);
    assertThat(taken.isAir()).isTrue();

    world.dropItem(new Vec3(6.5D, 60.5D, 0.5D), new FakeItem(STONE, 1));
    restoreAt(world.blockAt(5, 59, 0));
    platform.scheduler().advance(3);

    assertThat(taken.type()).isEqualTo(STONE);
    assertThat(world.items()).isEmpty();
  }

  @Test
  void nothingIsRememberedWhenNoHolderIsNear() {
    mutateAndLearn(1);
    player.teleport(new Loc(world, 200.5D, 61D, 0.5D), TeleportCause.PLUGIN);
    lord.onTick();
    List<BlockRef> crater = crater(STONE, 2);
    adapt.post(new EntityExplodeEvent(creeper(), new Loc(world, 5.5D, 60D, 0.5D), crater, 1F));
    for (BlockRef block : crater) {
      block.setType(AIR, false);
    }
    player.teleport(new Loc(world, 0.5D, 61D, 0.5D), TeleportCause.PLUGIN);
    bottle(100D);

    restoreAt(world.blockAt(4, 59, 0));
    platform.scheduler().advance(3);

    for (BlockRef block : crater) {
      assertThat(block.isAir()).isTrue();
    }
  }

  @Test
  void containersAreNeverRemembered() {
    mutateAndLearn(1);
    world.set(5, 60, 0, "barrel");
    world.blockEntity(new BlockPos(5, 60, 0));
    BlockRef barrel = world.blockAt(5, 60, 0);
    explode(List.of(barrel));
    bottle(100D);

    restoreAt(world.blockAt(5, 59, 0));
    platform.scheduler().advance(3);

    assertThat(barrel.isAir()).isTrue();
    assertThat(stored()).isEqualTo(100D);
  }

  @Test
  void anEmptyBottleRestoresNothing() {
    mutateAndLearn(1);
    List<BlockRef> crater = crater(STONE, 2);
    explode(crater);
    bottle(59D);

    restoreAt(world.blockAt(4, 59, 0));
    platform.scheduler().advance(3);

    for (BlockRef block : crater) {
      assertThat(block.isAir()).isTrue();
    }
    assertThat(stored()).isEqualTo(59D);
  }

  @Test
  void aProtectedSpotIsLeftAloneAndCostsNothing() {
    mutateAndLearn(1);
    List<BlockRef> crater = crater(STONE, 2);
    explode(crater);
    bottle(100D);
    platform.protection().deny(true);

    restoreAt(world.blockAt(4, 59, 0));
    platform.scheduler().advance(3);

    for (BlockRef block : crater) {
      assertThat(block.isAir()).isTrue();
    }
    assertThat(stored()).isEqualTo(100D);
  }

  @Test
  void restorationIsInertWithoutTheMutation() {
    profile.getData().getSkillLine("chronos").setAdaptation(lord, 3);
    lord.onTick();
    List<BlockRef> crater = crater(STONE, 2);
    adapt.post(new EntityExplodeEvent(creeper(), new Loc(world, 5.5D, 60D, 0.5D), crater, 1F));
    for (BlockRef block : crater) {
      block.setType(AIR, false);
    }
    bottle(100D);

    PlayerInteractEvent click = restoreAt(world.blockAt(4, 59, 0));
    platform.scheduler().advance(3);

    assertThat(click.isCancelled()).isFalse();
    for (BlockRef block : crater) {
      assertThat(block.isAir()).isTrue();
    }
  }

  @Test
  void blocksOlderThanTheWindowStayGone() {
    mutateAndLearn(1);
    List<BlockRef> crater = crater(STONE, 2);
    explode(crater);
    bottle(100D);
    lord.getConfig().restoreWindowSecondsLevel1 = -1D;

    restoreAt(world.blockAt(4, 59, 0));
    platform.scheduler().advance(3);

    for (BlockRef block : crater) {
      assertThat(block.isAir()).isTrue();
    }
  }

  private List<BlockRef> crater(Id type, int size) {
    List<BlockRef> blocks = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      world.set(5 + i, 60, 0, type.path());
      blocks.add(world.blockAt(5 + i, 60, 0));
    }
    return blocks;
  }

  private void explode(List<BlockRef> blocks) {
    lord.onTick();
    adapt.post(new EntityExplodeEvent(creeper(), new Loc(world, 5.5D, 60D, 0.5D), blocks, 0.3F));
    for (BlockRef block : blocks) {
      block.setType(AIR, false);
    }
  }

  private ItemEntityRef spawnDrop(BlockRef at, Id type, int amount) {
    ItemEntityRef item = world.dropItem(new Vec3(at.x() + 0.5D, at.y() + 0.4D, at.z() + 0.5D), new FakeItem(type, amount));
    adapt.post(new ItemSpawnEvent(item, item.location()));
    return item;
  }

  private FakeLiving creeper() {
    return world.add(new FakeLiving(Id.minecraft("creeper"), new Loc(world, 5.5D, 60D, 0.5D)));
  }

  private void bottle(double seconds) {
    player.inventory().setItem(9, ChronoTimeBottle.withStoredSeconds(seconds));
  }

  private double stored() {
    return ChronoTimeBottle.getStoredSeconds(player.inventory().item(9));
  }

  private PlayerInteractEvent restoreAt(BlockRef block) {
    player.holding(new FakeItem(CLOCK, 1));
    player.setSneaking(true);
    return adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND,
        player.inventory().mainHand(), block, BlockFace.UP, null, UseResult.DEFAULT, UseResult.DEFAULT));
  }

  private void mutateAndLearn(int level) {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("chronos", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("chronos"), registry.getSkill("stealth"),
        "Timekeeper")).isEqualTo(SkillMutations.Result.MUTATED);
    profile.getData().getSkillLine("chronos").setAdaptation(lord, level);
  }
}
