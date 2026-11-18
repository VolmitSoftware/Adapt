package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityDismountEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NetherStriderBondLifecycleTest extends AdaptTestBase {
    @ParameterizedTest
    @ValueSource(strings = {"active", "retired", "offline", "dead", "unlearned", "mounted"})
    void delayedDismountChecksCurrentOwnerBeforeReadingWorld(String state) {
        assertLifecycle(state, false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"active", "retired", "offline", "dead", "unlearned", "mounted"})
    void completedTeleportChecksCurrentOwnerBeforeCreditingRescue(String state) {
        assertLifecycle(state, true);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void foliaSearchReadsOnlyOwnedCandidateColumns(boolean owned) throws ReflectiveOperationException {
        NetherStriderBond bond = new NetherStriderBond();
        WorldRef world = mock(WorldRef.class);
        BlockRef block = mock(BlockRef.class);
        when(block.type()).thenReturn(Id.minecraft("deepslate"));
        when(world.blockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        when(world.blockAt(any(Loc.class))).thenReturn(block);
        AdaptScheduler scheduler = core.getPlatform().scheduler();
        when(scheduler.isRegionized()).thenReturn(true);
        when(scheduler.owns(any(Loc.class))).thenReturn(owned);

        Method method = NetherStriderBond.class.getDeclaredMethod("findSafeLanding", Loc.class, int.class);
        method.setAccessible(true);
        method.invoke(bond, new Loc(world, 0, 100, 0), 1);

        if (owned) verify(world, atLeastOnce()).blockAt(anyInt(), anyInt(), anyInt());
        else verify(world, never()).blockAt(anyInt(), anyInt(), anyInt());
    }

    private void assertLifecycle(String state, boolean completion) {
        NetherStriderBond bond = spy(new NetherStriderBond());
        PlayerRef player = mock(PlayerRef.class);
        when(player.uuid()).thenReturn(UUID.randomUUID());
        WorldRef world = mock(WorldRef.class);
        BlockRef block = mock(BlockRef.class);
        when(block.type()).thenReturn(Id.minecraft("stone"));
        when(world.blockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        when(world.blockAt(any(Loc.class))).thenReturn(block);
        when(player.location()).thenReturn(new Loc(world, 0, 100, 0));
        when(player.isOnline()).thenReturn(true);
        doReturn(true).when(bond).isRuntimeRegistered();
        doReturn(5).when(bond).getActiveLevel(player);
        doReturn(true).when(bond).preferenceEnabled(player, NetherStriderBond.RESCUE);
        doReturn(null).when(bond).getPlayer(player);
        List<Runnable> pending = new ArrayList<>();
        AdaptScheduler scheduler = core.getPlatform().scheduler();
        when(scheduler.runEntity(same(player), any(Runnable.class), eq(2L), any())).thenAnswer(call -> {
            pending.add(call.getArgument(1));
            return true;
        });
        when(scheduler.runEntity(same(player), any(Runnable.class), eq(0L), any())).thenAnswer(call -> {
            pending.add(call.getArgument(1));
            return true;
        });

        if (completion) {
            finish(bond, player);
        } else {
            EntityRef strider = mock(EntityRef.class);
            when(strider.type()).thenReturn(Id.minecraft("strider"));
            bond.on(new EntityDismountEvent(player, strider, true));
        }
        assertThat(pending).hasSize(1);

        switch (state) {
            case "retired" -> doReturn(false).when(bond).isRuntimeRegistered();
            case "offline" -> when(player.isOnline()).thenReturn(false);
            case "dead" -> when(player.isDead()).thenReturn(true);
            case "unlearned" -> doReturn(0).when(bond).getActiveLevel(player);
            case "mounted" -> when(player.vehicle()).thenReturn(mock(EntityRef.class));
            default -> { }
        }
        pending.getFirst().run();

        if (completion) {
            if (state.equals("active")) verify(bond).getPlayer(player);
            else verify(bond, never()).getPlayer(player);
        } else {
            if (state.equals("active")) verify(player).location();
            else verify(player, never()).location();
        }
    }

    private void finish(NetherStriderBond bond, PlayerRef player) {
        try {
            Method method = NetherStriderBond.class.getDeclaredMethod("finishRescueTeleport", PlayerRef.class, Boolean.class, Throwable.class);
            method.setAccessible(true);
            method.invoke(bond, player, true, null);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }
}
