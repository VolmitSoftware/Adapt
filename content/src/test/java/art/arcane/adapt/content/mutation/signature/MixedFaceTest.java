package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.engine.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.excavation.upgraded.Landshaper;
import art.arcane.adapt.content.adaptation.pickaxe.upgraded.Earthshaker;
import art.arcane.adapt.content.skill.SkillExcavation;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/** Pickaxes and Excavation, either leading: one swing clears stone and soft ground, each with its own tool. */
class MixedFaceTest {
  private static final Id AIR = Id.minecraft("air");
  private static final Id DIRT = Id.minecraft("dirt");
  private static final Id STONE = Id.minecraft("stone");

  @TempDir
  Path data;

  private SignatureBench bench;

  @BeforeEach
  void start() {
    bench = new SignatureBench(data, List.of(SkillPickaxes.class, SkillExcavation.class));
    bench.player.inventory().setItem(0, FakeItem.tool("diamond_pickaxe", 1561));
    bench.player.inventory().setItem(1, FakeItem.tool("iron_shovel", 250));
    face(2);
    face(3);
  }

  @AfterEach
  void stop() {
    bench.close();
  }

  @Test
  void aBoreAlsoDigsTheSoftGroundInItsFaceWithTheHotbarShovel() {
    bench.signature("pickaxe", "excavation", 1);
    bench.upgrade("pickaxe", Earthshaker.class, 1);

    swing(STONE);

    assertThat(cleared(2)).isEqualTo(9);
    assertThat(cleared(3)).isZero();
    assertThat(bench.player.inventory().heldSlot()).isZero();
  }

  @Test
  void eitherSkillMayLeadAndADigTakesTheStoneWithTheHotbarPickaxe() {
    bench.signature("excavation", "pickaxe", 1);
    bench.upgrade("excavation", Landshaper.class, 1);
    bench.player.inventory().setHeldSlot(1);

    swing(DIRT);

    for (BlockRef block : List.of(bench.world.blockAt(-1, 60, 2), bench.world.blockAt(0, 59, 2))) {
      assertThat(block.type()).isEqualTo(AIR);
    }
    assertThat(bench.player.inventory().heldSlot()).isEqualTo(1);
  }

  @Test
  void nothingMoreBreaksWithoutTheLearnedSignature() {
    bench.kit.mutate("pickaxe", "excavation");
    bench.upgrade("pickaxe", Earthshaker.class, 1);

    swing(STONE);

    assertThat(bench.world.blockAt(1, 59, 2).type()).isEqualTo(DIRT);
  }

  @Test
  void eachLevelReachesOneFaceDeeperAndTheCooldownHolds() {
    bench.signature("pickaxe", "excavation", 2);
    bench.upgrade("pickaxe", Earthshaker.class, 1);
    MixedFace mixed = bench.handler("pickaxe", "excavation", MixedFace.class);
    mixed.getConfig().cooldownMillis = 60_000L;

    swing(STONE);
    face(2);
    swing(STONE);

    assertThat(cleared(3)).isEqualTo(9);
    assertThat(bench.world.blockAt(1, 59, 2).type()).isEqualTo(DIRT);
    assertThat(mixed.depth(1)).isEqualTo(1);
    assertThat(mixed.depth(3)).isEqualTo(3);
  }

  @Test
  void aPlacedBlockInTheFaceIsLeftAndAPlacedFirstBlockSetsNothingOff() {
    bench.signature("pickaxe", "excavation", 3);
    bench.upgrade("pickaxe", Earthshaker.class, 1);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(argThat((BlockRef block) -> block != null
          && block.x() == 1 && block.y() == 59 && block.z() == 2))).thenReturn(true);
      swing(STONE);
    }
    face(2);
    face(3);
    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(argThat((BlockRef block) -> block != null
          && block.x() == 0 && block.y() == 60 && block.z() == 2))).thenReturn(true);
      swing(STONE);
    }

    assertThat(bench.world.blockAt(1, 59, 2).type()).isEqualTo(DIRT);
    assertThat(bench.world.blockAt(-1, 61, 2).type()).isEqualTo(DIRT);
    assertThat(cleared(3)).isZero();
  }

  @Test
  void theFaceBreaksAFewBlocksATickAndStopsWhenTheShovelIsPutAway() {
    bench.signature("pickaxe", "excavation", 1);
    bench.upgrade("pickaxe", Earthshaker.class, 1);
    MixedFace mixed = bench.handler("pickaxe", "excavation", MixedFace.class);
    mixed.getConfig().blocksPerTick = 1;
    BlockRef block = bench.world.blockAt(0, 60, 2);
    bench.world.set(0, 60, 2, "stone");

    bench.breakBlock(block);
    block.setType(AIR, false);
    bench.advance(3);
    int early = cleared(2);
    bench.player.inventory().setItem(1, FakeItem.of("stick"));
    bench.advance(20);

    assertThat(early).isLessThan(9);
    assertThat(cleared(2)).isEqualTo(early);
  }

  /** Stone and dirt in a checkerboard across the 3 by 3 face at {@code z}. */
  private void face(int z) {
    for (int x = -1; x <= 1; x++) {
      for (int y = 59; y <= 61; y++) {
        bench.world.set(x, y, z, ((x + y) & 1) == 0 ? "dirt" : "stone");
      }
    }
  }

  private void swing(Id origin) {
    BlockRef block = bench.world.blockAt(0, 60, 2);
    bench.world.set(0, 60, 2, origin.path());
    bench.breakBlock(block);
    block.setType(AIR, false);
    bench.advance(6);
  }

  private int cleared(int z) {
    int cleared = 0;
    for (int x = -1; x <= 1; x++) {
      for (int y = 59; y <= 61; y++) {
        cleared += bench.world.blockAt(x, y, z).isAir() ? 1 : 0;
      }
    }
    return cleared;
  }
}
