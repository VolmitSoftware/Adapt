package art.arcane.adapt.api.xp;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.data.unit.PlacementStamp;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
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

class XpProvenanceTest extends AdaptTestBase {
  private static final int TWO_DAYS_SECONDS = 172_800;
  private static final int TEN_SECONDS = 10;

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
  void blockGrowthProvenanceRunsAfterTheGrowthIsCommitted() throws Exception {
    assertCommittedMovementHandler(BlockGrowEvent.class);
  }

  @Test
  void aBreakForgetsAPlacementWhateverItsAgeAndMarksOnlyRecentPlacementsForReplaceDenial() {
    Block old = block(Material.STONE, 1, 64, 0);
    Block recent = block(Material.STONE, 2, 64, 0);
    Block natural = block(Material.STONE, 3, 64, 0);
    try (PlacementStore placements = new PlacementStore()) {
      placements.put(old, new PlacementStamp(nowSeconds() - TWO_DAYS_SECONDS, 0, 7));
      placements.put(recent, new PlacementStamp(nowSeconds() - TEN_SECONDS, 0, 0));
      XpProvenanceListener listener = new XpProvenanceListener();

      listener.on(new BlockBreakEvent(old, mock(Player.class)));
      listener.on(new BlockBreakEvent(recent, mock(Player.class)));
      listener.on(new BlockBreakEvent(natural, mock(Player.class)));

      assertThat(XpProvenance.wasPlaced(old)).isFalse();
      assertThat(placements.get(old).getBrokenAt()).isZero();
      assertThat(placements.get(old).getBonemealedAt()).isEqualTo(7);
      assertThat(XpProvenance.wasPlaced(recent)).isFalse();
      assertThat(placements.get(recent).getBrokenAt()).isPositive();
      assertThat(placements.get(natural)).isNull();
    }
  }

  @Test
  void growingIntoANewBlockForgetsAPlacementButAgeingInPlaceKeepsIt() {
    Block torchflower = block(Material.TORCHFLOWER_CROP, 1, 64, 0);
    Block fruitSpot = block(Material.AIR, 2, 64, 0);
    Block caneTop = block(Material.AIR, 3, 65, 0);
    Block wheat = block(Material.WHEAT, 4, 64, 0);
    try (PlacementStore placements = new PlacementStore()) {
      for (Block placed : List.of(torchflower, fruitSpot, caneTop, wheat)) {
        placements.put(placed, new PlacementStamp(nowSeconds() - TWO_DAYS_SECONDS, 0, 0));
      }
      XpProvenanceListener listener = new XpProvenanceListener();

      listener.on(new BlockGrowEvent(torchflower, state(Material.TORCHFLOWER)));
      listener.on(new BlockGrowEvent(fruitSpot, state(Material.PUMPKIN)));
      listener.on(new BlockGrowEvent(caneTop, state(Material.SUGAR_CANE)));
      listener.on(new BlockGrowEvent(wheat, state(Material.WHEAT)));

      assertThat(XpProvenance.wasPlaced(torchflower)).isFalse();
      assertThat(XpProvenance.wasPlaced(fruitSpot)).isFalse();
      assertThat(XpProvenance.wasPlaced(caneTop)).isFalse();
      assertThat(XpProvenance.wasPlaced(wheat)).isTrue();
    }
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
    assertThat(XpProvenance.resetsOnGrowth(Material.NETHER_WART_BLOCK)).isTrue();
    assertThat(XpProvenance.resetsOnGrowth(Material.WARPED_WART_BLOCK)).isTrue();
    assertThat(XpProvenance.resetsOnGrowth(Material.SHROOMLIGHT)).isTrue();
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
      provenance.when(() -> XpProvenance.forgetPlacement(any(Block.class))).thenReturn(true);

      new XpProvenanceListener().on(event);

      provenance.verify(() -> XpProvenance.forgetPlacement(log.getBlock()));
      provenance.verify(() -> XpProvenance.forgetPlacement(leaves.getBlock()));
      provenance.verify(() -> XpProvenance.forgetPlacement(sapling.getBlock()));
      provenance.verify(() -> XpProvenance.forgetPlacement(podzol.getBlock()), never());
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

  private static Block block(Material type, int x, int y, int z) {
    Block block = mock(Block.class);
    when(block.getType()).thenReturn(type);
    when(block.getX()).thenReturn(x);
    when(block.getY()).thenReturn(y);
    when(block.getZ()).thenReturn(z);
    return block;
  }

  private static BlockState state(Material type) {
    BlockState state = mock(BlockState.class);
    when(state.getType()).thenReturn(type);
    return state;
  }

  private static int nowSeconds() {
    return (int) (System.currentTimeMillis() / 1000L);
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
