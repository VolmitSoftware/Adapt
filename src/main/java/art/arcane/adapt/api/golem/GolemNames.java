package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.golem.reveal.RevealService;
import art.arcane.adapt.api.insight.GlossInsight;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;

import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

public final class GolemNames implements Listener {
  private static final String GLOSS_PLUGIN = "Gloss";

  private final GlossInsight gloss;
  private volatile GolemNameMode mode = GolemNameMode.VANILLA;
  private volatile boolean provisional;

  public GolemNames(GlossInsight gloss) {
    this.gloss = Objects.requireNonNull(gloss);
  }

  public void start() {
    evaluate();
  }

  public GolemNameMode mode() {
    return mode;
  }

  public GlossInsight gloss() {
    return gloss;
  }

  public void evaluate() {
    settle(glossOverlay(firstOnline()));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PluginEnableEvent e) {
    if (GLOSS_PLUGIN.equals(e.getPlugin().getName())) {
      gloss.refresh();
      evaluate();
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PluginDisableEvent e) {
    if (GLOSS_PLUGIN.equals(e.getPlugin().getName())) {
      provisional = false;
      settle(false);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent e) {
    if (provisional) {
      settle(glossOverlay(e.getPlayer()));
    }
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(PlayerInteractEntityEvent e) {
    if (ProtectionEventProbe.isActive(e)) {
      return;
    }
    Player player = e.getPlayer();
    PlayerInventory inventory = player.getInventory();
    EquipmentSlot hand = e.getHand();
    ItemStack tag = hand == EquipmentSlot.OFF_HAND ? inventory.getItemInOffHand() : inventory.getItemInMainHand();
    if (tag == null || tag.getType() != Material.NAME_TAG) {
      return;
    }
    Optional<GolemHandle> golem = GolemancyRuntime.handle(e.getRightClicked());
    Component name = golem.isPresent() ? PaperCompat.displayName(tag) : null;
    if (name == null) {
      return;
    }
    e.setCancelled(true);
    GolemHandle handle = golem.get();
    if (!handle.owner().equals(player.getUniqueId())) {
      AdaptHud.actionBar(player, C.RED + AdaptLanguage.text(GolemancyMessages.GOLEM_RENAME_REFUSED));
      return;
    }
    rename(handle, name);
    if (player.getGameMode() != GameMode.CREATIVE) {
      useOne(inventory, hand, tag);
    }
    AdaptHud.actionBar(player, C.AQUA + AdaptLanguage.text(GolemancyMessages.GOLEM_RENAMED,
        untrusted("name", PlainTextComponentSerializer.plainText().serialize(name))));
  }

  private void settle(boolean glossOverlay) {
    GolemNameMode next = GolemNameMode.resolve(glossOverlay);
    synchronized (this) {
      if (mode == next) {
        return;
      }
      mode = next;
    }
    GolemancyRuntime.registry().ifPresent(GolemRegistry::showNames);
    GolemancyRuntime.reveal().ifPresent(RevealService::refreshViewers);
  }

  private boolean glossOverlay(Player probe) {
    if (!gloss.available()) {
      provisional = false;
      return false;
    }
    provisional = probe == null;
    return probe == null || gloss.overlayActive(probe);
  }

  private static Player firstOnline() {
    Iterator<? extends Player> online = Bukkit.getOnlinePlayers().iterator();
    return online.hasNext() ? online.next() : null;
  }

  private static void rename(GolemHandle handle, Component name) {
    Mob golem = handle.entity();
    Runnable task = () -> GolemancyRuntime.registry().ifPresent(registry -> registry.rename(handle, name));
    if (J.isOwnedByCurrentRegion(golem)) {
      task.run();
    } else {
      J.runEntity(golem, task);
    }
  }

  private static void useOne(PlayerInventory inventory, EquipmentSlot hand, ItemStack tag) {
    int amount = tag.getAmount();
    if (amount <= 1) {
      inventory.setItem(hand, null);
    } else {
      tag.setAmount(amount - 1);
    }
  }
}
