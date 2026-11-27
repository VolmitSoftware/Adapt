package art.arcane.adapt.content.golem.item;

import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DormantGolemPlacementTest {
  private final List<BlockPlaceEvent> awakened = new ArrayList<>();
  private final DormantGolemPlacement placement = new DormantGolemPlacement(awakened::add);

  @Test
  void aDormantGolemPlacementIsAlwaysCancelledAndHandedToTheAwakener() {
    BlockPlaceEvent snow = place(GolemItemStacks.tagged(Id.minecraft("snow_block"), "dormant_golem", null));
    BlockPlaceEvent copper = place(GolemItemStacks.tagged(Id.minecraft("weathered_copper"), "dormant_golem", null));

    placement.on(snow);
    placement.on(copper);

    assertThat(snow.isCancelled()).isTrue();
    assertThat(copper.isCancelled()).isTrue();
    assertThat(awakened).containsExactly(snow, copper);
  }

  @Test
  void plainBlocksAndOtherGolemItemsAreLeftAlone() {
    ItemRef dirt = GolemItemStacks.plain(Id.minecraft("dirt"));
    BlockPlaceEvent plainDirt = place(dirt);
    BlockPlaceEvent plainIron = place(GolemItemStacks.plain(Id.minecraft("iron_block")));
    BlockPlaceEvent node = place(GolemItemStacks.tagged(Id.minecraft("iron_block"), "node", null));

    placement.on(plainDirt);
    placement.on(plainIron);
    placement.on(node);

    assertThat(plainDirt.isCancelled()).isFalse();
    assertThat(plainIron.isCancelled()).isFalse();
    assertThat(node.isCancelled()).isFalse();
    assertThat(awakened).isEmpty();
  }

  @Test
  void aFailingAwakeningStillNeverSetsTheBlock() {
    DormantGolemPlacement failing = new DormantGolemPlacement(event -> {
      throw new IllegalStateException("awakening failed");
    });
    BlockPlaceEvent iron = place(GolemItemStacks.tagged(Id.minecraft("iron_block"), "dormant_golem", null));

    assertThatThrownBy(() -> failing.on(iron)).hasMessage("awakening failed");
    assertThat(iron.isCancelled()).isTrue();
  }

  @Test
  void itListensAtHighestAfterProtectionHasRuled() throws NoSuchMethodException {
    Method handler = DormantGolemPlacement.class.getMethod("on", BlockPlaceEvent.class);
    Handle annotation = handler.getAnnotation(Handle.class);

    assertThat(annotation.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(annotation.ignoreCancelled()).isTrue();
  }

  @Test
  void theGuardSafetyNetRunsAtMonitorOnUncancelledPlacements() throws NoSuchMethodException {
    Method handler = GolemItemGuard.class.getMethod("placed", BlockPlaceEvent.class);
    Handle annotation = handler.getAnnotation(Handle.class);

    assertThat(annotation.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(annotation.ignoreCancelled()).isTrue();
  }

  private static BlockPlaceEvent place(ItemRef item) {
    return new BlockPlaceEvent(mock(PlayerRef.class), mock(BlockRef.class), mock(BlockStateRef.class), mock(BlockRef.class), item, Hand.MAIN_HAND);
  }
}
