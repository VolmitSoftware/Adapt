package art.arcane.adapt.content.golem;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.LidRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveLidSpaceTest {
  private final WorldRef world = mock(WorldRef.class);
  private MockedStatic<AdaptApi> tasks;
  private final AdaptScheduler adaptScheduler = mock(AdaptScheduler.class);

  @BeforeEach
  void setUp() {
    lenient().when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
    tasks = mockStatic(AdaptApi.class, CALLS_REAL_METHODS);
    tasks.when(AdaptApi::scheduler).thenReturn(adaptScheduler);
    when(adaptScheduler.owns(any(Loc.class))).thenReturn(true);
  }

  @AfterEach
  void tearDown() {
    tasks.close();
  }

  @Test
  void everyLiddedContainerKindIsItsOwnHalf() {
    BlockRef chest = lidded(3, 64, 3);
    BlockRef barrel = lidded(5, 64, 3);
    BlockRef shulker = lidded(7, 64, 3);

    assertThat(LiveLidSpace.INSTANCE.halves(chest)).containsExactly(chest);
    assertThat(LiveLidSpace.INSTANCE.halves(barrel)).containsExactly(barrel);
    assertThat(LiveLidSpace.INSTANCE.halves(shulker)).containsExactly(shulker);
  }

  @Test
  void aContainerWithoutALidHasNoHalves() {
    BlockRef hopper = block(3, 64, 3);
    lenient().when(hopper.lid()).thenReturn(Optional.empty());
    lenient().when(hopper.containerHalves()).thenReturn(List.of(hopper));

    assertThat(LiveLidSpace.INSTANCE.halves(hopper)).isEmpty();
    verify(hopper, never()).containerHalves();
  }

  @Test
  void aDoubleChestIsBothOfItsBlocksFromEitherSide() {
    BlockRef clicked = lidded(4, 64, 3);
    BlockRef left = lidded(3, 64, 3);
    BlockRef right = lidded(4, 64, 3);
    lenient().when(clicked.containerHalves()).thenReturn(List.of(left, right));

    assertThat(LiveLidSpace.INSTANCE.halves(clicked)).containsExactly(left, right);
  }

  @Test
  void aDoubleChestWhoseHalvesCannotBeLocatedFallsBackToTheClickedBlock() {
    BlockRef clicked = lidded(4, 64, 3);

    assertThat(LiveLidSpace.INSTANCE.halves(clicked)).containsExactly(clicked);
  }

  @Test
  void aLidIsViewedWhilePlayersHaveTheContainerOpenAndDrivesTheLiveState() {
    BlockRef chest = lidded(3, 64, 3);
    LidRef lid = chest.lid().orElseThrow();
    lenient().when(lid.viewed()).thenReturn(false);
    lenient().when(lid.isOpen()).thenReturn(true);

    assertThat(LiveLidSpace.INSTANCE.lid(chest)).containsSame(lid);
    assertThat(lid.viewed()).isFalse();
    assertThat(lid.isOpen()).isTrue();
    lenient().when(lid.viewed()).thenReturn(true);
    assertThat(lid.viewed()).isTrue();
    lid.open();
    lid.close();
    verify(lid).open();
    verify(lid).close();
  }

  private BlockRef lidded(int x, int y, int z) {
    BlockRef block = block(x, y, z);
    LidRef lid = mock(LidRef.class);
    lenient().when(block.lid()).thenReturn(Optional.of(lid));
    lenient().when(block.containerHalves()).thenReturn(List.of(block));
    return block;
  }

  private BlockRef block(int x, int y, int z) {
    BlockRef block = mock(BlockRef.class);
    lenient().when(block.world()).thenReturn(world);
    lenient().when(block.x()).thenReturn(x);
    lenient().when(block.y()).thenReturn(y);
    lenient().when(block.z()).thenReturn(z);
    lenient().when(block.location()).thenReturn(new Loc(world, x, y, z));
    return block;
  }
}
