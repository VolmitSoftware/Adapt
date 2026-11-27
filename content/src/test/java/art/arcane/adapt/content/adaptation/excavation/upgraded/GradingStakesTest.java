package art.arcane.adapt.content.adaptation.excavation.upgraded;

import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.engine.xp.XpProvenance;
import art.arcane.adapt.content.skill.SkillExcavation;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeItem;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/** Grading Stakes level natural ground with the hotbar shovel, fill only from the inventory and never dig placed blocks. */
class GradingStakesTest {
  private static final Id DIRT = Id.minecraft("dirt");
  private static final Id SAND = Id.minecraft("sand");
  private static final int GROUND = 60;
  private static final int RUN = 100;

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakeWorld world;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private SkillRegistry registry;
  private FakeItem shovel;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    world = platform.world();
    player = platform.player("Dredger", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillExcavation.class, SkillStealth.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    shovel = FakeItem.tool("iron_shovel", 250);
    player.inventory().setItem(2, shovel);
    for (int x = 0; x <= 5; x++) {
      for (int z = 10; z <= 15; z++) {
        for (int y = GROUND - 6; y <= GROUND; y++) {
          world.set(x, y, z, "dirt");
        }
      }
    }
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theStakesCutBumpsAndFillHolesWithTheDugSoilUsingTheHotbarShovel() {
    Landshaper landshaper = mutateAndLearn(1);
    world.set(2, GROUND + 1, 12, "dirt").set(2, GROUND + 2, 12, "dirt");
    world.set(3, GROUND, 13, "air").set(3, GROUND - 1, 13, "air");
    holdStakes(landshaper);

    plant(0, 10);
    plant(5, 15);
    platform.scheduler().advance(RUN);

    assertThat(world.blockAt(2, GROUND + 1, 12).isAir()).isTrue();
    assertThat(world.blockAt(2, GROUND + 2, 12).isAir()).isTrue();
    assertThat(world.blockAt(3, GROUND, 13).type()).isEqualTo(DIRT);
    assertThat(world.blockAt(3, GROUND - 1, 13).type()).isEqualTo(DIRT);
    assertThat(world.items()).isEmpty();
    assertThat(count(DIRT)).isZero();
    assertThat(player.inventory().heldSlot()).isEqualTo(2);
    assertThat(shovel.damage()).isEqualTo(2 * landshaper.getConfig().durabilityCostPerBlock);
    assertThat(XpPaid.by(profile, landshaper)).isPositive();
  }

  @Test
  void theStakesAreInertWithoutTheMutation() {
    Landshaper landshaper = upgrade();
    profile.getData().getSkillLine("excavation").setAdaptation(landshaper, 3);
    world.set(2, GROUND + 1, 12, "dirt");
    holdStakes(landshaper);

    plant(0, 10);
    plant(5, 15);
    platform.scheduler().advance(RUN);

    assertThat(world.blockAt(2, GROUND + 1, 12).type()).isEqualTo(DIRT);
  }

  @Test
  void placedSoilIsNeverDugSoItCannotBeCycledForXp() {
    Landshaper landshaper = mutateAndLearn(1);
    world.set(2, GROUND + 1, 12, "dirt");
    holdStakes(landshaper);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(argThat((BlockRef block) -> block.y() > GROUND)))
          .thenReturn(true);
      plant(0, 10);
      plant(5, 15);
      platform.scheduler().advance(RUN);
    }

    assertThat(world.blockAt(2, GROUND + 1, 12).type()).isEqualTo(DIRT);
    assertThat(XpPaid.by(profile, landshaper)).isZero();
  }

  @Test
  void aKeptBlockStopsItsColumnSoNothingIsLeftFloating() {
    Landshaper landshaper = mutateAndLearn(1);
    world.set(2, GROUND + 1, 12, "dirt").set(2, GROUND + 2, 12, "dirt");
    world.set(4, GROUND + 1, 14, "dirt").set(4, GROUND + 2, 14, "grass_block").set(4, GROUND + 3, 14, "oak_log");
    holdStakes(landshaper);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(argThat((BlockRef block) -> block.x() == 2 && block.y() == GROUND + 2)))
          .thenReturn(true);
      plant(0, 10);
      plant(5, 15);
      platform.scheduler().advance(RUN);
    }

    assertThat(world.blockAt(2, GROUND + 2, 12).type()).isEqualTo(DIRT);
    assertThat(world.blockAt(2, GROUND + 1, 12).type()).isEqualTo(DIRT);
    assertThat(world.blockAt(4, GROUND + 2, 14).type()).isEqualTo(Id.minecraft("grass_block"));
    assertThat(world.blockAt(4, GROUND + 1, 14).type()).isEqualTo(DIRT);
  }

  @Test
  void holesAreFilledOnlyFromTheInventoryAndStopWhenItRunsOut() {
    Landshaper landshaper = mutateAndLearn(1);
    world.set(3, GROUND, 13, "air").set(3, GROUND - 1, 13, "air").set(4, GROUND, 14, "air");
    player.inventory().setItem(20, new FakeItem(SAND, 2));
    holdStakes(landshaper);

    plant(0, 10);
    plant(5, 15);
    platform.scheduler().advance(RUN);

    assertThat(count(SAND)).isZero();
    assertThat(world.blockAt(3, GROUND - 1, 13).type()).isEqualTo(SAND);
    int filled = 0;
    for (BlockRef cell : List.of(world.blockAt(3, GROUND, 13), world.blockAt(4, GROUND, 14))) {
      if (!cell.isAir()) {
        filled++;
      }
    }
    assertThat(filled).isEqualTo(1);
  }

  @Test
  void aHoleDeeperThanTheReachIsLeftAlone() {
    Landshaper landshaper = mutateAndLearn(1);
    for (int y = GROUND - 4; y <= GROUND; y++) {
      world.set(3, y, 13, "air");
    }
    player.inventory().setItem(20, new FakeItem(DIRT, 10));
    holdStakes(landshaper);

    plant(0, 10);
    plant(5, 15);
    platform.scheduler().advance(RUN);

    assertThat(world.blockAt(3, GROUND, 13).isAir()).isTrue();
    assertThat(count(DIRT)).isEqualTo(10);
  }

  @Test
  void stakesTooFarApartLevelNothing() {
    Landshaper landshaper = mutateAndLearn(1);
    world.set(2, GROUND + 1, 12, "dirt").set(12, GROUND, 10, "dirt");
    holdStakes(landshaper);

    plant(0, 10);
    plant(12, 10);
    platform.scheduler().advance(RUN);

    assertThat(world.blockAt(2, GROUND + 1, 12).type()).isEqualTo(DIRT);
    assertThat(player.actionBars()).isNotEmpty();
  }

  @Test
  void pullingTheStakesBeforeTheRunLevelsNothing() {
    Landshaper landshaper = mutateAndLearn(1);
    world.set(2, GROUND + 1, 12, "dirt");
    holdStakes(landshaper);

    plant(0, 10);
    plant(5, 15);
    player.setSneaking(true);
    use(null);
    player.setSneaking(false);
    platform.scheduler().advance(RUN);

    assertThat(world.blockAt(2, GROUND + 1, 12).type()).isEqualTo(DIRT);
    assertThat(shovel.damage()).isZero();
  }

  private void plant(int x, int z) {
    use(world.blockAt(x, GROUND, z));
  }

  private void use(BlockRef block) {
    ItemRef hand = player.inventory().mainHand();
    adapt.post(new PlayerInteractEvent(player, block == null ? InteractAction.RIGHT_CLICK_AIR
        : InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND, hand, block, BlockFace.UP, null, UseResult.DEFAULT,
        UseResult.DEFAULT));
  }

  private void holdStakes(Landshaper landshaper) {
    player.inventory().setItem(0, ((GradingStakes) landshaper.getTools().getFirst()).create(new GradingStakes.Data()));
    player.inventory().setHeldSlot(0);
  }

  private int count(Id type) {
    int total = 0;
    for (ItemRef item : player.inventory().contents()) {
      if (item != null && !item.isEmpty() && item.type().equals(type)) {
        total += item.amount();
      }
    }
    return total;
  }

  private Landshaper mutateAndLearn(int level) {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("excavation", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("excavation"), registry.getSkill("stealth"),
        "Dredger")).isEqualTo(SkillMutations.Result.MUTATED);
    Landshaper landshaper = upgrade();
    profile.getData().getSkillLine("excavation").setAdaptation(landshaper, level);
    return landshaper;
  }

  private Landshaper upgrade() {
    return SkillHost.of(registry.getSkill("excavation")).getUpgrades().stream().filter(Landshaper.class::isInstance)
        .map(Landshaper.class::cast).findFirst().orElseThrow();
  }
}
