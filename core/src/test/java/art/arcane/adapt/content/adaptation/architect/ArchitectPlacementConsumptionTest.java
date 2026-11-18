package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.api.ability.AbilityDefaultCost;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Cancellable;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class ArchitectPlacementConsumptionTest extends AdaptTestBase {
    @BeforeEach
    void installPlatform() {
        TestCores.use(TestCores.install(TestPlatform.builder(dataFolder.toPath()).build().platform()));
    }

    @ParameterizedTest
    @CsvSource({"9,8,0", "32,8,23", "32,3,28", "32,0,31"})
    void placementPaysForTheTriggerWhenChangingTheStackSuppressesVanillaConsumption(int initial, int allowedExtras, int remaining) throws Exception {
        QuietPlacement adaptation = spy(new QuietPlacement());
        PlayerRef player = mock(PlayerRef.class);
        PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
        UUID id = UUID.randomUUID();
        when(player.uuid()).thenReturn(id);
        when(player.inventory()).thenReturn(inventory);
        Id material = Id.minecraft("stone");
        AtomicInteger amount = new AtomicInteger(initial);
        ItemRef hand = mock(ItemRef.class);
        when(hand.type()).thenReturn(material);
        when(hand.amount()).thenAnswer(invocation -> amount.get());
        doAnswer(invocation -> {
            amount.set(invocation.getArgument(0));
            return null;
        }).when(hand).setAmount(anyInt());
        when(inventory.hand(Hand.MAIN_HAND)).thenReturn(hand);
        when(hand.isSimilar(hand)).thenReturn(true);
        Loc location = new Loc(mock(WorldRef.class), 0, 100, 0);
        Map<BlockRef, BlockFace> footprint = new HashMap<>();
        BlockRef original = mock(BlockRef.class);
        when(original.location()).thenReturn(location);
        BlockStateRef data = mock(BlockStateRef.class);
        for (int index = 0; index < 9; index++) {
            BlockRef source = mock(BlockRef.class);
            BlockRef destination = index == 0 ? original : mock(BlockRef.class);
            when(source.relative(BlockFace.UP)).thenReturn(destination);
            when(source.type()).thenReturn(material);
            when(source.state()).thenReturn(data);
            when(destination.location()).thenReturn(location);
            when(destination.type()).thenReturn(Id.minecraft("air"));
            footprint.put(source, BlockFace.UP);
        }
        BlockPlaceEvent event = new BlockPlaceEvent(player, original, data, original, hand, Hand.MAIN_HAND);
        Field field = ArchitectPlacement.class.getDeclaredField("totalMap");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Map<BlockRef, BlockFace>> previews = (Map<UUID, Map<BlockRef, BlockFace>>) field.get(adaptation);
        previews.put(id, footprint);
        doReturn(1).when(adaptation).getActiveLevel(eq(player), any());
        doReturn(1D).when(adaptation).getValue(any(BlockRef.class));
        doReturn(true).when(adaptation).canBlockPlace(eq(player), any(Loc.class));
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return null;
        }).when(adaptation).withPlayerThread(eq(player), any(Cancellable.class), any(Runnable.class));
        doAnswer(invocation -> {
            AbilityDefaultCost cost = invocation.getArgument(4);
            return cost.take();
        }).when(adaptation).payItemCost(eq(player), anyString(), any(ItemRef.class), anyInt(), any(AbilityDefaultCost.class));
        int[] accepted = {0};

        try (MockedStatic<ProtectionProbes> protection = mockStatic(ProtectionProbes.class)) {
            protection.when(() -> ProtectionProbes.attemptBlockPlaceProbe(eq(player), any(BlockRef.class)))
                    .thenAnswer(invocation -> accepted[0]++ < allowedExtras);
            assertThatThrownBy(() -> adaptation.on(event)).isInstanceOf(FeedbackReached.class);
        }

        int finalAmount = hand.amount() == initial ? initial - 1 : hand.amount();
        assertThat(finalAmount).isEqualTo(remaining);
        assertThat(event.isCancelled()).isFalse();
    }

    private static final class FeedbackReached extends RuntimeException {
    }

    private static final class QuietPlacement extends ArchitectPlacement {
        @Override
        protected FxEmitter fx(Loc location, FxPriority priority) {
            throw new FeedbackReached();
        }

        @Override
        protected void addStat(PlayerRef player, String stat, double amount) {
        }

        @Override
        public void xp(PlayerRef player, double amount) {
        }
    }
}
