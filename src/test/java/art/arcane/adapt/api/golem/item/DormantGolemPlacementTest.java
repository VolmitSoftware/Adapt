package art.arcane.adapt.api.golem.item;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
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
    BlockPlaceEvent snow = place(GolemItemStacks.tagged(Material.SNOW_BLOCK, "dormant_golem", null));
    BlockPlaceEvent copper = place(GolemItemStacks.tagged(Material.WEATHERED_COPPER, "dormant_golem", null));

    placement.on(snow);
    placement.on(copper);

    assertThat(snow.isCancelled()).isTrue();
    assertThat(copper.isCancelled()).isTrue();
    assertThat(awakened).containsExactly(snow, copper);
  }

  @Test
  void plainBlocksAndOtherGolemItemsAreLeftAlone() {
    ItemStack dirt = GolemItemStacks.plain(Material.DIRT);
    BlockPlaceEvent plainDirt = place(dirt);
    BlockPlaceEvent plainIron = place(GolemItemStacks.plain(Material.IRON_BLOCK));
    BlockPlaceEvent node = place(GolemItemStacks.tagged(Material.IRON_BLOCK, "node", null));

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
    BlockPlaceEvent iron = place(GolemItemStacks.tagged(Material.IRON_BLOCK, "dormant_golem", null));

    assertThatThrownBy(() -> failing.on(iron)).hasMessage("awakening failed");
    assertThat(iron.isCancelled()).isTrue();
  }

  @Test
  void itListensAtHighestAfterProtectionHasRuled() throws NoSuchMethodException {
    Method handler = DormantGolemPlacement.class.getMethod("on", BlockPlaceEvent.class);
    EventHandler annotation = handler.getAnnotation(EventHandler.class);

    assertThat(annotation.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(annotation.ignoreCancelled()).isTrue();
  }

  @Test
  void theGuardSafetyNetRunsAtMonitorOnUncancelledPlacements() throws NoSuchMethodException {
    Method handler = GolemItemGuard.class.getMethod("placed", BlockPlaceEvent.class);
    EventHandler annotation = handler.getAnnotation(EventHandler.class);

    assertThat(annotation.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(annotation.ignoreCancelled()).isTrue();
  }

  private static BlockPlaceEvent place(ItemStack item) {
    return new BlockPlaceEvent(mock(Block.class), mock(BlockState.class), mock(Block.class), item, mock(Player.class), true, EquipmentSlot.HAND);
  }
}
