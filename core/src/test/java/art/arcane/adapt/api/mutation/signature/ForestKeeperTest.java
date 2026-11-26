package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.axe.upgraded.Timberfall;
import art.arcane.adapt.content.skill.SkillAxes;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static art.arcane.adapt.util.data.BlockFlag.VEIN_MINED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/** Axes and Herbalism, either leading: a felled tree replants from a carried sapling and ages the saplings around it. */
class ForestKeeperTest {
  private static final Id SAPLING = Id.minecraft("oak_sapling");

  @TempDir
  Path data;

  private SignatureBench bench;

  @BeforeEach
  void start() {
    bench = new SignatureBench(data, List.of(SkillAxes.class, SkillHerbalism.class));
    bench.player.holding(FakeItem.tool("iron_axe", 250));
    bench.player.setSneaking(true);
    for (int x = -8; x <= 14; x++) {
      for (int z = -8; z <= 14; z++) {
        bench.world.set(x, 60, z, "dirt");
      }
    }
  }

  @AfterEach
  void stop() {
    bench.close();
  }

  @Test
  void aFelledTreeReplantsFromACarriedSaplingAndAgesTheSaplingsAround() {
    bench.signature("axes", "herbalism", 1);
    bench.upgrade("axes", Timberfall.class, 1);
    bench.player.inventory().setItem(20, new FakeItem(SAPLING, 2));
    BlockRef young = sapling(6, 3);

    fell();

    assertThat(bench.world.blockAt(3, 61, 3).type()).isEqualTo(SAPLING);
    assertThat(bench.count(SAPLING)).isEqualTo(1);
    assertThat(young.state().property("stage")).isEqualTo("1");
  }

  @Test
  void eitherSkillMayLead() {
    bench.signature("herbalism", "axes", 1);
    bench.upgrade("axes", Timberfall.class, 1);
    BlockRef young = sapling(6, 3);

    fell();

    assertThat(young.state().property("stage")).isEqualTo("1");
  }

  @Test
  void nothingGrowsWithoutTheLearnedSignature() {
    bench.kit.mutate("axes", "herbalism");
    bench.upgrade("axes", Timberfall.class, 1);
    bench.player.inventory().setItem(20, new FakeItem(SAPLING, 2));
    BlockRef young = sapling(6, 3);

    fell();

    assertThat(bench.world.blockAt(3, 61, 3).isAir()).isTrue();
    assertThat(young.state().property("stage")).isEqualTo("0");
  }

  @Test
  void eachFellAgesNoMoreSaplingsThanTheLevelAllowsAndTheCooldownHolds() {
    bench.signature("axes", "herbalism", 1);
    bench.upgrade("axes", Timberfall.class, 1);
    ForestKeeper keeper = bench.handler("axes", "herbalism", ForestKeeper.class);
    keeper.getConfig().cooldownMillis = 60_000L;
    List<BlockRef> young = List.of(sapling(5, 1), sapling(5, 2), sapling(5, 4), sapling(5, 5), sapling(1, 5),
        sapling(1, 1));

    fell();
    fell();

    long aged = young.stream().filter((BlockRef block) -> "1".equals(block.state().property("stage"))).count();
    assertThat(aged).isEqualTo(keeper.saplings(1)).isEqualTo(4);
    assertThat(keeper.saplings(3)).isEqualTo(24);
  }

  @Test
  void aTreeOfPlacedLogsGrowsNothing() {
    bench.signature("axes", "herbalism", 3);
    bench.upgrade("axes", Timberfall.class, 1);
    bench.player.inventory().setItem(20, new FakeItem(SAPLING, 2));
    BlockRef young = sapling(6, 3);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(any(BlockRef.class))).thenReturn(true);
      fell();
    }

    assertThat(young.state().property("stage")).isEqualTo("0");
    assertThat(bench.count(SAPLING)).isEqualTo(2);
  }

  @Test
  void aLogAnotherVeinminerBreaksIsNotATimberfallFell() {
    bench.signature("axes", "herbalism", 3);
    bench.upgrade("axes", Timberfall.class, 1);
    bench.player.inventory().setItem(20, new FakeItem(SAPLING, 2));
    BlockRef young = sapling(6, 3);
    BlockRef origin = bench.world.blockAt(3, 61, 3);
    bench.world.set(3, 61, 3, "oak_log");

    VEIN_MINED.add(origin);
    try {
      bench.breakBlock(origin);
    } finally {
      VEIN_MINED.remove(origin);
    }
    bench.world.set(3, 61, 3, "air");
    bench.advance(8);

    assertThat(young.state().property("stage")).isEqualTo("0");
    assertThat(bench.count(SAPLING)).isEqualTo(2);
  }

  @Test
  void theSaplingSearchReadsABoundedArea() {
    bench.signature("axes", "herbalism", 3);
    bench.upgrade("axes", Timberfall.class, 1);
    BlockRef near = sapling(4, 3);
    BlockRef far = sapling(-8, -8);

    fell();

    assertThat(near.state().property("stage")).isEqualTo("1");
    assertThat(far.state().property("stage")).isEqualTo("0");
  }

  private BlockRef sapling(int x, int z) {
    bench.world.set(new BlockPos(x, 61, z), new FakeBlockState(SAPLING, Map.of("stage", "0")));
    return bench.world.blockAt(x, 61, z);
  }

  private void fell() {
    for (int y = 61; y <= 66; y++) {
      bench.world.set(3, y, 3, "oak_log");
    }
    bench.breakBlock(bench.world.blockAt(3, 61, 3));
    bench.world.set(3, 61, 3, "air");
    bench.advance(8);
  }
}
