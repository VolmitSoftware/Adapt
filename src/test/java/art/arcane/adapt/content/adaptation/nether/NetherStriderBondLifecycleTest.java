package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.reflect.events.api.entity.EntityDismountEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Strider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
        World world = mock(World.class);
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(mock(Material.class));
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            scheduling.when(J::isFoliaThreading).thenReturn(true);
            scheduling.when(() -> J.isOwnedByCurrentRegion(any(Location.class))).thenReturn(owned);
            Method method = NetherStriderBond.class.getDeclaredMethod("findSafeLanding", Location.class, int.class);
            method.setAccessible(true);
            method.invoke(bond, new Location(world, 0, 100, 0), 1);
            if (owned) verify(world, atLeastOnce()).getBlockAt(anyInt(), anyInt(), anyInt());
            else verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        }
    }

    private void assertLifecycle(String state, boolean completion) {
        NetherStriderBond bond = spy(new NetherStriderBond());
        Player player = mock(Player.class);
        World world = mock(World.class);
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.STONE);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        when(player.getLocation()).thenReturn(new Location(world, 0, 100, 0));
        when(player.isOnline()).thenReturn(true);
        doReturn(true).when(bond).isRuntimeRegistered();
        doReturn(5).when(bond).getActiveLevel(player);
        doReturn(true).when(bond).preferenceEnabled(player, NetherStriderBond.RESCUE);
        doReturn(null).when(bond).getPlayer(player);
        List<Runnable> pending = new ArrayList<>();
        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            scheduling.when(() -> J.runEntity(same(player), any(Runnable.class), eq(2))).thenAnswer(call -> {
                pending.add(call.getArgument(1));
                return true;
            });
            scheduling.when(() -> J.runEntity(same(player), any(Runnable.class))).thenAnswer(call -> {
                pending.add(call.getArgument(1));
                return true;
            });
            if (completion) {
                finish(bond, player);
            } else {
                EntityDismountEvent event = mock(EntityDismountEvent.class);
                when(event.getEntity()).thenReturn(player);
                when(event.getDismounted()).thenReturn(mock(Strider.class));
                bond.on(event);
            }
            assertThat(pending).hasSize(1);
            switch (state) {
                case "retired" -> doReturn(false).when(bond).isRuntimeRegistered();
                case "offline" -> when(player.isOnline()).thenReturn(false);
                case "dead" -> when(player.isDead()).thenReturn(true);
                case "unlearned" -> doReturn(0).when(bond).getActiveLevel(player);
                case "mounted" -> when(player.isInsideVehicle()).thenReturn(true);
                default -> { }
            }
            pending.getFirst().run();
            if (completion) {
                if (state.equals("active")) verify(bond).getPlayer(player);
                else verify(bond, never()).getPlayer(player);
            } else {
                if (state.equals("active")) verify(player).getLocation();
                else verify(player, never()).getLocation();
            }
        }
    }

    private void finish(NetherStriderBond bond, Player player) {
        try {
            Method method = NetherStriderBond.class.getDeclaredMethod("finishRescueTeleport", Player.class, Boolean.class, Throwable.class);
            method.setAccessible(true);
            method.invoke(bond, player, true, null);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }
}
