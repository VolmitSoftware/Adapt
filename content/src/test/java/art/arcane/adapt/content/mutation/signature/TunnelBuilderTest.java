package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.engine.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.pickaxe.upgraded.Earthshaker;
import art.arcane.adapt.content.skill.SkillArchitect;
import art.arcane.adapt.content.skill.SkillPickaxes;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/** Pickaxes and Architect, either leading: Earthshaker's bore is lined with the off-hand block and lit with torches. */
class TunnelBuilderTest {
  private static final Id STONE = Id.minecraft("stone");
  private static final Id BRICKS = Id.minecraft("stone_bricks");
  private static final Id TORCH = Id.minecraft("torch");

  @TempDir
  Path data;

  private SignatureBench bench;

  @BeforeEach
  void start() {
    bench = new SignatureBench(data, List.of(SkillPickaxes.class, SkillArchitect.class));
    bench.player.holding(FakeItem.tool("diamond_pickaxe", 1561));
    bench.player.inventory().setOffHand(new FakeItem(BRICKS, 32));
    bench.player.inventory().setItem(20, new FakeItem(TORCH, 4));
    rock(2);
    rock(3);
  }

  @AfterEach
  void stop() {
    bench.close();
  }

  @Test
  void theFloorIsSwappedForTheOffHandBlockAndATorchGoesDown() {
    bench.signature("pickaxe", "architect", 1);
    bench.upgrade("pickaxe", Earthshaker.class, 1);

    bore(2);

    assertThat(bench.placed(BRICKS, floor(2))).isEqualTo(3);
    assertThat(bench.placed(BRICKS, walls(2))).isZero();
    assertThat(bench.world.blockAt(-1, 59, 2).type()).isEqualTo(TORCH);
    assertThat(bench.count(BRICKS)).isEqualTo(29);
    assertThat(bench.count(TORCH)).isEqualTo(3);
  }

  @Test
  void eitherSkillMayLeadAndTheTopLevelLinesWallsAndCeiling() {
    bench.signature("architect", "pickaxe", 3);
    bench.upgrade("pickaxe", Earthshaker.class, 1);

    bore(2);

    assertThat(bench.placed(BRICKS, floor(2))).isEqualTo(3);
    assertThat(bench.placed(BRICKS, walls(2))).isEqualTo(6);
    assertThat(bench.placed(BRICKS, ceiling(2))).isEqualTo(3);
    assertThat(bench.count(BRICKS)).isEqualTo(20);
  }

  @Test
  void nothingIsLinedWithoutTheLearnedSignature() {
    bench.kit.mutate("pickaxe", "architect");
    bench.upgrade("pickaxe", Earthshaker.class, 1);

    bore(2);

    assertThat(bench.placed(BRICKS, floor(2))).isZero();
    assertThat(bench.count(BRICKS)).isEqualTo(32);
    assertThat(bench.count(TORCH)).isEqualTo(4);
  }

  @Test
  void torchesKeepTheirSpacingAndTheCooldownHolds() {
    bench.signature("pickaxe", "architect", 1);
    bench.upgrade("pickaxe", Earthshaker.class, 1);

    bore(2);
    bore(3);
    int afterTwoBores = bench.count(TORCH);
    bench.handler("pickaxe", "architect", TunnelBuilder.class).getConfig().cooldownMillis = 60_000L;
    rock(4);
    rock(5);
    bore(4);
    bore(5);

    assertThat(afterTwoBores).isEqualTo(3);
    assertThat(bench.placed(BRICKS, floor(3))).isEqualTo(3);
    assertThat(bench.placed(BRICKS, floor(5))).isZero();
  }

  @Test
  void placedStoneIsNeverSwappedSoNoBlockCanBeFarmed() {
    bench.signature("pickaxe", "architect", 3);
    bench.upgrade("pickaxe", Earthshaker.class, 1);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(any(BlockRef.class))).thenReturn(true);
      bore(2);
    }

    assertThat(bench.placed(STONE, floor(2))).isEqualTo(3);
    assertThat(bench.placed(STONE, walls(2))).isEqualTo(6);
    assertThat(bench.count(BRICKS)).isEqualTo(32);
  }

  private void rock(int z) {
    for (int x = -3; x <= 3; x++) {
      for (int y = 57; y <= 63; y++) {
        bench.world.set(x, y, z, "stone");
      }
    }
  }

  private void bore(int z) {
    BlockRef origin = bench.world.blockAt(0, 60, z);
    bench.breakBlock(origin);
    origin.setType(Id.minecraft("air"), false);
    bench.advance(6);
  }

  private List<BlockRef> floor(int z) {
    List<BlockRef> cells = new ArrayList<>();
    for (int x = -1; x <= 1; x++) {
      cells.add(bench.world.blockAt(x, 58, z));
    }
    return cells;
  }

  private List<BlockRef> ceiling(int z) {
    List<BlockRef> cells = new ArrayList<>();
    for (int x = -1; x <= 1; x++) {
      cells.add(bench.world.blockAt(x, 62, z));
    }
    return cells;
  }

  private List<BlockRef> walls(int z) {
    List<BlockRef> cells = new ArrayList<>();
    for (int y = 59; y <= 61; y++) {
      cells.add(bench.world.blockAt(-2, y, z));
      cells.add(bench.world.blockAt(2, y, z));
    }
    return cells;
  }
}
