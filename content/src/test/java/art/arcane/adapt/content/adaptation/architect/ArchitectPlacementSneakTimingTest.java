package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArchitectPlacementSneakTimingTest extends AdaptTestBase {
    @BeforeEach
    void installPlatform() {
        TestCores.use(TestCores.install(TestPlatform.builder(dataFolder.toPath()).build().platform()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"active", "cancelled", "unlearned", "offline", "standing", "retired", "unequipped", "rejected"})
    void sneakPreviewWaitsForCommittedPlayerStateAndChecksItsOwner(String state) {
        ArchitectPlacement placement = spy(TestContent.adaptation(Id.adapt("architect"), ArchitectPlacement::new));
        PlayerRef player = mock(PlayerRef.class);
        PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
        ItemRef hand = mock(ItemRef.class);
        BlockRef source = mock(BlockRef.class);
        BlockRef adjacent = mock(BlockRef.class);
        when(player.uuid()).thenReturn(UUID.randomUUID());
        when(player.inventory()).thenReturn(inventory);
        when(inventory.mainHand()).thenReturn(hand);
        when(hand.type()).thenReturn(Id.minecraft("stone"));
        when(source.type()).thenReturn(Id.minecraft("stone"));
        when(source.x()).thenReturn(0);
        when(adjacent.x()).thenReturn(-1);
        when(player.lastTwoTargetBlocks(5)).thenReturn(List.of(adjacent, source));
        when(player.isOnline()).thenReturn(true);
        doReturn(true).when(placement).isRuntimeRegistered();
        doReturn(1).when(placement).getActiveLevel(player);
        doReturn(ArchitectPlacement.Control.SNEAK).when(placement).preference(player, ArchitectPlacement.CONTROL);
        doNothing().when(placement).runPlayerViewport(any(), any(), any(), any());
        PlayerToggleSneakEvent event = new PlayerToggleSneakEvent(player, true);
        List<Runnable> pending = new ArrayList<>();

        try (MockedStatic<Tasks> scheduler = SchedulerForwarding.mockTasks()) {
            scheduler.when(() -> Tasks.runEntity(same(player), any(Runnable.class), eq(1))).thenAnswer(call -> {
                if (state.equals("rejected")) return false;
                pending.add(call.getArgument(1));
                return true;
            });

            placement.on(event);

            verify(placement, never()).runPlayerViewport(any(), any(), any(), any());
            verify(player, never()).lastTwoTargetBlocks(anyInt());
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
                case "unequipped" -> when(hand.type()).thenReturn(Id.minecraft("diamond_pickaxe"));
                default -> { }
            }

            pending.getFirst().run();

            if (state.equals("active")) verify(placement).runPlayerViewport(BlockFace.WEST, source, Id.minecraft("stone"), player);
            else verify(placement, never()).runPlayerViewport(any(), any(), any(), any());
        }
    }
}
