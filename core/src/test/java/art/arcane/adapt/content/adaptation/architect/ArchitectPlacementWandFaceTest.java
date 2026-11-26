package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillArchitect;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.testkit.FakeBlockState;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

/** The original Builders Wand face: real paid placements of the single-item state, for a player who never mutated. */
class ArchitectPlacementWandFaceTest {
  private static final Id SLAB = Id.minecraft("oak_slab");
  private static final int Y = 44;

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakeWorld world;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private ArchitectPlacement placement;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    world = platform.world();
    player = platform.player("Builder", 0.5D, Y + 2D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillArchitect.class));
    profile = adapt.join(player);
    SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
    placement = (ArchitectPlacement) registry.getAdaptation(ArchitectPlacement.ID);
    profile.getData().getSkillLine("architect").setAdaptation(placement, 1);
    player.setSneaking(true);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aDoubleSlabFaceLaysSingleSlabsForOneItemEachThroughRealPlaceEvents() {
    BlockStateRef doubleSlab = new FakeBlockState(SLAB, Map.of("type", "double"));
    for (int x = -1; x <= 1; x++) {
      for (int z = -1; z <= 1; z++) {
        world.set(new BlockPos(x, Y, z), doubleSlab);
      }
    }
    BlockRef source = world.blockAt(0, Y, 0);
    placement.runPlayerViewport(BlockFace.UP, source, SLAB, player);
    world.set(new BlockPos(0, Y + 1, 0), new FakeBlockState(SLAB, Map.of("type", "bottom")));
    BlockRef placed = world.blockAt(0, Y + 1, 0);
    player.holding(new FakeItem(SLAB, 9));

    try (MockedStatic<ProtectionProbes> probes = mockStatic(ProtectionProbes.class, CALLS_REAL_METHODS)) {
      adapt.post(new BlockPlaceEvent(player, placed, FakeBlockState.of("air"), source, player.inventory().mainHand(),
          Hand.MAIN_HAND));
      platform.scheduler().advance(3);
      probes.verify(() -> ProtectionProbes.callPlace(any(PlayerRef.class), any(BlockRef.class), any(BlockStateRef.class),
          any(BlockRef.class), any(ItemRef.class)), times(8));
    }

    for (BlockRef block : layer(Y + 1)) {
      assertThat(block.type()).isEqualTo(SLAB);
      assertThat(block.state().property("type")).isEqualTo("bottom");
    }
    assertThat(count(SLAB)).isEqualTo(1);
    assertThat(XpPaid.by(profile, placement)).isZero();
  }

  @Test
  void theFaceStopsWhenTheItemsRunOut() {
    for (int x = -1; x <= 1; x++) {
      for (int z = -1; z <= 1; z++) {
        world.set(x, Y, z, "stone");
      }
    }
    BlockRef source = world.blockAt(0, Y, 0);
    placement.runPlayerViewport(BlockFace.UP, source, Id.minecraft("stone"), player);
    BlockRef placed = world.set(0, Y + 1, 0, "stone").blockAt(0, Y + 1, 0);
    player.holding(new FakeItem(Id.minecraft("stone"), 9));
    player.inventory().mainHand().setAmount(9);

    adapt.post(new BlockPlaceEvent(player, placed, FakeBlockState.of("air"), source, player.inventory().mainHand(),
        Hand.MAIN_HAND));
    player.inventory().mainHand().setAmount(3);
    platform.scheduler().advance(3);

    int laid = 0;
    for (BlockRef block : layer(Y + 1)) {
      laid += block.isAir() ? 0 : 1;
    }
    assertThat(laid).isEqualTo(4);
    assertThat(count(Id.minecraft("stone"))).isZero();
  }

  private List<BlockRef> layer(int y) {
    List<BlockRef> blocks = new java.util.ArrayList<>();
    for (int x = -1; x <= 1; x++) {
      for (int z = -1; z <= 1; z++) {
        blocks.add(world.blockAt(x, y, z));
      }
    }
    return blocks;
  }

  private int count(Id type) {
    int count = 0;
    for (ItemRef item : player.inventory().contents()) {
      if (item != null && !item.isEmpty() && item.type().equals(type)) {
        count += item.amount();
      }
    }
    return count;
  }
}
