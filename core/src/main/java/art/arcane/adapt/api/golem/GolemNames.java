package art.arcane.adapt.api.golem;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.golem.reveal.RevealService;
import art.arcane.adapt.api.insight.GlossInsightPort;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
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
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;

import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

public final class GolemNames {
  private static final String GLOSS_PLUGIN = "Gloss";
  private static final Id NAME_TAG = Id.minecraft("name_tag");

  private final GlossInsightPort gloss;
  private volatile GolemNameMode mode = GolemNameMode.VANILLA;
  private volatile boolean provisional;

  public GolemNames(GlossInsightPort gloss) {
    this.gloss = Objects.requireNonNull(gloss);
  }

  public void start() {
    evaluate();
  }

  public GolemNameMode mode() {
    return mode;
  }

  public GlossInsightPort gloss() {
    return gloss;
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
      gloss.refresh();
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
      AdaptHud.actionBar(player, C.RED + AdaptLanguage.text(GolemancyMessages.GOLEM_RENAME_REFUSED));
      return;
    }
    rename(handle, name);
    if (player.gameMode() != GameMode.CREATIVE) {
      useOne(inventory, hand, tag);
    }
    AdaptHud.actionBar(player, C.AQUA + AdaptLanguage.text(GolemancyMessages.GOLEM_RENAMED,
        untrusted("name", GolemSnapshot.plainName(name))));
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
    if (!gloss.available()) {
      provisional = false;
      return false;
    }
    provisional = probe == null;
    return probe == null || gloss.overlayActive(probe);
  }

  private static PlayerRef firstOnline() {
    Iterator<PlayerRef> online = AdaptCore.platform().host().onlinePlayers().iterator();
    return online.hasNext() ? online.next() : null;
  }

  private static void rename(GolemHandle handle, String name) {
    MobRef golem = handle.entity();
    Runnable task = () -> GolemancyRuntime.registry().ifPresent(registry -> registry.rename(handle, name));
    if (Tasks.isOwnedByCurrentRegion(golem)) {
      task.run();
    } else {
      Tasks.runEntity(golem, task);
    }
  }

  private static void useOne(PlayerInventoryRef inventory, Hand hand, ItemRef tag) {
    int amount = tag.amount();
    if (amount <= 1) {
      inventory.setHand(hand, AdaptCore.platform().host().items().empty());
    } else {
      tag.setAmount(amount - 1);
      inventory.setHand(hand, tag);
    }
  }
}
