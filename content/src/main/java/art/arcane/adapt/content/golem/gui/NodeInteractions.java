package art.arcane.adapt.content.golem.gui;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PrePlayerAttackEntityEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;

import art.arcane.adapt.content.golem.ChannelMask;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.item.GolemItemKind;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.content.golem.node.NodeEdits;
import art.arcane.adapt.content.golem.node.NodeFilters;
import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.content.golem.node.NodeRules;
import art.arcane.adapt.content.golem.node.NodeService;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;


public final class NodeInteractions {
  private static final Id INTERACTION = Id.minecraft("interaction");

  private final NodeTagPrompt prompt;
  private final NodeAreaSelector areas;

  public NodeInteractions(NodeTagPrompt prompt, NodeAreaSelector areas) {
    this.prompt = Objects.requireNonNull(prompt);
    this.areas = Objects.requireNonNull(areas);
  }

  @Handle(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void on(PlayerInteractEntityEvent e) {
    if (e.protectionProbe() || areas.swallows(e.player())) {
      return;
    }
    Optional<NodeKey> key = nodeOf(e.clicked());
    if (key.isEmpty()) {
      return;
    }
    e.setCancelled(true);
    if (e.hand() == Hand.MAIN_HAND) {
      clicked(e.player(), key.get(), NodeGestures.Click.RIGHT);
    }
  }

  @Handle(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void on(PrePlayerAttackEntityEvent e) {
    if (attacked(e.player(), e.attacked())) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void on(EntityDamageByEntityEvent e) {
    if (e.damager() instanceof PlayerRef player && attacked(player, e.entity())) {
      e.setCancelled(true);
    }
  }

  private boolean attacked(PlayerRef player, EntityRef target) {
    if (areas.swallows(player)) {
      return false;
    }
    Optional<NodeKey> key = nodeOf(target);
    if (key.isEmpty()) {
      return false;
    }
    clicked(player, key.get(), NodeGestures.Click.LEFT);
    return true;
  }

  private void clicked(PlayerRef player, NodeKey key, NodeGestures.Click click) {
    NodeRecord record = GolemancyRuntime.nodes().flatMap(service -> service.node(key)).orElse(null);
    if (record == null) {
      return;
    }
    PlayerInventoryRef inventory = player.inventory();
    ItemRef mainHand = inventory.mainHand();
    NodeGestures.Action action = NodeGestures.route(click, player.isSneaking(), isCrook(mainHand), isCrook(inventory.offHand()), mainHand.type());
    if (action == NodeGestures.Action.NONE) {
      return;
    }
    if (!record.owner().equals(player.uuid())) {
      GolemGuiSupport.warn(player, Text.key("adapt.golemancy.node_message.not_owner"));
      return;
    }
    switch (action) {
      case NONE -> {
      }
      case OPEN_GUI -> NodeGui.open(player, key, prompt, areas);
      case NEXT_CHANNEL -> setChannel(player, key, current -> NodeEdits.nextChannel(current, true));
      case SET_CHANNEL -> {
        DyeColor dye = NodeEdits.dyeChannel(mainHand.type()).orElse(null);
        setChannel(player, key, current -> dye);
      }
      case TOGGLE_FILTER -> toggleFilter(player, key, mainHand);
      case CYCLE_PRIORITY -> cyclePriority(player, key);
      case REMOVE -> remove(player, key, record);
    }
  }

  private void setChannel(PlayerRef player, NodeKey key, UnaryOperator<DyeColor> channel) {
    edit(player, key, current -> current.toBuilder().channel(channel.apply(current.channel())).build(), updated -> {
      int mask = updated.channel() == null ? ChannelMask.NONE : ChannelMask.of(updated.channel());
      GolemGuiSupport.notify(player, Text.key("adapt.golemancy.gui.channel").with("channel", GolemGuiActions.channelName(mask)));
    });
  }

  private void toggleFilter(PlayerRef player, NodeKey key, ItemRef held) {
    int slots = NodeGui.filterSlots(NodeGui.level(player));
    ItemRef ghost = NodeFilters.ghost(held);
    String name = GolemGuiSupport.itemName(ghost);
    AtomicReference<NodeFilters.Outcome> outcome = new AtomicReference<>(NodeFilters.Outcome.UNCHANGED);
    edit(player, key, current -> {
      NodeFilters.FilterEdit change = NodeFilters.toggleItem(current, ghost, slots);
      outcome.set(change.outcome());
      return change.record();
    }, updated -> {
      switch (outcome.get()) {
        case ADDED -> GolemGuiSupport.notify(player, Text.key("adapt.golemancy.node_message.filter_added").with("item", name));
        case REMOVED -> GolemGuiSupport.notify(player, Text.key("adapt.golemancy.node_message.filter_removed").with("item", name));
        case FULL -> GolemGuiSupport.warn(player, Text.key("adapt.golemancy.node_message.filter_full").withMarkup("count", String.valueOf(slots)));
        case REPLACED, UNCHANGED -> {
        }
      }
    });
  }

  private void cyclePriority(PlayerRef player, NodeKey key) {
    int level = NodeGui.level(player);
    if (!NodeRules.allowsPriority(level)) {
      GolemGuiSupport.warn(player, Text.key("adapt.golemancy.node_message.priority_locked").withMarkup("level", String.valueOf(NodeRules.PRIORITY_LEVEL)));
      return;
    }
    edit(player, key, current -> NodeEdits.cyclePriority(current, level, true).orElse(current),
        updated -> GolemGuiSupport.notify(player, Text.key("adapt.golemancy.node_gui.priority").withMarkup("priority", String.valueOf(updated.priority()))));
  }

  private void remove(PlayerRef player, NodeKey key, NodeRecord record) {
    onAnchor(player, key, service -> {
      if (service.remove(player, key)) {
        GolemGuiSupport.notify(player, Text.key("adapt.golemancy.node_message.removed").with("node", record.type().displayName()));
      } else {
        GolemGuiSupport.warn(player, Text.key("adapt.golemancy.node_message.remove_failed"));
      }
    });
  }

  private void edit(PlayerRef player, NodeKey key, UnaryOperator<NodeRecord> change, Consumer<NodeRecord> feedback) {
    UUID owner = player.uuid();
    onAnchor(player, key, service -> service.update(owner, key, change)
        .ifPresentOrElse(feedback, () -> GolemGuiSupport.warn(player, Text.key("adapt.golemancy.node_message.gone"))));
  }

  private static void onAnchor(PlayerRef player, NodeKey key, Consumer<NodeService> task) {
    WorldRef world = AdaptApi.platform().host().world(key.world());
    Optional<NodeService> service = GolemancyRuntime.nodes();
    if (world == null || service.isEmpty() || !AdaptApi.scheduler().runAt(key.center(world), () -> task.accept(service.get()))) {
      GolemGuiSupport.warn(player, Text.key("adapt.golemancy.node_message.gone"));
    }
  }

  private static Optional<NodeKey> nodeOf(EntityRef entity) {
    if (!entity.type().equals(INTERACTION)) {
      return Optional.empty();
    }
    return GolemancyRuntime.nodes().flatMap(service -> service.renderer().nodeOf(entity));
  }

  private static boolean isCrook(ItemRef item) {
    return GolemItems.kindOf(item).filter(kind -> kind == GolemItemKind.CROOK).isPresent();
  }
}
