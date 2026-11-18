package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.InventoryOpenEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bukkit's handler salvaged only when the inventory holder was a {@code org.bukkit.block.Container} (or the left half of
 * a double chest): lecterns, jukeboxes, chiseled bookshelves and decorated pots hold items without being containers.
 * The core handler keeps that split through {@code BlockRef#container()}.
 */
class SeaborneDeepSalvagerHolderTest {
  @Test
  void containerHoldersAreSalvaged() {
    BlockRef chest = mock(BlockRef.class);
    BlockRef water = mock(BlockRef.class);
    when(water.type()).thenReturn(Id.minecraft("water"));
    when(chest.relative(any(BlockFace.class))).thenReturn(water);
    when(chest.container()).thenReturn(mock(InventoryRef.class));

    open(chest);

    verify(chest).blockEntityData();
  }

  @Test
  void blockInventoryHoldersThatAreNotContainersAreSkipped() {
    BlockRef lectern = mock(BlockRef.class);
    when(lectern.blockInventory()).thenReturn(mock(InventoryRef.class));

    open(lectern);

    verify(lectern, never()).relative(any(BlockFace.class));
    verify(lectern, never()).blockEntityData();
  }

  @Test
  void inventoriesWithoutAHolderBlockAreSkipped() {
    SeaborneDeepSalvager adaptation = adaptation();
    PlayerRef player = mock(PlayerRef.class);

    adaptation.on(new InventoryOpenEvent(player, InventoryKind.OTHER, mock(InventoryRef.class), null, null));

    verify(player, never()).isInWater();
  }

  private static void open(BlockRef block) {
    PlayerRef player = mock(PlayerRef.class);
    when(player.isInWater()).thenReturn(true);
    adaptation().on(new InventoryOpenEvent(player, InventoryKind.CHEST, mock(InventoryRef.class), block, null));
  }

  private static SeaborneDeepSalvager adaptation() {
    return mock(SeaborneDeepSalvager.class, invocation -> switch (invocation.getMethod().getName()) {
      case "getActiveLevel" -> 4;
      case "withAdaptedPlayer" -> {
        ((Runnable) invocation.getArguments()[invocation.getArguments().length - 1]).run();
        yield null;
      }
      default -> invocation.callRealMethod();
    });
  }
}
