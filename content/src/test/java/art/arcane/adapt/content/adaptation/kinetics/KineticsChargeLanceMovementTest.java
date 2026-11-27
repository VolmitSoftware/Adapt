package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.engine.adaptation.PlayerStateRegistry;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.testkit.TestContent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KineticsChargeLanceMovementTest extends AdaptTestBase {
    private static final Id WOODEN_SPEAR = Id.minecraft("wooden_spear");
    private static final Id STICK = Id.minecraft("stick");

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void acceptedSprintMovementAddsDamageEvenWhenServerVelocityIsZero(int age) {
        assertCharge("moving", age, 10D * (1D + 0.28D * 2D));
    }

    @ParameterizedTest
    @ValueSource(strings = {"stale", "teleport", "mounted", "unlearned", "unequipped", "below-speed", "unregistered", "disconnect", "reset", "loses-level", "removes-spear", "mounts-after"})
    void invalidMovementCannotSupplyChargeDamage(String state) {
        assertCharge(state, 0, -1D);
    }

    private void assertCharge(String state, int age, double expected) {
        KineticsChargeLance adaptation = spy(TestContent.adaptation(Id.adapt("kinetics"), KineticsChargeLance::new));
        PlayerRef player = mock(PlayerRef.class);
        PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
        ItemRef spear = mock(ItemRef.class);
        WorldRef world = mock(WorldRef.class);
        EntityRef mount = mock(EntityRef.class);
        when(player.uuid()).thenReturn(UUID.randomUUID());
        when(player.inventory()).thenReturn(inventory);
        when(player.ticksLived()).thenReturn(100);
        when(inventory.mainHand()).thenReturn(spear);
        when(spear.type()).thenReturn(state.equals("unequipped") ? STICK : WOODEN_SPEAR);
        when(player.vehicle()).thenReturn(state.equals("mounted") ? mount : null);
        doReturn(!state.equals("unlearned")).when(adaptation).hasActiveAdaptation(player);
        Loc from = new Loc(world, 0, 100, 0);
        Loc to = new Loc(world, state.equals("below-speed") ? 0.1D : 0.28D, 100, 0);
        adaptation.on(new PlayerMoveEvent(player, from, to));
        switch (state) {
            case "unregistered" -> adaptation.unregister();
            case "disconnect" -> PlayerStateRegistry.clearPlayer(player.uuid());
            case "reset" -> PlayerStateRegistry.reset();
            case "loses-level" -> {
                doReturn(false).when(adaptation).hasActiveAdaptation(player);
                adaptation.on(new PlayerMoveEvent(player, to, from));
            }
            case "removes-spear" -> {
                when(spear.type()).thenReturn(STICK);
                adaptation.on(new PlayerMoveEvent(player, to, from));
            }
            case "mounts-after" -> {
                when(player.vehicle()).thenReturn(mount);
                adaptation.on(new PlayerMoveEvent(player, to, from));
                when(player.vehicle()).thenReturn(null);
            }
            default -> { }
        }
        if (state.equals("teleport")) {
            adaptation.on(new PlayerTeleportEvent(player, to, new Loc(world, 30, 100, 0), TeleportCause.PLUGIN));
        }
        when(player.ticksLived()).thenReturn(state.equals("stale") ? 103 : 100 + age);
        EntityDamageByEntityEvent hit = mock(EntityDamageByEntityEvent.class);
        when(hit.damage()).thenReturn(10D);
        doReturn(new Adaptation.MeleeContext(player, mock(LivingRef.class), spear, 5))
                .when(adaptation).resolveMeleeContext(eq(hit), any());
        adaptation.on(hit);
        if (expected >= 0) verify(hit).setDamage(expected);
        else verify(hit, never()).setDamage(anyDouble());
    }
}
