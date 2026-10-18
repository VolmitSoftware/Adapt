package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeEdits;
import art.arcane.adapt.api.golem.node.NodeFilters;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeRules;
import art.arcane.adapt.api.golem.node.RedstoneMode;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.data.MaterialBlock;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public final class NodeGui {
  private static final int ROWS = 5;
  private static final int HEADER_ROW = 0;
  private static final int AREA_ROW = 1;
  private static final int FILTER_ROW = 2;
  private static final int PANEL_ROW = 4;
  private static final int LEFT_EDGE = -4;

  private final Player player;
  private final NodeKey key;
  private final NodeTagPrompt prompt;
  private UIWindow window;

  private NodeGui(Player player, NodeKey key, NodeTagPrompt prompt) {
    this.player = player;
    this.key = key;
    this.prompt = prompt;
  }

  public static void open(Player player, NodeKey key, NodeTagPrompt prompt) {
    NodeGui gui = new NodeGui(player, key, prompt);
    gui.onAnchor(gui::snapshot);
  }

  static void openAfter(Player player, NodeKey key, NodeTagPrompt prompt, UnaryOperator<NodeRecord> change) {
    new NodeGui(player, key, prompt).edit(change);
  }

  static int level(Player player) {
    return GolemancyRuntime.nodeWeaving().map(policy -> policy.level(player)).orElse(0);
  }

  static int filterSlots(int level) {
    return GolemancyRuntime.nodeWeaving().map(policy -> policy.filterSlots(level)).orElse(0);
  }

  private void onAnchor(Runnable task) {
    World world = Bukkit.getWorld(key.world());
    if (world == null || !J.runAt(key.center(world), task)) {
      GolemGuiSupport.warn(player, GolemancyMessages.NODE_GONE);
    }
  }

  private void snapshot() {
    Optional<NodeRecord> record = GolemancyRuntime.nodes().flatMap(service -> service.node(key))
        .filter(found -> found.owner().equals(player.getUniqueId()));
    if (record.isEmpty()) {
      gone();
      return;
    }
    J.runEntity(player, () -> render(record.get()));
  }

  private void edit(UnaryOperator<NodeRecord> change) {
    onAnchor(() -> {
      Optional<NodeRecord> updated = GolemancyRuntime.nodes().flatMap(service -> service.update(player.getUniqueId(), key, change));
      if (updated.isEmpty()) {
        gone();
        return;
      }
      J.runEntity(player, () -> render(updated.get()));
    });
  }

  private void gone() {
    GolemGuiSupport.warn(player, GolemancyMessages.NODE_GONE);
    J.runEntity(player, () -> {
      if (window != null && window.isVisible()) {
        window.close();
      }
    });
  }

  private void render(NodeRecord record) {
    if (!player.isOnline() || (window != null && !window.isVisible())) {
      return;
    }
    int level = level(player);
    int maxRadius = GolemancyRuntime.nodeWeaving().map(policy -> policy.maxAreaRadius(level)).orElse(0);
    int slots = Math.min(NodeRecord.MAX_FILTER_ENTRIES, filterSlots(level));
    try (LanguageAudience.Scope audience = LanguageAudience.open(player.getUniqueId())) {
      UIWindow next = new UIWindow(Adapt.instance, player);
      GuiTheme.apply(next, "golemancy-node");
      next.setViewportHeight(ROWS);
      next.batch(() -> {
        placeHeader(next, record, level, slots);
        placeArea(next, record, maxRadius);
        placeFilter(next, record, slots);
        placePanel(next, record, level);
      });
      next.setTitle(AdaptLanguage.text(GolemancyMessages.NODE_NAME, trusted("node", AdaptLanguage.text(record.type().displayName()))));
      window = next;
      next.open();
      Adapt.instance.getGuiLeftovers().put(player.getUniqueId().toString(), next);
      Inventory top = UIWindow.inventoryViewTopInventory(player.getOpenInventory());
      next.onClosed(closed -> closed(next, top));
      if (top != null) {
        GuiCursor.track(player.getUniqueId(), top);
      }
    }
  }

  private void closed(UIWindow closed, Inventory top) {
    Adapt.instance.getGuiLeftovers().remove(player.getUniqueId().toString(), closed);
    if (top != null) {
      GuiCursor.untrack(player.getUniqueId(), top);
    }
  }

  private void placeHeader(UIWindow target, NodeRecord record, int level, int slots) {
    target.setElement(LEFT_EDGE, HEADER_ROW, itemElement("node-header", GolemItems.node(record.type()))
        .setName(C.AQUA + AdaptLanguage.text(GolemancyMessages.NODE_NAME, trusted("node", AdaptLanguage.text(record.type().displayName()))))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_POSITION, trusted("x", key.x()), trusted("y", key.y()), trusted("z", key.z()))));
    int mask = record.channel() == null ? ChannelMask.NONE : ChannelMask.of(record.channel());
    target.setElement(-2, HEADER_ROW, GuiTheme.element("node-channel", GolemGuiActions.channelIcon(mask), "gui", "golemancy", "channel")
        .setName(C.YELLOW + AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL, trusted("channel", AdaptLanguage.text(GolemGuiActions.channelName(mask)))))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_CYCLE))
        .setProgress(1D)
        .onLeftClick(clicked -> edit(current -> current.toBuilder().channel(NodeEdits.nextChannel(current.channel(), true)).build()))
        .onRightClick(clicked -> edit(current -> current.toBuilder().channel(NodeEdits.nextChannel(current.channel(), false)).build())));
    Element priority = GuiTheme.element("node-priority", Material.GOLD_NUGGET, "gui", "golemancy", "priority")
        .setName(C.YELLOW + AdaptLanguage.text(GolemancyMessages.NODE_GUI_PRIORITY, trusted("priority", record.priority())))
        .setCount(record.priority())
        .setProgress(1D);
    if (NodeRules.allowsPriority(level)) {
      priority.addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_CYCLE))
          .onLeftClick(clicked -> edit(current -> NodeEdits.cyclePriority(current, level, true).orElse(current)))
          .onRightClick(clicked -> edit(current -> NodeEdits.cyclePriority(current, level, false).orElse(current)));
    } else {
      lockedLore(priority, NodeRules.PRIORITY_LEVEL);
    }
    target.setElement(-1, HEADER_ROW, priority);
    Element redstone = GuiTheme.element("node-redstone", redstoneIcon(record.redstone()), "gui", "golemancy", "redstone")
        .setName(C.YELLOW + AdaptLanguage.text(GolemancyMessages.NODE_GUI_REDSTONE, trusted("mode", AdaptLanguage.text(redstoneName(record.redstone())))))
        .setProgress(1D);
    if (NodeRules.allowsRedstone(level)) {
      redstone.addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_CYCLE))
          .onLeftClick(clicked -> edit(current -> current.toBuilder().redstone(NodeEdits.nextRedstone(current.redstone(), true)).build()))
          .onRightClick(clicked -> edit(current -> current.toBuilder().redstone(NodeEdits.nextRedstone(current.redstone(), false)).build()));
    } else {
      lockedLore(redstone, NodeRules.REDSTONE_LEVEL);
    }
    target.setElement(0, HEADER_ROW, redstone);
    placeFilterMode(target, record, level, slots);
  }

  private void placeFilterMode(UIWindow target, NodeRecord record, int level, int slots) {
    boolean unlocked = NodeRules.allowsBlacklistAndTags(level);
    Element mode = GuiTheme.element("node-filter-mode", record.whitelist() ? Material.WHITE_WOOL : Material.BLACK_WOOL, "gui", "golemancy",
            record.whitelist() ? "whitelist" : "blacklist")
        .setName(C.YELLOW + AdaptLanguage.text(record.whitelist() ? GolemancyMessages.NODE_GUI_WHITELIST : GolemancyMessages.NODE_GUI_BLACKLIST))
        .addLore(C.GRAY + AdaptLanguage.text(record.whitelist() ? GolemancyMessages.NODE_GUI_WHITELIST_LORE : GolemancyMessages.NODE_GUI_BLACKLIST_LORE))
        .addLore(C.DARK_GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_EMPTY_FILTER))
        .setProgress(1D);
    if (unlocked) {
      mode.addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_TOGGLE))
          .onLeftClick(clicked -> edit(current -> current.toBuilder().whitelist(!current.whitelist()).build()));
    } else {
      lockedLore(mode, NodeRules.BLACKLIST_LEVEL);
    }
    target.setElement(2, HEADER_ROW, mode);
    Element tag = GuiTheme.element("node-tag-add", Material.NAME_TAG, "gui", "golemancy", "tag_add")
        .setName(C.GREEN + AdaptLanguage.text(GolemancyMessages.NODE_GUI_TAG_ADD))
        .setProgress(1D);
    if (unlocked) {
      tag.addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_TAG_ADD_LORE))
          .onLeftClick(clicked -> beginTag(record, slots));
    } else {
      lockedLore(tag, NodeRules.BLACKLIST_LEVEL);
    }
    target.setElement(3, HEADER_ROW, tag);
  }

  private void placeArea(UIWindow target, NodeRecord record, int maxRadius) {
    NodeEdits.Axis[] axes = NodeEdits.Axis.values();
    for (int index = 0; index < axes.length; index++) {
      NodeEdits.Axis axis = axes[index];
      target.setElement(GuiLayout.centeredPosition(index, axes.length), AREA_ROW,
          GuiTheme.element("node-area-" + axis.name(), areaIcon(axis), "gui", "golemancy", "area_" + axis.name().toLowerCase(Locale.ROOT))
              .setName(C.YELLOW + AdaptLanguage.text(GolemancyMessages.NODE_GUI_AREA, trusted("axis", axis.name()), trusted("radius", NodeEdits.radius(record, axis))))
              .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_AREA_LORE))
              .addLore(C.DARK_GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_AREA_CAP, trusted("radius", maxRadius)))
              .setProgress(1D)
              .onLeftClick(clicked -> edit(current -> NodeEdits.resize(current, axis, 1, maxRadius)))
              .onRightClick(clicked -> edit(current -> NodeEdits.resize(current, axis, -1, maxRadius))));
    }
  }

  private void placeFilter(UIWindow target, NodeRecord record, int slots) {
    List<ItemStack> items = record.filterItems();
    List<String> tags = record.filterTags();
    for (int index = 0; index < NodeRecord.MAX_FILTER_ENTRIES; index++) {
      int position = LEFT_EDGE + index % GuiLayout.WIDTH;
      int row = FILTER_ROW + index / GuiLayout.WIDTH;
      target.setElement(position, row, filterSlot(index, items, tags, slots));
    }
  }

  private Element filterSlot(int index, List<ItemStack> items, List<String> tags, int slots) {
    String id = "node-filter-" + index;
    if (index < items.size()) {
      ItemStack shown = items.get(index);
      return itemElement(id, shown.clone())
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_FILTER_SET))
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_FILTER_CLEAR))
          .onLeftClick(clicked -> replaceFromCursor(shown))
          .onRightClick(clicked -> edit(current -> NodeFilters.removeItem(current, shown)));
    }
    if (index < items.size() + tags.size()) {
      String tag = tags.get(index - items.size());
      return GuiTheme.element(id, Material.NAME_TAG, "gui", "golemancy", "filter_tag")
          .setName(C.YELLOW + tag)
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_TAG))
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_FILTER_CLEAR))
          .setProgress(1D)
          .onRightClick(clicked -> edit(current -> NodeFilters.removeTag(current, tag)));
    }
    if (index < slots) {
      return GuiTheme.element(id, Material.LIGHT_GRAY_STAINED_GLASS_PANE, "gui", "golemancy", "filter_empty")
          .setName(C.GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_FILTER_EMPTY))
          .addLore(C.DARK_GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_FILTER_SET))
          .setProgress(1D)
          .onLeftClick(clicked -> addFromCursor(slots));
    }
    return GuiTheme.element(id, Material.BARRIER, "gui", "golemancy", "filter_locked")
        .setName(C.RED + AdaptLanguage.text(GolemancyMessages.NODE_GUI_FILTER_LOCKED))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_FILTER_LOCKED_LORE))
        .setProgress(1D);
  }

  private void placePanel(UIWindow target, NodeRecord record, int level) {
    Optional<NodePanel> panel = NodePanels.of(record.type());
    if (panel.isEmpty()) {
      return;
    }
    List<NodePanelControl> controls;
    try {
      controls = panel.get().controls(new NodePanelContext(player, key, record, level));
    } catch (RuntimeException failure) {
      Adapt.warn("The " + record.type().id() + " node panel failed to build for " + player.getName() + "; the menu opens without it.", failure);
      return;
    }
    int shown = Math.min(NodePanels.MAX_CONTROLS, controls.size());
    for (int index = 0; index < shown; index++) {
      target.setElement(GuiLayout.centeredPosition(index, shown), PANEL_ROW, panelElement(controls.get(index)));
    }
  }

  private Element panelElement(NodePanelControl control) {
    Element element = itemElement("node-panel-" + control.id(), control.icon().clone()).setName(control.name());
    for (String line : control.lore()) {
      element.addLore(line);
    }
    if (control.leftClick() != null) {
      element.onLeftClick(clicked -> edit(control.leftClick()));
    }
    if (control.rightClick() != null) {
      element.onRightClick(clicked -> edit(control.rightClick()));
    }
    return element;
  }

  private void replaceFromCursor(ItemStack shown) {
    cursorGhost().ifPresent(ghost -> edit(current -> NodeFilters.replaceItem(current, shown, ghost).record()));
  }

  private void addFromCursor(int slots) {
    cursorGhost().ifPresent(ghost -> edit(current -> {
      NodeFilters.FilterEdit change = NodeFilters.addItem(current, ghost, slots);
      if (change.outcome() == NodeFilters.Outcome.FULL) {
        GolemGuiSupport.warn(player, GolemancyMessages.NODE_FILTER_FULL, trusted("count", slots));
      }
      return change.record();
    }));
  }

  private Optional<ItemStack> cursorGhost() {
    ItemStack cursor = player.getItemOnCursor();
    return cursor.getType() == Material.AIR ? Optional.empty() : Optional.of(NodeFilters.ghost(cursor));
  }

  private void beginTag(NodeRecord record, int slots) {
    if (record.filterCount() >= slots) {
      GolemGuiSupport.warn(player, GolemancyMessages.NODE_FILTER_FULL, trusted("count", slots));
      return;
    }
    prompt.begin(player, key);
  }

  private static void lockedLore(Element element, int level) {
    element.addLore(C.RED + AdaptLanguage.text(GolemancyMessages.NODE_GUI_LOCKED, trusted("level", level)));
  }

  private static Element itemElement(String id, ItemStack item) {
    return new UIElement(id)
        .setMaterial(new MaterialBlock(item.getType()))
        .setBaseItemStack(item)
        .setProgress(1D);
  }

  private static Material areaIcon(NodeEdits.Axis axis) {
    return switch (axis) {
      case X -> Material.RED_CONCRETE;
      case Y -> Material.LIME_CONCRETE;
      case Z -> Material.BLUE_CONCRETE;
    };
  }

  private static Material redstoneIcon(RedstoneMode mode) {
    return switch (mode) {
      case IGNORE -> Material.REDSTONE;
      case POWERED -> Material.REDSTONE_TORCH;
      case UNPOWERED -> Material.LEVER;
    };
  }

  private static TextKey redstoneName(RedstoneMode mode) {
    return switch (mode) {
      case IGNORE -> GolemancyMessages.NODE_GUI_REDSTONE_IGNORE;
      case POWERED -> GolemancyMessages.NODE_GUI_REDSTONE_POWERED;
      case UNPOWERED -> GolemancyMessages.NODE_GUI_REDSTONE_UNPOWERED;
    };
  }
}
