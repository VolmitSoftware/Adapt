package art.arcane.adapt.content.adaptation.seaborrne;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

class SeaborneDeepSalvagerLootTest {
    @Test
    void salvageMarkerPreservesAddedTreasureAndPreventsASecondReward() throws Exception {
        AtomicInteger contents = new AtomicInteger();
        AtomicBoolean salvaged = new AtomicBoolean();
        NamespacedKey key = NamespacedKey.fromString("adapt:seaborne_salvaged");
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(data.has(key, PersistentDataType.BYTE)).thenAnswer(invocation -> salvaged.get());
        doAnswer(invocation -> { salvaged.set(true); return null; }).when(data).set(eq(key), eq(PersistentDataType.BYTE), eq((byte) 1));
        TileState snapshot = mock(TileState.class);
        TileState live = mock(TileState.class);
        when(snapshot.getPersistentDataContainer()).thenReturn(data);
        when(live.getPersistentDataContainer()).thenReturn(data);
        when(snapshot.update()).thenAnswer(invocation -> { contents.set(0); return true; });
        when(live.update()).thenReturn(true);
        Block block = mock(Block.class);
        Block water = mock(Block.class);
        when(water.getType()).thenReturn(Material.WATER);
        when(block.getRelative(any(BlockFace.class))).thenReturn(water);
        when(block.getState()).thenReturn(snapshot);
        when(block.getState(false)).thenReturn(live);
        Player player = mock(Player.class);
        when(player.isInWater()).thenReturn(true);
        Inventory inventory = mock(Inventory.class);
        when(inventory.addItem(any(ItemStack[].class))).thenAnswer(invocation -> { contents.incrementAndGet(); return new HashMap<Integer, ItemStack>(); });
        SeaborneDeepSalvager.Config config = new SeaborneDeepSalvager.Config();
        SeaborneDeepSalvager adaptation = mock(SeaborneDeepSalvager.class, invocation -> switch (invocation.getMethod().getName()) {
            case "getActiveLevel" -> 4;
            case "getLevelPercent" -> 1D;
            case "getConfig" -> config;
            case "xp", "addStat", "playSalvageEffects" -> null;
            default -> invocation.callRealMethod();
        });
        Field marker = SeaborneDeepSalvager.class.getDeclaredField("salvagedKey");
        marker.setAccessible(true);
        marker.set(adaptation, key);
        Method salvage = SeaborneDeepSalvager.class.getDeclaredMethod("trySalvage", Player.class, Inventory.class, Block.class);
        salvage.setAccessible(true);
        try (MockedConstruction<ItemStack> items = mockConstruction(ItemStack.class)) {
            salvage.invoke(adaptation, player, inventory, block);
            assertThat(contents.get()).isEqualTo(4);
            assertThat(salvaged.get()).isTrue();
            salvage.invoke(adaptation, player, inventory, block);
            assertThat(contents.get()).isEqualTo(4);
            assertThat(items.constructed()).hasSize(4);
        }
    }
}
