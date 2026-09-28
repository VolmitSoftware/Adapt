package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.ability.AbilityDefaultCost;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.mockito.MockedConstruction;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArchitectPlacementConsumptionTest extends AdaptTestBase {
    @ParameterizedTest
    @CsvSource({"9,8,0", "32,8,23", "32,3,28", "32,0,31"})
    void placementPaysForTheTriggerWhenChangingTheStackSuppressesVanillaConsumption(int initial, int allowedExtras, int remaining) throws Exception {
        QuietPlacement adaptation = spy(new QuietPlacement());
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getPlayer()).thenReturn(player);
        Material material = mock(Material.class);
        when(material.isBlock()).thenReturn(true);
        Material air = mock(Material.class);
        when(air.isAir()).thenReturn(true);
        AtomicInteger amount = new AtomicInteger(initial);
        ItemStack hand = mock(ItemStack.class);
        when(hand.getType()).thenReturn(material);
        when(hand.getAmount()).thenAnswer(invocation -> amount.get());
        doAnswer(invocation -> {
            amount.set(invocation.getArgument(0));
            return null;
        }).when(hand).setAmount(anyInt());
        when(event.getItemInHand()).thenReturn(hand);
        Location location = new Location(mock(World.class), 0, 100, 0);
        Map<Block, BlockFace> footprint = new HashMap<>();
        Block original = mock(Block.class);
        when(event.getBlock()).thenReturn(original);
        when(original.getLocation()).thenReturn(location);
        BlockData data = mock(BlockData.class);
        when(data.clone()).thenReturn(data);
        for (int index = 0; index < 9; index++) {
            Block source = mock(Block.class);
            Block destination = index == 0 ? original : mock(Block.class);
            when(source.getRelative(BlockFace.UP)).thenReturn(destination);
            when(source.getType()).thenReturn(material);
            when(source.getBlockData()).thenReturn(data);
            when(destination.getLocation()).thenReturn(location);
            when(destination.getType()).thenReturn(air);
            footprint.put(source, BlockFace.UP);
        }
        Field field = ArchitectPlacement.class.getDeclaredField("totalMap");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Map<Block, BlockFace>> previews = (Map<UUID, Map<Block, BlockFace>>) field.get(adaptation);
        previews.put(id, footprint);
        doReturn(1).when(adaptation).getActiveLevel(eq(player), any());
        doReturn(1D).when(adaptation).getValue(any(Block.class));
        doReturn(true).when(adaptation).canBlockPlace(eq(player), any(Location.class));
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return null;
        }).when(adaptation).withPlayerThread(eq(player), any(Cancellable.class), any(Runnable.class));
        doAnswer(invocation -> {
            AbilityDefaultCost cost = invocation.getArgument(4);
            return cost.take();
        }).when(adaptation).payItemCost(eq(player), anyString(), any(ItemStack.class), anyInt(), any(AbilityDefaultCost.class));
        int[] accepted = {0};
        try (MockedConstruction<ItemStack> costs = mockConstruction(ItemStack.class);
             MockedStatic<J> scheduling = mockStatic(J.class);
             MockedStatic<ProtectionEventProbe> protection = mockStatic(ProtectionEventProbe.class)) {
            protection.when(() -> ProtectionEventProbe.attemptBlockPlaceProbe(eq(player), any(Block.class)))
                    .thenAnswer(invocation -> accepted[0]++ < allowedExtras);
            assertThatThrownBy(() -> adaptation.on(event)).isInstanceOf(FeedbackReached.class);
        }
        int finalAmount = hand.getAmount() == initial ? initial - 1 : hand.getAmount();
        assertThat(finalAmount).isEqualTo(remaining);
        verify(event, never()).setCancelled(true);
    }

    private static final class FeedbackReached extends RuntimeException {
    }

    private static final class QuietPlacement extends ArchitectPlacement {
        @Override
        protected FxEmitter fx(Location location, FxPriority priority) {
            throw new FeedbackReached();
        }

        @Override
        protected void addStat(Player player, String stat, double amount) {
        }

        @Override
        public void xp(Player player, double amount) {
        }
    }
}
