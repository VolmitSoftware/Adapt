package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.protection.ProtectionProbePort;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class RiftAccessProtectionTest {
  @AfterEach
  void removeProbe() {
    AdaptPorts.uninstall(ProtectionProbePort.class);
  }

  @Test
  void remoteOpenUsesAStandardBlockInteractionAndHonorsItsDenial() {
    PlayerRef player = mock(PlayerRef.class);
    BlockRef block = mock(BlockRef.class);
    AtomicReference<PlayerRef> probedPlayer = new AtomicReference<>();
    AtomicReference<BlockRef> probedBlock = new AtomicReference<>();
    stubBlockActionContext(player, block);
    AdaptPorts.install(ProtectionProbePort.class, new ProtectionProbePort() {
      @Override
      public boolean probeUse(PlayerRef actor, BlockRef target) {
        probedPlayer.set(actor);
        probedBlock.set(target);
        return false;
      }
    });

    boolean allowed = ProtectionProbes.attemptContainerOpen(player, List.of(block));

    assertThat(allowed).isFalse();
    assertThat(probedPlayer.get()).isSameAs(player);
    assertThat(probedBlock.get()).isSameAs(block);
  }

  @Test
  void containerOpenHonorsStandardEventCancellation() {
    PlayerRef player = mock(PlayerRef.class);
    BlockRef block = mock(BlockRef.class);
    stubBlockActionContext(player, block);
    AdaptPorts.install(ProtectionProbePort.class, new ProtectionProbePort() {
      @Override
      public boolean probeUse(PlayerRef actor, BlockRef target) {
        return false;
      }
    });

    boolean allowed = ProtectionProbes.attemptContainerOpen(player, List.of(block));

    assertThat(allowed).isFalse();
  }

  @Test
  void containerOpenAllowsDefaultAndExplicitlyAllowedBlockUse() {
    for (String result : List.of("DEFAULT", "ALLOW")) {
      PlayerRef player = mock(PlayerRef.class);
      BlockRef block = mock(BlockRef.class);
      stubBlockActionContext(player, block);
      AdaptPorts.install(ProtectionProbePort.class, new ProtectionProbePort() {
        @Override
        public boolean probeUse(PlayerRef actor, BlockRef target) {
          return true;
        }
      });

      boolean allowed = ProtectionProbes.attemptContainerOpen(player, List.of(block));

      assertThat(allowed).as(result).isTrue();
    }
  }

  @Test
  void eitherDoubleChestHalfCanDenyContainerOpen() {
    PlayerRef player = mock(PlayerRef.class);
    BlockRef first = mock(BlockRef.class);
    BlockRef second = mock(BlockRef.class);
    List<BlockRef> dispatched = new ArrayList<>();
    stubBlockActionContext(player, first, second);
    AdaptPorts.install(ProtectionProbePort.class, new ProtectionProbePort() {
      @Override
      public boolean probeUse(PlayerRef actor, BlockRef target) {
        dispatched.add(target);
        return target != second;
      }
    });

    boolean allowed = ProtectionProbes.attemptContainerOpen(player, List.of(first, second));

    assertThat(allowed).isFalse();
    assertThat(dispatched).containsExactly(first, second);
  }

  @Test
  void doubleChestAccessIncludesBothPhysicalBlocks() {
    BlockRef boundBlock = mock(BlockRef.class);
    BlockRef otherBlock = mock(BlockRef.class);
    when(boundBlock.state()).thenReturn(mock(BlockStateRef.class));
    when(boundBlock.containerHalves()).thenReturn(List.of(boundBlock, otherBlock));

    assertThat(ProtectionProbes.containerBlocks(boundBlock, true))
        .containsExactly(boundBlock, otherBlock);
  }

  @Test
  void doubleChestTargetsIndexBothBlocksAndCrossChunkAnchors() {
    UUID worldId = UUID.randomUUID();
    WorldRef world = mock(WorldRef.class);
    BlockRef first = mock(BlockRef.class);
    BlockRef second = mock(BlockRef.class);
    Loc firstLocation = new Loc(world, 15, 70, 8);
    Loc secondLocation = new Loc(world, 16, 70, 8);
    when(world.uid()).thenReturn(worldId);
    when(first.location()).thenReturn(firstLocation);
    when(second.location()).thenReturn(secondLocation);

    RiftAccess.ContainerTargets targets = RiftAccess.containerTargets(List.of(first, second));

    RiftAccessViewRegistry.BlockKey firstBlock = new RiftAccessViewRegistry.BlockKey(worldId, 15, 70, 8);
    RiftAccessViewRegistry.BlockKey secondBlock = new RiftAccessViewRegistry.BlockKey(worldId, 16, 70, 8);
    RiftAccessViewRegistry.ChunkKey firstChunk = new RiftAccessViewRegistry.ChunkKey(worldId, 0, 0);
    RiftAccessViewRegistry.ChunkKey secondChunk = new RiftAccessViewRegistry.ChunkKey(worldId, 1, 0);
    assertThat(targets.blockKeys()).containsExactlyInAnyOrder(firstBlock, secondBlock);
    assertThat(targets.chunkAnchors()).containsOnlyKeys(firstChunk, secondChunk);
    assertThat(targets.chunkAnchors().get(firstChunk)).isEqualTo(firstLocation);
    assertThat(targets.chunkAnchors().get(secondChunk)).isEqualTo(secondLocation);
  }

  @Test
  void doubleChestTargetsDeduplicateASharedChunkTicket() {
    UUID worldId = UUID.randomUUID();
    WorldRef world = mock(WorldRef.class);
    BlockRef first = mock(BlockRef.class);
    BlockRef second = mock(BlockRef.class);
    when(world.uid()).thenReturn(worldId);
    when(first.location()).thenReturn(new Loc(world, 4, 70, 8));
    when(second.location()).thenReturn(new Loc(world, 5, 70, 8));

    RiftAccess.ContainerTargets targets = RiftAccess.containerTargets(List.of(first, second));

    assertThat(targets.blockKeys()).hasSize(2);
    assertThat(targets.chunkAnchors())
        .containsOnlyKeys(new RiftAccessViewRegistry.ChunkKey(worldId, 0, 0));
  }

  @Test
  void doubleChestOwnershipChecksBothPhysicalHalves() {
    WorldRef world = mock(WorldRef.class);
    BlockRef first = mock(BlockRef.class);
    BlockRef second = mock(BlockRef.class);
    Loc firstLocation = new Loc(world, 1, 70, 1);
    Loc secondLocation = new Loc(world, 2, 70, 1);
    when(first.location()).thenReturn(firstLocation);
    when(second.location()).thenReturn(secondLocation);

    try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class)) {
      scheduling.when(() -> Tasks.isOwnedByCurrentRegion(firstLocation)).thenReturn(true);
      scheduling.when(() -> Tasks.isOwnedByCurrentRegion(secondLocation)).thenReturn(false);

      assertThat(RiftAccess.areContainerRegionsOwned(List.of(first, second))).isFalse();
      scheduling.verify(() -> Tasks.isOwnedByCurrentRegion(firstLocation));
      scheduling.verify(() -> Tasks.isOwnedByCurrentRegion(secondLocation));
    }
  }

  private static void stubBlockActionContext(PlayerRef player, BlockRef... blocks) {
    WorldRef world = mock(WorldRef.class);
    when(player.isOnline()).thenReturn(true);
    when(player.world()).thenReturn(world);
    for (BlockRef block : blocks) {
      when(block.world()).thenReturn(world);
    }
  }
}
