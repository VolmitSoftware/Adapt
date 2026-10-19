package art.arcane.adapt.api.xp;

import art.arcane.adapt.api.data.unit.PlacementStamp;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class XpProvenanceTest {
  @Test
  void permanentPlacementEvidenceDoesNotExpireWithTheXpTtl() {
    assertThat(XpProvenance.hasPermanentPlacementRecord(null)).isFalse();
    assertThat(XpProvenance.hasPermanentPlacementRecord(new PlacementStamp(0, 0, 0))).isFalse();
    assertThat(XpProvenance.hasPermanentPlacementRecord(new PlacementStamp(1, 0, 0))).isTrue();
    assertThat(XpProvenance.hasPermanentPlacementRecord(new PlacementStamp(0, 1, 0))).isTrue();
  }

  @Test
  void dragonEggMovementProvenanceRunsAfterCommittedMovement() throws Exception {
    assertCommittedMovementHandler(BlockFromToEvent.class);
    assertCommittedMovementHandler(EntityChangeBlockEvent.class);
  }

  @Test
  void treeGrowthProvenanceRunsAfterTheGrowthIsCommitted() throws Exception {
    assertCommittedMovementHandler(StructureGrowEvent.class);
  }

  @Test
  void aPlacementRecordLastsWhateverItsAgeAndIgnoresBreakMarks() {
    assertThat(XpProvenance.hasPlacement(null)).isFalse();
    assertThat(XpProvenance.hasPlacement(new PlacementStamp(0, 5, 7))).isFalse();
    assertThat(XpProvenance.hasPlacement(new PlacementStamp(1, 0, 0))).isTrue();
  }

  @Test
  void onlyTreePartsAndSaplingsResetOnGrowth() {
    assertThat(XpProvenance.resetsOnGrowth(Material.OAK_LOG)).isTrue();
    assertThat(XpProvenance.resetsOnGrowth(Material.OAK_WOOD)).isTrue();
    assertThat(XpProvenance.resetsOnGrowth(Material.CRIMSON_STEM)).isTrue();
    assertThat(XpProvenance.resetsOnGrowth(Material.MUSHROOM_STEM)).isTrue();
    assertThat(XpProvenance.resetsOnGrowth(Material.WARPED_HYPHAE)).isTrue();
    assertThat(XpProvenance.resetsOnGrowth(Material.OAK_LEAVES)).isTrue();
    assertThat(XpProvenance.resetsOnGrowth(Material.OAK_SAPLING)).isTrue();
    assertThat(XpProvenance.resetsOnGrowth(Material.MANGROVE_PROPAGULE)).isTrue();
    assertThat(XpProvenance.resetsOnGrowth(Material.PODZOL)).isFalse();
    assertThat(XpProvenance.resetsOnGrowth(Material.DIRT)).isFalse();
    assertThat(XpProvenance.resetsOnGrowth(Material.ROOTED_DIRT)).isFalse();
    assertThat(XpProvenance.resetsOnGrowth(Material.MOSS_CARPET)).isFalse();
  }

  @Test
  void treeGrowthResetsTreePartsAndTheSaplingSpotButNeverTheGround() {
    World world = mock(World.class);
    BlockState log = grown(Material.OAK_LOG, 0, 65, 0);
    BlockState leaves = grown(Material.OAK_LEAVES, 1, 66, 0);
    BlockState podzol = grown(Material.PODZOL, 0, 63, 0);
    BlockState sapling = grown(Material.ROOTED_DIRT, 4, 64, 4);
    StructureGrowEvent event = new StructureGrowEvent(new Location(world, 4, 64, 4), TreeType.TREE, false, null,
        List.of(log, leaves, podzol, sapling));

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.recordGrowth(any(Block.class))).then(invocation -> null);

      new XpProvenanceListener().on(event);

      provenance.verify(() -> XpProvenance.recordGrowth(log.getBlock()));
      provenance.verify(() -> XpProvenance.recordGrowth(leaves.getBlock()));
      provenance.verify(() -> XpProvenance.recordGrowth(sapling.getBlock()));
      provenance.verify(() -> XpProvenance.recordGrowth(podzol.getBlock()), never());
    }
  }

  @Test
  void grownBlocksForgetTheirPlacementButKeepBreakAndBonemealMarks() {
    assertThat(XpProvenance.grown(null)).isNull();
    assertThat(XpProvenance.grown(new PlacementStamp(0, 5, 7))).isNull();

    PlacementStamp grown = XpProvenance.grown(new PlacementStamp(9, 5, 7));

    assertThat(grown.getPlacedAt()).isZero();
    assertThat(grown.getBrokenAt()).isEqualTo(5);
    assertThat(grown.getBonemealedAt()).isEqualTo(7);
  }

  private static BlockState grown(Material type, int x, int y, int z) {
    BlockState state = mock(BlockState.class);
    Block block = mock(Block.class);
    when(state.getType()).thenReturn(type);
    when(state.getBlock()).thenReturn(block);
    when(state.getX()).thenReturn(x);
    when(state.getY()).thenReturn(y);
    when(state.getZ()).thenReturn(z);
    return state;
  }

  private static void assertCommittedMovementHandler(Class<?> eventType) throws Exception {
    Method handler = XpProvenanceListener.class.getDeclaredMethod("on", eventType);
    EventHandler annotation = handler.getAnnotation(EventHandler.class);

    assertThat(annotation.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(annotation.ignoreCancelled()).isTrue();
  }
}
