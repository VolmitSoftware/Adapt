package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.protection.Protector;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public final class CrookInteractions implements Listener {
  private static final double LEASH_RADIUS = 32D;

  private final CrookGestures<GolemHandle> gestures = new CrookGestures<>();

  @EventHandler(priority = EventPriority.HIGH)
  public void on(PlayerInteractEntityEvent e) {
    Player player = e.getPlayer();
    if (!isCrook(player.getInventory().getItemInMainHand())) {
      return;
    }
    Optional<GolemHandle> handle = GolemancyRuntime.handle(e.getRightClicked());
    boolean ownGolem = handle.isPresent() && handle.get().owner().equals(player.getUniqueId());
    if (CrookGestures.onEntity(ownGolem) != CrookGestures.Action.GOLEM_GUI) {
      return;
    }
    e.setCancelled(true);
    gestures.entityClicked(player.getUniqueId(), System.currentTimeMillis());
    if (e.getHand() == EquipmentSlot.HAND) {
      GolemGui.open(player, handle.get(), gestures);
    }
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void on(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND || !isCrook(e.getItem())) {
      return;
    }
    Player player = e.getPlayer();
    long now = System.currentTimeMillis();
    switch (e.getAction()) {
      case RIGHT_CLICK_AIR -> {
        if (CrookGestures.onAir(player.isSneaking(), gestures.isEntityClickEcho(player.getUniqueId(), now)) == CrookGestures.Action.ROSTER_GUI) {
          RosterGui.open(player);
        }
      }
      case RIGHT_CLICK_BLOCK -> linkHomeChest(e, player, now);
      default -> {
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    gestures.forget(e.getPlayer().getUniqueId());
  }

  private void linkHomeChest(PlayerInteractEvent e, Player player, long now) {
    Block block = e.getClickedBlock();
    Optional<GolemHandle> target = gestures.linkTarget(player.getUniqueId(), now);
    boolean container = target.isPresent() && block != null && block.getState() instanceof Container;
    if (CrookGestures.onBlock(target.isPresent(), container) != CrookGestures.Action.LINK_CHEST) {
      return;
    }
    e.setUseInteractedBlock(Event.Result.DENY);
    e.setUseItemInHand(Event.Result.DENY);
    Location chestLocation = block.getLocation();
    if (!canAccessChest(player, chestLocation)) {
      GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_CHEST_DENIED);
      return;
    }
    GolemHandle handle = target.get();
    GolemState.Anchor chest = GolemState.Anchor.at(chestLocation);
    boolean scheduled = J.runEntity(handle.entity(), () -> {
      if (handle.isRetired() || !handle.entity().isValid()) {
        GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
        return;
      }
      GolemState.Anchor home = handle.state().home();
      GolemState.Anchor center = home == null ? GolemState.Anchor.at(handle.entity().getLocation()) : home;
      if (!GolemGuiActions.chestInReach(chest, handle.entity().getWorld().getUID(), center, LEASH_RADIUS)) {
        GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_CHEST_OUT_OF_REACH, trusted("radius", (int) LEASH_RADIUS));
        return;
      }
      handle.setHomeChest(chest);
      gestures.clearLink(player.getUniqueId());
      GolemGuiSupport.notify(player, GolemancyMessages.MESSAGE_CHEST_LINKED,
          trusted("x", chest.x()), trusted("y", chest.y()), trusted("z", chest.z()));
    });
    if (!scheduled) {
      GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
    }
  }

  private static boolean canAccessChest(Player player, Location location) {
    for (Protector protector : Adapt.instance.getProtectorRegistry().getDefaultProtectors()) {
      if (!protector.canAccessChest(player, location, null)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isCrook(ItemStack item) {
    return GolemItems.kindOf(item).filter(kind -> kind == GolemItemKind.CROOK).isPresent();
  }
}
