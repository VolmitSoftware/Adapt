package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArchitectPlacementSneakTimingTest extends AdaptTestBase {
    @ParameterizedTest
    @ValueSource(strings = {"active", "cancelled", "unlearned", "offline", "standing", "retired", "unequipped", "rejected"})
    void sneakPreviewWaitsForCommittedPlayerStateAndChecksItsOwner(String state) {
        ArchitectPlacement placement = spy(new ArchitectPlacement());
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack hand = mock(ItemStack.class);
        Material material = mock(Material.class);
        Block source = mock(Block.class);
        Block adjacent = mock(Block.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(hand);
        when(hand.getType()).thenReturn(material);
        when(material.isBlock()).thenReturn(true);
        when(material.isOccluding()).thenReturn(true);
        when(source.getType()).thenReturn(material);
        when(source.getFace(adjacent)).thenReturn(BlockFace.WEST);
        when(player.getTargetBlock(isNull(), eq(5))).thenReturn(source);
        when(player.getLastTwoTargetBlocks(isNull(), eq(5))).thenReturn(List.of(adjacent, source));
        when(player.isOnline()).thenReturn(true);
        doReturn(true).when(placement).isRuntimeRegistered();
        doReturn(1).when(placement).getActiveLevel(player);
        doReturn(ArchitectPlacement.Control.SNEAK).when(placement).preference(player, ArchitectPlacement.CONTROL);
        doNothing().when(placement).runPlayerViewport(any(), any(), any(), any());
        PlayerToggleSneakEvent event = new PlayerToggleSneakEvent(player, true);
        List<Runnable> pending = new ArrayList<>();
        try (MockedStatic<J> scheduler = mockStatic(J.class)) {
            scheduler.when(() -> J.runEntity(same(player), any(Runnable.class), eq(1))).thenAnswer(call -> {
                if (state.equals("rejected")) return false;
                pending.add(call.getArgument(1));
                return true;
            });
            placement.on(event);
            verify(placement, never()).runPlayerViewport(any(), any(), any(), any());
            verify(player, never()).getTargetBlock(any(), anyInt());
            if (state.equals("rejected")) {
                assertThat(pending).isEmpty();
                return;
            }
            assertThat(pending).hasSize(1);
            when(player.isSneaking()).thenReturn(true);
            switch (state) {
                case "cancelled" -> event.setCancelled(true);
                case "unlearned" -> doReturn(0).when(placement).getActiveLevel(player);
                case "offline" -> when(player.isOnline()).thenReturn(false);
                case "standing" -> when(player.isSneaking()).thenReturn(false);
                case "retired" -> doReturn(false).when(placement).isRuntimeRegistered();
                case "unequipped" -> when(material.isBlock()).thenReturn(false);
                default -> { }
            }
            pending.getFirst().run();
            if (state.equals("active")) verify(placement).runPlayerViewport(BlockFace.WEST, source, material, player);
            else verify(placement, never()).runPlayerViewport(any(), any(), any(), any());
        }
    }
}
