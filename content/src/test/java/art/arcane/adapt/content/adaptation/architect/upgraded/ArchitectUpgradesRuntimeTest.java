package art.arcane.adapt.content.adaptation.architect.upgraded;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.engine.mutation.PairKeys;
import art.arcane.adapt.api.mutation.SkillPair;

import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.engine.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.architect.ArchitectDemolition;
import art.arcane.adapt.content.adaptation.architect.ArchitectGlass;
import art.arcane.adapt.content.adaptation.architect.ArchitectPlacement;
import art.arcane.adapt.content.skill.SkillArchitect;
import art.arcane.adapt.content.skill.SkillCrafting;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeItemEntity;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

class ArchitectUpgradesRuntimeTest {
  private static final Id STONE = Id.minecraft("stone");
  private static final Id GLASS = Id.minecraft("glass");

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
    player = platform.player("Mason", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillArchitect.class, SkillCrafting.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theMutatedSkillOffersTheThreeUpgrades() {
    mutate();

    assertThat(registry.getMutatedSkill(PairKeys.parse("architect+crafting")).getAdaptations()).extracting((Adaptation<?> adaptation) -> adaptation.getId().toString())
        .containsSubsequence("adapt:architect/upgrade/master_builder", "adapt:architect/upgrade/skyhook", "adapt:architect/upgrade/wrecking_hand");
  }

  @Test
  void masterBuilderRefillsTheEmptiedHandFromTheInventory() {
    mutate();
    learn(MasterBuilder.class, 1);

    placeLastStone();

    assertThat(player.inventory().mainHand().type()).isEqualTo(STONE);
    assertThat(player.inventory().mainHand().amount()).isEqualTo(32);
  }

  @Test
  void masterBuilderFillsAWiderFaceAtEachLevel() {
    mutate();
    player.setSneaking(true);
    ArchitectPlacement wand = original(ArchitectPlacement.class);
    int[] sides = {3, 5, 7};
    for (int level = 1; level <= 3; level++) {
      learn(MasterBuilder.class, level);
      int y = 40 + (level * 4);
      for (int x = -4; x <= 4; x++) {
        for (int z = -4; z <= 4; z++) {
          world.set(x, y, z, "stone");
        }
      }
      BlockRef source = world.blockAt(0, y, 0);
      wand.runPlayerViewport(BlockFace.UP, source, STONE, player);
      BlockRef placed = world.set(0, y + 1, 0, "stone").blockAt(0, y + 1, 0);
      player.holding(new FakeItem(STONE, 64));
      adapt.post(new BlockPlaceEvent(player, placed, FakeBlockState.of("air"), source, player.inventory().mainHand(),
          Hand.MAIN_HAND));
      platform.scheduler().advance(2);

      int filled = 0;
      for (int x = -4; x <= 4; x++) {
        for (int z = -4; z <= 4; z++) {
          filled += world.blockAt(x, y + 1, z).type().equals(STONE) ? 1 : 0;
        }
      }
      assertThat(filled).as("level %d", level).isEqualTo(sides[level - 1] * sides[level - 1]);
    }
  }

  @Test
  void masterBuilderFillsCountAsPlayerPlaced() {
    mutate();
    player.setSneaking(true);
    ArchitectPlacement wand = original(ArchitectPlacement.class);
    learn(MasterBuilder.class, 1);
    for (int x = -1; x <= 1; x++) {
      for (int z = -1; z <= 1; z++) {
        world.set(x, 44, z, "stone");
      }
    }
    BlockRef source = world.blockAt(0, 44, 0);
    wand.runPlayerViewport(BlockFace.UP, source, STONE, player);
    BlockRef placed = world.set(0, 45, 0, "stone").blockAt(0, 45, 0);

    player.holding(new FakeItem(STONE, 64));
    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      adapt.post(new BlockPlaceEvent(player, placed, FakeBlockState.of("air"), source, player.inventory().mainHand(),
          Hand.MAIN_HAND));
      platform.scheduler().advance(2);

      provenance.verify(() -> XpProvenance.recordPlacement(argThat((BlockRef block) -> block.x() == 1
          && block.y() == 45 && block.z() == 1)));
    }
  }

  @Test
  void masterBuilderDoesNothingForAPlayerWhoHasNotMutated() {
    learn(MasterBuilder.class, 3);

    placeLastStone();

    assertThat(player.inventory().mainHand().isEmpty()).isTrue();
  }

  @Test
  void skyhookTurnsASneakPlacedBlockIntoFootingThatDissolves() {
    mutate();
    learn(Skyhook.class, 3);
    player.setSneaking(true);
    BlockRef placed = world.set(0, 60, 0, "stone").blockAt(0, 60, 0);

    adapt.post(new BlockPlaceEvent(player, placed, FakeBlockState.of("air"), placed.relative(BlockFace.DOWN),
        new FakeItem(STONE, 4), Hand.MAIN_HAND));
    platform.scheduler().advance(30 * 20 + 1);

    assertThat(profile.getData().getStat("architect.scaffolder.blocks-scaffolded")).isEqualTo(1D);
    assertThat(placed.type().isAir()).isTrue();
  }

  @Test
  void wreckingHandPicksUpGlassByHandAtItsTopLevel() {
    mutate();
    learn(WreckingHand.class, 3);
    player.holding(FakeItem.empty());
    BlockRef glass = world.set(2, 60, 0, "glass").blockAt(2, 60, 0);

    adapt.post(new BlockBreakEvent(player, glass, true, 0));

    assertThat(world.items()).extracting(FakeItemEntity::item).anyMatch(item -> item.type().equals(GLASS));
    assertThat(original(ArchitectGlass.class).getActiveLevel(player)).isEqualTo(original(ArchitectGlass.class).getMaxLevel());
  }

  @Test
  void wreckingHandErasesAFreshPlacementWithoutAMemoryMultiplier() {
    mutate();
    learn(WreckingHand.class, 1);
    player.setSneaking(true);
    BlockRef placed = world.set(0, 60, 4, "stone").blockAt(0, 60, 4);
    adapt.post(new BlockPlaceEvent(player, placed, FakeBlockState.of("air"), placed.relative(BlockFace.DOWN),
        new FakeItem(STONE, 4), Hand.MAIN_HAND));

    BlockBreakEvent erase = adapt.post(new BlockBreakEvent(player, placed, true, 0));

    assertThat(erase.dropItems()).isFalse();
    assertThat(profile.getData().getStat("architect.demolition.blocks-demolished")).isEqualTo(1D);
    assertThat(Mutations.strength(original(ArchitectDemolition.class), player)).isEqualTo(1D);
  }

  private void placeLastStone() {
    player.holding(new FakeItem(STONE, 1));
    player.inventory().setItem(5, new FakeItem(STONE, 32));
    BlockRef placed = world.set(0, 60, 0, "stone").blockAt(0, 60, 0);
    adapt.post(new BlockPlaceEvent(player, placed, FakeBlockState.of("air"), placed.relative(BlockFace.DOWN),
        new FakeItem(STONE, 1), Hand.MAIN_HAND));
    player.inventory().setMainHand(FakeItem.empty());
    platform.scheduler().advance(2);
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("architect", "crafting")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("architect"), registry.getSkill("crafting"), "Mason"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = SkillHost.of(registry.getSkill("architect")).getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
    profile.getData().getSkillLine("architect").setAdaptation(upgrade, level);
    return upgrade;
  }

  private <A extends Adaptation<?>> A original(Class<A> type) {
    return registry.getSkill("architect").getAdaptations().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }
}
