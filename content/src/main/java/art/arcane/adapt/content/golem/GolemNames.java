package art.arcane.adapt.content.golem;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.content.golem.reveal.RevealService;
import art.arcane.adapt.api.integration.GlossOverlay;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.server.PluginStateEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.api.text.TextFormat;

import java.util.Iterator;
import java.util.Optional;


public final class GolemNames {
  private static final String GLOSS_PLUGIN = "Gloss";
  private static final Id NAME_TAG = Id.minecraft("name_tag");

  private volatile GolemNameMode mode = GolemNameMode.VANILLA;
  private volatile boolean provisional;

  public void start() {
    evaluate();
  }

  public GolemNameMode mode() {
    return mode;
  }

  public void evaluate() {
    settle(glossOverlay(firstOnline()));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PluginStateEvent e) {
    if (!GLOSS_PLUGIN.equals(e.plugin())) {
      return;
    }
    if (e.enabled()) {
      GlossOverlay.refresh();
      evaluate();
    } else {
      provisional = false;
      settle(false);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent e) {
    if (provisional) {
      settle(glossOverlay(e.player()));
    }
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerInteractEntityEvent e) {
    if (e.protectionProbe()) {
      return;
    }
    PlayerRef player = e.player();
    PlayerInventoryRef inventory = player.inventory();
    Hand hand = e.hand();
    ItemRef tag = inventory.hand(hand);
    if (tag == null || tag.isEmpty() || !NAME_TAG.equals(tag.type())) {
      return;
    }
    Optional<GolemHandle> golem = GolemancyRuntime.handle(e.clicked());
    String name = golem.isPresent() ? tag.displayNameJson() : null;
    if (name == null) {
      return;
    }
    e.setCancelled(true);
    GolemHandle handle = golem.get();
    if (!handle.owner().equals(player.uuid())) {
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.golemancy.golem.rename_refused").resolve());
      return;
    }
    rename(handle, name);
    if (player.gameMode() != GameMode.CREATIVE) {
      useOne(inventory, hand, tag);
    }
    HudStatus.show(player, TextFormat.AQUA + Text.key("adapt.golemancy.golem.renamed").with("name", GolemSnapshot.plainName(name)).resolve());
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

  private boolean glossOverlay(PlayerRef probe) {
    if (!GlossOverlay.available()) {
      provisional = false;
      return false;
    }
    provisional = probe == null;
    return probe == null || GlossOverlay.overlayActive(probe);
  }

  private static PlayerRef firstOnline() {
    Iterator<PlayerRef> online = AdaptApi.platform().host().onlinePlayers().iterator();
    return online.hasNext() ? online.next() : null;
  }

  private static void rename(GolemHandle handle, String name) {
    MobRef golem = handle.entity();
    Runnable task = () -> GolemancyRuntime.registry().ifPresent(registry -> registry.rename(handle, name));
    if (AdaptApi.scheduler().owns(golem)) {
      task.run();
    } else {
      AdaptApi.scheduler().runEntity(golem, task);
    }
  }

  private static void useOne(PlayerInventoryRef inventory, Hand hand, ItemRef tag) {
    int amount = tag.amount();
    if (amount <= 1) {
      inventory.setHand(hand, AdaptApi.platform().host().items().empty());
    } else {
      tag.setAmount(amount - 1);
      inventory.setHand(hand, tag);
    }
  }
}
