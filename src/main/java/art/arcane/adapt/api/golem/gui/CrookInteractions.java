package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemPacking;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.overseer.Overseer;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public final class CrookInteractions implements Listener {
  private final CrookGestures<GolemHandle> gestures = new CrookGestures<>();

  @EventHandler(priority = EventPriority.HIGH)
  public void on(PlayerInteractEntityEvent e) {
    if (ProtectionEventProbe.isActive(e)) {
      return;
    }
    Player player = e.getPlayer();
    if (!isCrook(player.getInventory().getItemInMainHand())) {
      return;
    }
    Optional<GolemHandle> handle = GolemancyRuntime.handle(e.getRightClicked());
    boolean ownGolem = handle.isPresent() && handle.get().owner().equals(player.getUniqueId());
    boolean sneaking = player.isSneaking();
    CrookGestures.Action action = CrookGestures.onEntity(ownGolem, sneaking, ownGolem && sneaking && GolemPacking.packs(player));
    if (action == CrookGestures.Action.NONE) {
      return;
    }
    e.setCancelled(true);
    gestures.entityClicked(player.getUniqueId(), System.currentTimeMillis());
    if (e.getHand() != EquipmentSlot.HAND) {
      return;
    }
    if (action == CrookGestures.Action.PACK) {
      GolemPacking.packer().ifPresent(packer -> packer.pack(player, handle.get()));
    } else {
      GolemGui.open(player, handle.get(), gestures);
    }
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void on(PlayerInteractEvent e) {
    if (ProtectionEventProbe.isActive(e) || e.getHand() != EquipmentSlot.HAND || !isCrook(e.getItem())) {
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
      case LEFT_CLICK_AIR -> signal(player, now);
      default -> {
      }
    }
  }

  public void attacked(Player player) {
    if (isCrook(player.getInventory().getItemInMainHand())) {
      gestures.entityClicked(player.getUniqueId(), System.currentTimeMillis());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    gestures.forget(e.getPlayer().getUniqueId());
  }

  private void signal(Player player, long now) {
    CrookGestures.Action action = CrookGestures.onLeftAir(player.isSneaking(), gestures.isEntityClickEcho(player.getUniqueId(), now));
    switch (action) {
      case SEND_SIGNAL -> Overseer.commander().ifPresent(commander -> commander.send(player));
      case CYCLE_SIGNAL -> Overseer.commander().ifPresent(commander -> commander.cycle(player));
      case NONE, GOLEM_GUI, PACK, ROSTER_GUI, LINK_CHEST -> {
      }
    }
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
        gestures.clearLink(player.getUniqueId());
        GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
        return;
      }
      double leashRadius = GolemancyRuntime.brainSettings().leashRadius();
      GolemState.Anchor home = handle.state().home();
      GolemState.Anchor center = home == null ? GolemState.Anchor.at(handle.entity().getLocation()) : home;
      if (!GolemGuiActions.chestInReach(chest, handle.entity().getWorld().getUID(), center, leashRadius)) {
        GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_CHEST_OUT_OF_REACH, trusted("radius", (int) leashRadius));
        return;
      }
      handle.setHomeChest(chest);
      gestures.clearLink(player.getUniqueId());
      GolemGuiSupport.notify(player, GolemancyMessages.MESSAGE_CHEST_LINKED,
          trusted("x", chest.x()), trusted("y", chest.y()), trusted("z", chest.z()));
    }, () -> {
      gestures.clearLink(player.getUniqueId());
      GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
    });
    if (!scheduled) {
      gestures.clearLink(player.getUniqueId());
      GolemGuiSupport.warn(player, GolemancyMessages.MESSAGE_GONE);
    }
  }

  private static boolean canAccessChest(Player player, Location location) {
    return GolemancyRuntime.protectionPolicy().map(policy -> policy.canAccessChest(player, location)).orElse(false);
  }

  private static boolean isCrook(ItemStack item) {
    return GolemItems.kindOf(item).filter(kind -> kind == GolemItemKind.CROOK).isPresent();
  }

  public static final class PaperAttacks implements Listener {
    private final CrookInteractions interactions;

    public PaperAttacks(CrookInteractions interactions) {
      this.interactions = Objects.requireNonNull(interactions);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PrePlayerAttackEntityEvent e) {
      interactions.attacked(e.getPlayer());
    }
  }

  public static final class SpigotAttacks implements Listener {
    private final CrookInteractions interactions;

    public SpigotAttacks(CrookInteractions interactions) {
      this.interactions = Objects.requireNonNull(interactions);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityDamageByEntityEvent e) {
      if (e.getDamager() instanceof Player player) {
        interactions.attacked(player);
      }
    }
  }
}
