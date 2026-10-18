package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeEdits;
import art.arcane.adapt.api.golem.node.NodeFilters;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeRules;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

public final class NodeInteractions implements Listener {
  private final NodeTagPrompt prompt;

  public NodeInteractions(NodeTagPrompt prompt) {
    this.prompt = Objects.requireNonNull(prompt);
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void on(PlayerInteractEntityEvent e) {
    if (ProtectionEventProbe.isActive(e)) {
      return;
    }
    Optional<NodeKey> key = nodeOf(e.getRightClicked());
    if (key.isEmpty()) {
      return;
    }
    e.setCancelled(true);
    if (e.getHand() == EquipmentSlot.HAND) {
      clicked(e.getPlayer(), key.get(), NodeGestures.Click.RIGHT);
    }
  }

  public void attacked(Player player, Entity target, Cancellable event) {
    Optional<NodeKey> key = nodeOf(target);
    if (key.isEmpty()) {
      return;
    }
    event.setCancelled(true);
    clicked(player, key.get(), NodeGestures.Click.LEFT);
  }

  private void clicked(Player player, NodeKey key, NodeGestures.Click click) {
    NodeRecord record = GolemancyRuntime.nodes().flatMap(service -> service.node(key)).orElse(null);
    if (record == null) {
      return;
    }
    PlayerInventory inventory = player.getInventory();
    ItemStack mainHand = inventory.getItemInMainHand();
    NodeGestures.Action action = NodeGestures.route(click, player.isSneaking(), isCrook(mainHand), isCrook(inventory.getItemInOffHand()), mainHand.getType());
    if (action == NodeGestures.Action.NONE) {
      return;
    }
    if (!record.owner().equals(player.getUniqueId())) {
      GolemGuiSupport.warn(player, GolemancyMessages.NODE_NOT_OWNER);
      return;
    }
    switch (action) {
      case NONE -> {
      }
      case OPEN_GUI -> NodeGui.open(player, key, prompt);
      case NEXT_CHANNEL -> setChannel(player, key, current -> NodeEdits.nextChannel(current, true));
      case SET_CHANNEL -> {
        DyeColor dye = NodeEdits.dyeChannel(mainHand.getType()).orElse(null);
        setChannel(player, key, current -> dye);
      }
      case TOGGLE_FILTER -> toggleFilter(player, key, mainHand);
      case CYCLE_PRIORITY -> cyclePriority(player, key);
      case REMOVE -> remove(player, key, record);
    }
  }

  private void setChannel(Player player, NodeKey key, UnaryOperator<DyeColor> channel) {
    edit(player, key, current -> current.toBuilder().channel(channel.apply(current.channel())).build(), updated -> {
      int mask = updated.channel() == null ? ChannelMask.NONE : ChannelMask.of(updated.channel());
      GolemGuiSupport.notify(player, GolemancyMessages.GUI_CHANNEL, trusted("channel", GolemGuiSupport.localized(GolemGuiActions.channelName(mask))));
    });
  }

  private void toggleFilter(Player player, NodeKey key, ItemStack held) {
    int slots = NodeGui.filterSlots(NodeGui.level(player));
    ItemStack ghost = NodeFilters.ghost(held);
    Object name = GolemGuiSupport.itemName(ghost);
    AtomicReference<NodeFilters.Outcome> outcome = new AtomicReference<>(NodeFilters.Outcome.UNCHANGED);
    edit(player, key, current -> {
      NodeFilters.FilterEdit change = NodeFilters.toggleItem(current, ghost, slots);
      outcome.set(change.outcome());
      return change.record();
    }, updated -> {
      switch (outcome.get()) {
        case ADDED -> GolemGuiSupport.notify(player, GolemancyMessages.NODE_FILTER_ADDED, untrusted("item", name));
        case REMOVED -> GolemGuiSupport.notify(player, GolemancyMessages.NODE_FILTER_REMOVED, untrusted("item", name));
        case FULL -> GolemGuiSupport.warn(player, GolemancyMessages.NODE_FILTER_FULL, trusted("count", slots));
        case REPLACED, UNCHANGED -> {
        }
      }
    });
  }

  private void cyclePriority(Player player, NodeKey key) {
    int level = NodeGui.level(player);
    if (!NodeRules.allowsPriority(level)) {
      GolemGuiSupport.warn(player, GolemancyMessages.NODE_PRIORITY_LOCKED, trusted("level", NodeRules.PRIORITY_LEVEL));
      return;
    }
    edit(player, key, current -> NodeEdits.cyclePriority(current, level, true).orElse(current),
        updated -> GolemGuiSupport.notify(player, GolemancyMessages.NODE_GUI_PRIORITY, trusted("priority", updated.priority())));
  }

  private void remove(Player player, NodeKey key, NodeRecord record) {
    onAnchor(player, key, service -> {
      if (service.remove(player, key)) {
        GolemGuiSupport.notify(player, GolemancyMessages.NODE_REMOVED, trusted("node", GolemGuiSupport.localized(record.type().displayName())));
      } else {
        GolemGuiSupport.warn(player, GolemancyMessages.NODE_REMOVE_FAILED);
      }
    });
  }

  private void edit(Player player, NodeKey key, UnaryOperator<NodeRecord> change, Consumer<NodeRecord> feedback) {
    UUID owner = player.getUniqueId();
    onAnchor(player, key, service -> service.update(owner, key, change)
        .ifPresentOrElse(feedback, () -> GolemGuiSupport.warn(player, GolemancyMessages.NODE_GONE)));
  }

  private static void onAnchor(Player player, NodeKey key, Consumer<NodeService> task) {
    World world = Bukkit.getWorld(key.world());
    Optional<NodeService> service = GolemancyRuntime.nodes();
    if (world == null || service.isEmpty() || !J.runAt(key.center(world), () -> task.accept(service.get()))) {
      GolemGuiSupport.warn(player, GolemancyMessages.NODE_GONE);
    }
  }

  private static Optional<NodeKey> nodeOf(Entity entity) {
    if (!(entity instanceof Interaction)) {
      return Optional.empty();
    }
    return GolemancyRuntime.nodes().flatMap(service -> service.renderer().nodeOf(entity));
  }

  private static boolean isCrook(ItemStack item) {
    return GolemItems.kindOf(item).filter(kind -> kind == GolemItemKind.CROOK).isPresent();
  }

  public static final class PaperAttacks implements Listener {
    private final NodeInteractions interactions;

    public PaperAttacks(NodeInteractions interactions) {
      this.interactions = Objects.requireNonNull(interactions);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void on(PrePlayerAttackEntityEvent e) {
      interactions.attacked(e.getPlayer(), e.getAttacked(), e);
    }
  }

  public static final class SpigotAttacks implements Listener {
    private final NodeInteractions interactions;

    public SpigotAttacks(NodeInteractions interactions) {
      this.interactions = Objects.requireNonNull(interactions);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void on(EntityDamageByEntityEvent e) {
      if (e.getDamager() instanceof Player player) {
        interactions.attacked(player, e.getEntity(), e);
      }
    }
  }
}
