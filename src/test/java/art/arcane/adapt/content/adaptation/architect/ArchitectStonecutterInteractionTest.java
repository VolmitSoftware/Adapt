package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.EventHandlerInvoker;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArchitectStonecutterInteractionTest extends AdaptTestBase {
    @ParameterizedTest
    @CsvSource({"true,false,false,true", "true,false,true,true", "false,true,false,true", "false,true,true,false", "false,false,false,false", "false,false,true,false"})
    void carriedStonecutterUsesStorageAndOffhandAccordingToConfiguredRequirement(boolean offhand, boolean storage, boolean required, boolean permitted) throws Exception {
        ArchitectStonecutterSavant adaptation = spy(new ArchitectStonecutterSavant());
        adaptation.getConfig().requireOffhand = required;
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.isSneaking()).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(mock(World.class), 0, 100, 0));
        ItemStack empty = mock(ItemStack.class);
        when(empty.getType()).thenReturn(Material.AIR);
        when(inventory.getItemInMainHand()).thenReturn(empty);
        ItemStack carried = mock(ItemStack.class);
        when(carried.getType()).thenReturn(offhand ? Material.STONECUTTER : Material.AIR);
        when(inventory.getItemInOffHand()).thenReturn(carried);
        when(inventory.contains(Material.STONECUTTER)).thenReturn(storage);
        doReturn(false).when(adaptation).preferenceEnabled(player, ArchitectStonecutterSavant.OFFHAND);
        doReturn(null).when(adaptation).resolveInteractContext(eq(player), any(Location.class), any());
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.LEFT_CLICK_AIR, null, null, BlockFace.UP, EquipmentSlot.HAND);
        Method handler = ArchitectStonecutterSavant.class.getMethod("on", PlayerInteractEvent.class);
        EventHandlerInvoker.createExecutor(adaptation, handler, PlayerInteractEvent.class, true).execute(adaptation, event);
        if (permitted) verify(adaptation).resolveInteractContext(eq(player), any(Location.class), any());
        else verify(adaptation, never()).resolveInteractContext(eq(player), any(Location.class), any());
    }
}
