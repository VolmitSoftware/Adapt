package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.AdaptPlatform;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeaborneDeepSalvagerLootTest {
    @Test
    void salvageMarkerPreservesAddedTreasureAndPreventsASecondReward() throws Exception {
        AtomicInteger contents = new AtomicInteger();
        AtomicBoolean salvaged = new AtomicBoolean();
        assertThat(SeaborneDeepSalvager.SALVAGED_KEY.id()).isEqualTo(Id.parse("adapt:seaborne_salvaged"));
        DataHolder data = mock(DataHolder.class);
        when(data.has(SeaborneDeepSalvager.SALVAGED_KEY)).thenAnswer(invocation -> salvaged.get());
        doAnswer(invocation -> { salvaged.set(true); return null; }).when(data).set(eq(SeaborneDeepSalvager.SALVAGED_KEY), eq((byte) 1));
        BlockRef block = mock(BlockRef.class);
        BlockRef water = mock(BlockRef.class);
        when(water.type()).thenReturn(Id.minecraft("water"));
        when(block.relative(any(BlockFace.class))).thenReturn(water);
        when(block.blockEntityData()).thenReturn(data);
        PlayerRef player = mock(PlayerRef.class);
        when(player.isInWater()).thenReturn(true);
        InventoryRef inventory = mock(InventoryRef.class);
        when(inventory.addItem(any(ItemRef.class))).thenAnswer(invocation -> { contents.incrementAndGet(); return List.of(); });
        SeaborneDeepSalvager.Config config = new SeaborneDeepSalvager.Config();
        SeaborneDeepSalvager adaptation = mock(SeaborneDeepSalvager.class, invocation -> switch (invocation.getMethod().getName()) {
            case "getActiveLevel" -> 4;
            case "getLevelPercent" -> 1D;
            case "getConfig" -> config;
            case "xp", "addStat", "playSalvageEffects" -> null;
            default -> invocation.callRealMethod();
        });
        Method salvage = SeaborneDeepSalvager.class.getDeclaredMethod("trySalvage", PlayerRef.class, InventoryRef.class, BlockRef.class);
        salvage.setAccessible(true);
        AdaptPlatform platform = mock(AdaptPlatform.class, RETURNS_DEEP_STUBS);
        ItemOps items = platform.host().items();
        when(items.create(any(Id.class), anyInt())).thenAnswer(invocation -> mock(ItemRef.class));
        try (MockedStatic<AdaptApi> api = mockStatic(AdaptApi.class)) {
            api.when(AdaptApi::platform).thenReturn(platform);
            salvage.invoke(adaptation, player, inventory, block);
            assertThat(contents.get()).isEqualTo(4);
            assertThat(salvaged.get()).isTrue();
            salvage.invoke(adaptation, player, inventory, block);
            assertThat(contents.get()).isEqualTo(4);
            verify(items, times(4)).create(any(Id.class), anyInt());
        }
    }
}
