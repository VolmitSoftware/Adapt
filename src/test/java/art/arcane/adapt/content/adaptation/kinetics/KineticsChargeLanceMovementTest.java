package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.PlayerStateRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;
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
        KineticsChargeLance adaptation = spy(new QuietLance());
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack spear = mock(ItemStack.class);
        World world = mock(World.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getInventory()).thenReturn(inventory);
        when(player.getVelocity()).thenReturn(new Vector());
        when(player.getTicksLived()).thenReturn(100);
        when(inventory.getItemInMainHand()).thenReturn(spear);
        when(spear.getType()).thenReturn(state.equals("unequipped") ? Material.STICK : Material.WOODEN_SPEAR);
        when(player.isInsideVehicle()).thenReturn(state.equals("mounted"));
        doReturn(!state.equals("unlearned")).when(adaptation).hasActiveAdaptation(player);
        Location from = new Location(world, 0, 100, 0);
        Location to = new Location(world, state.equals("below-speed") ? 0.1D : 0.28D, 100, 0);
        adaptation.on(new PlayerMoveEvent(player, from, to));
        switch (state) {
            case "unregistered" -> adaptation.unregister();
            case "disconnect" -> PlayerStateRegistry.clearPlayer(player.getUniqueId());
            case "reset" -> PlayerStateRegistry.reset();
            case "loses-level" -> {
                doReturn(false).when(adaptation).hasActiveAdaptation(player);
                adaptation.on(new PlayerMoveEvent(player, to, from));
            }
            case "removes-spear" -> {
                when(spear.getType()).thenReturn(Material.STICK);
                adaptation.on(new PlayerMoveEvent(player, to, from));
            }
            case "mounts-after" -> {
                when(player.isInsideVehicle()).thenReturn(true);
                adaptation.on(new PlayerMoveEvent(player, to, from));
                when(player.isInsideVehicle()).thenReturn(false);
            }
            default -> { }
        }
        if (state.equals("teleport")) adaptation.on(new PlayerTeleportEvent(player, to, new Location(world, 30, 100, 0)));
        when(player.getTicksLived()).thenReturn(state.equals("stale") ? 103 : 100 + age);
        EntityDamageByEntityEvent hit = mock(EntityDamageByEntityEvent.class);
        when(hit.getDamage()).thenReturn(10D);
        doReturn(new Adaptation.MeleeContext(player, mock(LivingEntity.class), spear, 5))
                .when(adaptation).resolveMeleeContext(eq(hit), any());
        adaptation.on(hit);
        if (expected >= 0) verify(hit).setDamage(expected);
        else verify(hit, never()).setDamage(anyDouble());
    }

    private static final class QuietLance extends KineticsChargeLance {
        @Override
        protected void addStat(Player player, String stat, double amount) {
        }
    }
}
