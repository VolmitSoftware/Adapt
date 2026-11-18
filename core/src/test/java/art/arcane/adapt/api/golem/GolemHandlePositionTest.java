package art.arcane.adapt.api.golem;

import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class GolemHandlePositionTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

  @Test
  void trackingAPositionInTheSameChunkKeepsTheSameRecord() {
    WorldRef world = mock(WorldRef.class);
    lenient().when(world.uid()).thenReturn(WORLD);
    MobRef golem = GolemTestEntities.golem(GolemBody.IRON);
    lenient().when(golem.location()).thenReturn(new Loc(world, 3, 64, 3));
    GolemState state = GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, GolemBody.IRON, GolemCoreType.BLANK,
        GolemAnchor.of(WORLD, 3, 64, 3)));
    GolemHandle handle = new GolemHandle(golem, state, new FakeGolemPlatform());

    GolemHandle.ChunkPosition first = handle.trackPosition(new Loc(world, 3, 64, 3));
    GolemHandle.ChunkPosition sameChunk = handle.trackPosition(new Loc(world, 15, 70, 15));
    GolemHandle.ChunkPosition nextChunk = handle.trackPosition(new Loc(world, 16, 64, 3));

    assertThat(sameChunk).isSameAs(first);
    assertThat(nextChunk).isNotSameAs(first);
    assertThat(nextChunk.chunkKey()).isEqualTo(GolemRoster.chunkKey(1, 0));
    assertThat(handle.trackPosition(null)).isSameAs(nextChunk);
  }
}
