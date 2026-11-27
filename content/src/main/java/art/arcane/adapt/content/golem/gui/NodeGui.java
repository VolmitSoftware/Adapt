package art.arcane.adapt.content.golem.gui;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;

import art.arcane.adapt.content.golem.ChannelMask;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.content.golem.node.NodeCuboid;
import art.arcane.adapt.content.golem.node.NodeArea;
import art.arcane.adapt.content.golem.node.NodeEdits;
import art.arcane.adapt.content.golem.node.NodeFilters;
import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.content.golem.node.NodeRules;
import art.arcane.adapt.content.golem.node.RedstoneMode;
import art.arcane.adapt.api.text.TextFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.UnaryOperator;


public final class NodeGui {
  private static final int ROWS = 5;
  private static final int HEADER_ROW = 0;
  private static final int AREA_ROW = 1;
  private static final int FILTER_ROW = 2;
  private static final int PANEL_ROW = 4;
  private static final int LEFT_EDGE = -4;

  private final PlayerRef player;
  private final NodeKey key;
  private final NodeTagPrompt prompt;
  private final NodeAreaSelector areas;
  private final LiveRefresh<NodeRecord> live;
  private GolemWindow window;
  private NodeRecord shownRecord;
  private int shownLevel;
  private List<String> shownPanel = List.of();
  private boolean channelPicker;

  private NodeGui(PlayerRef player, NodeKey key, NodeTagPrompt prompt, NodeAreaSelector areas) {
    this.player = player;
    this.key = key;
    this.prompt = prompt;
    this.areas = areas;
    this.live = new LiveRefresh<>(new LiveRefresh.Hooks<>(
        this::runAtAnchor,
        (task, delay, retired) -> AdaptApi.scheduler().runEntity(player, task, delay, retired),
        this::read, this::refreshed, this::gone, this::isOpen));
  }

  public static void open(PlayerRef player, NodeKey key, NodeTagPrompt prompt, NodeAreaSelector areas) {
    NodeGui gui = new NodeGui(player, key, prompt, areas);
    gui.onAnchor(gui::snapshot);
  }

  static void openAfter(PlayerRef player, NodeKey key, NodeTagPrompt prompt, NodeAreaSelector areas, UnaryOperator<NodeRecord> change) {
    new NodeGui(player, key, prompt, areas).edit(change);
  }

  static int level(PlayerRef player) {
    return GolemancyRuntime.nodeWeaving().map(policy -> policy.level(player)).orElse(0);
  }

  static int filterSlots(int level) {
    return GolemancyRuntime.nodeWeaving().map(policy -> policy.filterSlots(level)).orElse(0);
  }

  private void onAnchor(Runnable task) {
    WorldRef world = AdaptApi.platform().host().world(key.world());
    if (world == null || !AdaptApi.scheduler().runAt(key.center(world), task)) {
      GolemGuiSupport.warn(player, Text.key("adapt.golemancy.node_message.gone"));
    }
  }

  private boolean runAtAnchor(Runnable task, int delayTicks, Runnable retired) {
    WorldRef world = AdaptApi.platform().host().world(key.world());
    return world != null && AdaptApi.scheduler().runAt(key.center(world), task, delayTicks);
  }

  private Optional<NodeRecord> read() {
    return GolemancyRuntime.nodes().flatMap(service -> service.node(key))
        .filter(found -> found.owner().equals(player.uuid()));
  }

  private boolean isOpen() {
    return player.isOnline() && window != null && window.isVisible();
  }

  private void snapshot() {
    Optional<NodeRecord> record = read();
    if (record.isEmpty()) {
      gone();
      return;
    }
    AdaptApi.scheduler().runEntity(player, () -> present(record.get()));
  }

  private void present(NodeRecord record) {
    render(record);
    live.start();
  }

  private void refreshed(NodeRecord record) {
    int level = level(player);
    if (record.equals(shownRecord) && level == shownLevel && panelLinesOf(record, level).equals(shownPanel)) {
      return;
    }
    render(record);
  }

  private List<String> panelLinesOf(NodeRecord record, int level) {
    return Text.renderFor(player.uuid(), () -> {
      return panelLines(panelControls(record, level));
    });
  }

  private void edit(UnaryOperator<NodeRecord> change) {
    onAnchor(() -> {
      WorldRef world = AdaptApi.platform().host().world(key.world());
      Optional<NodeRecord> updated = GolemancyRuntime.nodes().flatMap(service -> service.update(player.uuid(), key, current -> {
        NodeRecord next = change.apply(current);
        if (!next.area().equals(current.area()) && (world == null || !NodeArea.inWorld(key, next.area(), world.minHeight(), world.maxHeight()))) {
          GolemGuiSupport.warn(player, Text.key("adapt.golemancy.node.editor.world_limit"));
          return current;
        }
        return next;
      }));
      if (updated.isEmpty()) {
        gone();
        return;
      }
      AdaptApi.scheduler().runEntity(player, () -> present(updated.get()));
    });
  }

  private void gone() {
    live.stop();
    GolemGuiSupport.warn(player, Text.key("adapt.golemancy.node_message.gone"));
    AdaptApi.scheduler().runEntity(player, () -> {
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
    Text.renderFor(player.uuid(), () -> {
      List<NodePanelControl> controls = panelControls(record, level);
      GolemWindow next = new GolemWindow(player);
      GolemTheme.apply(next, "golemancy-node");
      next.setViewportHeight(ROWS);
      next.setFreePlayerInventory(true);
      if (channelPicker) {
        placeChannels(next, record);
      } else {
        placeHeader(next, record, level, slots);
        placeArea(next, record, maxRadius);
        placeFilter(next, record, slots);
        placePanel(next, controls);
      }
      String nodeTitle = Text.key("adapt.golemancy.item.node.name").with("node", record.type().displayName()).resolve();
      next.setTitle(channelPicker ? Text.key("adapt.golemancy.node.editor.channel_title").resolve() : nodeTitle);
      next.setLayout(channelPicker ? MenuLayout.LIST : MenuLayout.GRID);
      if (channelPicker) {
        next.setBreadcrumb(List.of(nodeTitle));
      }
      window = next;
      shownRecord = record;
      shownLevel = level;
      shownPanel = panelLines(controls);
      next.onClosed(this::closed);
      next.open();
    });
  }

  private void closed(GolemWindow closed) {
    if (closed == window) {
      live.stop();
    }
  }

  private void placeHeader(GolemWindow target, NodeRecord record, int level, int slots) {
    target.setElement(LEFT_EDGE, HEADER_ROW, itemElement("node-header", GolemItems.node(record.type()))
        .setName(TextFormat.AQUA + Text.key("adapt.golemancy.item.node.name").with("node", record.type().displayName()).resolve())
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.node_gui.position").withMarkup("x", String.valueOf(key.x())).withMarkup("y", String.valueOf(key.y())).withMarkup("z", String.valueOf(key.z())).resolve())
        .setRole(MenuRole.INFO));
    int mask = record.channel() == null ? ChannelMask.NONE : ChannelMask.of(record.channel());
    String channel = GolemGuiActions.channelName(mask).resolve();
    target.setElement(-2, HEADER_ROW, GolemGuiActions.channelElement("node-channel", mask)
        .setName(TextFormat.YELLOW + Text.key("adapt.golemancy.gui.channel").withMarkup("channel", String.valueOf(channel)).resolve())
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.node.editor.channel_pick").resolve())
        .onLeftClick(clicked -> {
          channelPicker = true;
          render(record);
        })
        .setRole(MenuRole.NAVIGATION)
        .setValue(channel)
        .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.node.editor.channel_pick").resolve()));
    target.setElement(-3, HEADER_ROW, GolemTheme.element("node-copy", Id.minecraft("writable_book"), "gui", "golemancy", "copy")
        .setName(TextFormat.YELLOW + Text.key("adapt.golemancy.node.editor.copy").resolve())
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.node.editor.copy_lore").resolve())
        .onLeftClick(clicked -> onAnchor(() -> read().ifPresent(current -> {
          if (NodeSettingsClipboard.copy(player.uuid(), current)) {
            GolemGuiSupport.notify(player, Text.key("adapt.golemancy.node.editor.copied"));
          }
        })))
        .setRole(MenuRole.ACTION)
        .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.node.editor.copy").resolve()));
    target.setElement(4, HEADER_ROW, GolemTheme.element("node-paste", Id.minecraft("book"), "gui", "golemancy", "paste")
        .setName(TextFormat.YELLOW + Text.key("adapt.golemancy.node.editor.paste").resolve())
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.node.editor.paste_lore").resolve())
        .onLeftClick(clicked -> paste())
        .setRole(MenuRole.ACTION)
        .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.node.editor.paste").resolve()));
    GolemElement priority = GolemTheme.element("node-priority", Id.minecraft("gold_nugget"), "gui", "golemancy", "priority")
        .setName(TextFormat.YELLOW + Text.key("adapt.golemancy.node_gui.priority").withMarkup("priority", String.valueOf(record.priority())).resolve())
        .setCount(record.priority())
        .setValue(Integer.toString(record.priority()));
    if (NodeRules.allowsPriority(level)) {
      priority.addLore(TextFormat.GRAY + Text.key("adapt.golemancy.gui.cycle").resolve())
          .onLeftClick(clicked -> edit(current -> NodeEdits.cyclePriority(current, level, true).orElse(current)))
          .onRightClick(clicked -> edit(current -> NodeEdits.cyclePriority(current, level, false).orElse(current)))
          .setHint(MenuHint.number(Integer.toString(record.priority()), (double) NodeRecord.MIN_PRIORITY, (double) NodeRecord.MAX_PRIORITY, 1D, null));
      cycleLabels(priority);
    } else {
      lockedLore(priority.setRole(MenuRole.INFO), NodeRules.PRIORITY_LEVEL);
    }
    target.setElement(-1, HEADER_ROW, priority);
    String redstoneMode = redstoneName(record.redstone()).resolve();
    GolemElement redstone = GolemTheme.element("node-redstone", redstoneIcon(record.redstone()), "gui", "golemancy", "redstone")
        .setName(TextFormat.YELLOW + Text.key("adapt.golemancy.node_gui.redstone").withMarkup("mode", String.valueOf(redstoneMode)).resolve())
        .setValue(redstoneMode);
    if (NodeRules.allowsRedstone(level)) {
      List<String> modes = new ArrayList<>();
      for (RedstoneMode mode : RedstoneMode.values()) {
        modes.add(redstoneName(mode).resolve());
      }
      redstone.addLore(TextFormat.GRAY + Text.key("adapt.golemancy.gui.cycle").resolve())
          .onLeftClick(clicked -> edit(current -> current.toBuilder().redstone(NodeEdits.nextRedstone(current.redstone(), true)).build()))
          .onRightClick(clicked -> edit(current -> current.toBuilder().redstone(NodeEdits.nextRedstone(current.redstone(), false)).build()))
          .setHint(MenuHint.choice(redstoneMode, modes, null));
      cycleLabels(redstone);
    } else {
      lockedLore(redstone.setRole(MenuRole.INFO), NodeRules.REDSTONE_LEVEL);
    }
    target.setElement(0, HEADER_ROW, redstone);
    placeFilterMode(target, record, level, slots);
  }

  private void placeFilterMode(GolemWindow target, NodeRecord record, int level, int slots) {
    boolean unlocked = NodeRules.allowsBlacklistAndTags(level);
    String modeName = (record.whitelist() ? Text.key("adapt.golemancy.node_gui.whitelist") : Text.key("adapt.golemancy.node_gui.blacklist")).resolve();
    GolemElement mode = GolemTheme.element("node-filter-mode", record.whitelist() ? Id.minecraft("white_wool") : Id.minecraft("black_wool"), "gui", "golemancy",
            record.whitelist() ? "whitelist" : "blacklist")
        .setName(TextFormat.YELLOW + modeName)
        .addLore(TextFormat.GRAY + (record.whitelist() ? Text.key("adapt.golemancy.node_gui.whitelist_lore") : Text.key("adapt.golemancy.node_gui.blacklist_lore")).resolve())
        .addLore(TextFormat.DARK_GRAY + (record.type() == NodeType.FILTER ? Text.key("adapt.golemancy.sorter_core.empty_filter")
            : Text.key("adapt.golemancy.node_gui.empty_filter")).resolve())
        .setValue(modeName);
    if (unlocked) {
      mode.setDescription(mode.getLore())
          .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.node_gui.toggle").resolve())
          .onLeftClick(clicked -> edit(current -> current.toBuilder().whitelist(!current.whitelist()).build()))
          .setHint(MenuHint.choice(modeName, List.of(Text.key("adapt.golemancy.node_gui.whitelist").resolve(),
              Text.key("adapt.golemancy.node_gui.blacklist").resolve()), null))
          .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.node_gui.toggle").resolve());
    } else {
      lockedLore(mode.setRole(MenuRole.INFO), NodeRules.BLACKLIST_LEVEL);
    }
    target.setElement(2, HEADER_ROW, mode);
    GolemElement tag = GolemTheme.element("node-tag-add", Id.minecraft("name_tag"), "gui", "golemancy", "tag_add")
        .setName(TextFormat.GREEN + Text.key("adapt.golemancy.node_gui.tag_add").resolve());
    if (unlocked) {
      tag.addLore(TextFormat.GRAY + Text.key("adapt.golemancy.node_gui.tag_add_lore").resolve())
          .onLeftClick(clicked -> beginTag(record, slots))
          .setHint(MenuHint.text("", prompt.input(key)))
          .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.node_gui.tag_add").resolve());
    } else {
      lockedLore(tag.setRole(MenuRole.INFO), NodeRules.BLACKLIST_LEVEL);
    }
    target.setElement(3, HEADER_ROW, tag);
  }

  private void placeArea(GolemWindow target, NodeRecord record, int maxRadius) {
    NodeEdits.Axis[] axes = NodeEdits.Axis.values();
    int controls = axes.length + 1;
    NodeCuboid area = record.area();
    for (int index = 0; index < axes.length; index++) {
      NodeEdits.Axis axis = axes[index];
      String size = TextFormat.GRAY + Text.key("adapt.golemancy.node.editor.size").withMarkup("x", String.valueOf(area.spanX())).withMarkup("y", String.valueOf(area.spanY())).withMarkup("z", String.valueOf(area.spanZ())).resolve();
      String preview = TextFormat.GRAY + Text.key("adapt.golemancy.node.editor.resize_preview").withMarkup("axis", String.valueOf(axis.name())).withMarkup("grow", String.valueOf(resizePreview(area, axis, NodeEdits.STEP, maxRadius))).withMarkup("shrink", String.valueOf(resizePreview(area, axis, -NodeEdits.STEP, maxRadius))).resolve();
      String cap = TextFormat.DARK_GRAY + Text.key("adapt.golemancy.node.editor.size_cap").withMarkup("radius", String.valueOf(maxRadius)).resolve();
      String extent = Integer.toString(span(area, axis));
      target.setElement(GolemLayout.centeredPosition(index, controls), AREA_ROW,
          GolemTheme.element("node-area-" + axis.name(), areaIcon(axis), "gui", "golemancy", "area_" + axis.name().toLowerCase(Locale.ROOT))
              .setName(TextFormat.YELLOW + Text.key("adapt.golemancy.node_gui.area").withMarkup("axis", String.valueOf(axis.name())).withMarkup("min", String.valueOf(Integer.toString(area.min(axis)))).withMarkup("max", String.valueOf(signed(area.max(axis)))).resolve())
              .addLore(size)
              .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.node.editor.size_lore").resolve())
              .addLore(preview)
              .addLore(cap)
              .setHint(MenuHint.number(extent, null))
              .setValue(extent)
              .setDescription(List.of(size, preview, cap))
              .setActionLabel(ClickKind.LEFT, signed(NodeEdits.STEP))
              .setActionLabel(ClickKind.RIGHT, signed(-NodeEdits.STEP))
              .setActionLabel(ClickKind.SHIFT_LEFT, signed(NodeEdits.SHIFT_STEP))
              .setActionLabel(ClickKind.SHIFT_RIGHT, signed(-NodeEdits.SHIFT_STEP))
              .onLeftClick(clicked -> edit(current -> NodeEdits.resize(current, axis, NodeEdits.STEP, maxRadius)))
              .onRightClick(clicked -> edit(current -> NodeEdits.resize(current, axis, -NodeEdits.STEP, maxRadius)))
              .onShiftLeftClick(clicked -> edit(current -> NodeEdits.resize(current, axis, NodeEdits.SHIFT_STEP, maxRadius)))
              .onShiftRightClick(clicked -> edit(current -> NodeEdits.resize(current, axis, -NodeEdits.SHIFT_STEP, maxRadius))));
    }
    boolean selecting = areas.selecting(player, key);
    target.setElement(GolemLayout.centeredPosition(axes.length, controls), AREA_ROW,
        GolemTheme.element("node-area-select", selecting ? Id.minecraft("barrier") : Id.minecraft("iron_hoe"), "gui", "golemancy", "area_select")
            .setName(TextFormat.GREEN + (selecting ? Text.key("adapt.golemancy.node_area.stop") : Text.key("adapt.golemancy.node.editor.shift_title")).resolve())
            .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.node.editor.shift_lore").resolve())
            .addLore(TextFormat.DARK_GRAY + Text.key("adapt.golemancy.node.editor.size_cap").withMarkup("radius", String.valueOf(maxRadius)).resolve())
            .onLeftClick(clicked -> areas.toggle(player, key, record))
            .setRole(MenuRole.ACTION)
            .setActionLabel(ClickKind.LEFT, (selecting ? Text.key("adapt.golemancy.node_area.stop") : Text.key("adapt.golemancy.node.editor.shift_title")).resolve()));
  }

  private void placeFilter(GolemWindow target, NodeRecord record, int slots) {
    List<ItemRef> items = record.filterItems();
    List<String> tags = record.filterTags();
    for (int index = 0; index < NodeRecord.MAX_FILTER_ENTRIES; index++) {
      int position = LEFT_EDGE + index % GolemLayout.WIDTH;
      int row = FILTER_ROW + index / GolemLayout.WIDTH;
      target.setElement(position, row, filterSlot(index, items, tags, slots));
    }
  }

  private GolemElement filterSlot(int index, List<ItemRef> items, List<String> tags, int slots) {
    String id = "node-filter-" + index;
    if (index < items.size()) {
      ItemRef shown = items.get(index);
      return itemElement(id, shown.copy())
          .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.node.editor.filter_sample").resolve())
          .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.node_gui.filter_clear").resolve())
          .onLeftClick(clicked -> replaceFromCursor(shown))
          .onRightClick(clicked -> edit(current -> NodeFilters.removeItem(current, shown)))
          .setRole(MenuRole.ENTRY)
          .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.node.editor.filter_sample").resolve())
          .setActionLabel(ClickKind.RIGHT, Text.key("adapt.golemancy.node_gui.filter_clear").resolve());
    }
    if (index < items.size() + tags.size()) {
      String tag = tags.get(index - items.size());
      return GolemTheme.element(id, Id.minecraft("name_tag"), "gui", "golemancy", "filter_tag")
          .setName(TextFormat.YELLOW + tag)
          .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.node_gui.tag").resolve())
          .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.node_gui.filter_clear").resolve())
          .onRightClick(clicked -> edit(current -> NodeFilters.removeTag(current, tag)))
          .setRole(MenuRole.ENTRY)
          .setDescription(List.of(TextFormat.GRAY + Text.key("adapt.golemancy.node_gui.tag").resolve()))
          .setActionLabel(ClickKind.RIGHT, Text.key("adapt.golemancy.node_gui.filter_clear").resolve());
    }
    if (index < slots) {
      return GolemTheme.element(id, Id.minecraft("lime_stained_glass_pane"), "gui", "golemancy", "slots", "filter_empty")
          .setName(TextFormat.GREEN + Text.key("adapt.golemancy.node_gui.filter_empty").resolve())
          .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.node.editor.filter_sample").resolve())
          .onLeftClick(clicked -> addFromCursor(slots))
          .setRole(MenuRole.ACTION)
          .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.node.editor.filter_sample").resolve());
    }
    return GolemTheme.element(id, Id.minecraft("black_stained_glass_pane"), "gui", "golemancy", "slots", "filter_locked")
        .setName(TextFormat.RED + Text.key("adapt.golemancy.node_gui.filter_locked").resolve())
        .addLore(TextFormat.GRAY + Text.key("adapt.golemancy.node_gui.filter_locked_lore").resolve())
        .setRole(MenuRole.INFO);
  }

  private void placePanel(GolemWindow target, List<NodePanelControl> controls) {
    int shown = Math.min(NodePanels.MAX_CONTROLS, controls.size());
    for (int index = 0; index < shown; index++) {
      target.setElement(GolemLayout.centeredPosition(index, shown), PANEL_ROW, panelElement(controls.get(index)));
    }
  }

  private List<NodePanelControl> panelControls(NodeRecord record, int level) {
    Optional<NodePanel> panel = NodePanels.of(record.type());
    if (panel.isEmpty()) {
      return List.of();
    }
    try {
      return panel.get().controls(new NodePanelContext(player, key, record, level));
    } catch (RuntimeException failure) {
      AdaptApi.log().error("The " + record.type().id() + " node panel failed to build for " + player.name() + "; the menu opens without it.", failure);
      return List.of();
    }
  }

  private static List<String> panelLines(List<NodePanelControl> controls) {
    List<String> lines = new ArrayList<>(controls.size());
    for (NodePanelControl control : controls) {
      lines.add(control.id() + "|" + control.icon().type() + "|" + control.name() + "|" + String.join("|", control.lore()));
    }
    return lines;
  }

  private GolemElement panelElement(NodePanelControl control) {
    GolemElement element = itemElement("node-panel-" + control.id(), control.icon().copy()).setName(control.name());
    for (String line : control.lore()) {
      element.addLore(line);
    }
    if (control.leftClick() != null) {
      element.onLeftClick(clicked -> edit(control.leftClick()));
    }
    if (control.open() != null) {
      element.onLeftClick(clicked -> control.open().accept(player)).setRole(MenuRole.NAVIGATION);
    }
    if (control.rightClick() != null) {
      element.onRightClick(clicked -> edit(control.rightClick()));
    }
    return element;
  }

  private void replaceFromCursor(ItemRef shown) {
    cursorGhost().ifPresent(ghost -> edit(current -> {
      NodeFilters.FilterEdit change = NodeFilters.replaceItem(current, shown, ghost);
      GolemGuiSupport.notify(player, Text.key("adapt.golemancy.node.editor.filter_copied"));
      return change.record();
    }));
  }

  private void addFromCursor(int slots) {
    cursorGhost().ifPresent(ghost -> edit(current -> {
      NodeFilters.FilterEdit change = NodeFilters.addItem(current, ghost, slots);
      if (change.outcome() == NodeFilters.Outcome.FULL) {
        GolemGuiSupport.warn(player, Text.key("adapt.golemancy.node_message.filter_full").withMarkup("count", String.valueOf(slots)));
      } else {
        GolemGuiSupport.notify(player, Text.key("adapt.golemancy.node.editor.filter_copied"));
      }
      return change.record();
    }));
  }

  private Optional<ItemRef> cursorGhost() {
    ItemRef cursor = player.inventory().cursor();
    if (cursor.isEmpty()) {
      GolemGuiSupport.warn(player, Text.key("adapt.golemancy.node.editor.filter_empty_cursor"));
      return Optional.empty();
    }
    return Optional.of(NodeFilters.ghost(cursor));
  }

  private void placeChannels(GolemWindow target, NodeRecord record) {
    DyeColor[] colors = DyeColor.values();
    for (int index = 0; index < colors.length; index++) {
      DyeColor color = colors[index];
      int mask = ChannelMask.of(color);
      target.setElement(-3 + index % 8, 1 + index / 8,
          GolemGuiActions.channelElement("node-channel-" + color.name(), mask)
              .setName((record.channel() == color ? TextFormat.GREEN : TextFormat.YELLOW) + GolemGuiActions.channelName(mask).resolve())
              .onLeftClick(clicked -> chooseChannel(color))
              .setRole(MenuRole.ENTRY)
              .setValue(record.channel() == color ? Text.key("adapt.golemancy.gui.channel_selected").resolve() : "")
              .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.gui.channel_select").resolve()));
    }
    target.setElement(0, 3, GolemTheme.element("node-channel-clear", Id.minecraft("glass_bottle"), "gui", "golemancy", "channels_clear")
        .setName(TextFormat.YELLOW + Text.key("adapt.golemancy.node.editor.channel_clear").resolve())
        .onLeftClick(clicked -> chooseChannel(null))
        .setRole(MenuRole.ENTRY)
        .setValue(record.channel() == null ? Text.key("adapt.golemancy.gui.channel_selected").resolve() : "")
        .setActionLabel(ClickKind.LEFT, Text.key("adapt.golemancy.gui.channel_select").resolve()));
    target.setElement(-4, 4, GolemTheme.element("node-channel-back", Id.minecraft("arrow"), "gui", "golemancy", "back")
        .setName(TextFormat.GRAY + Text.key("adapt.golemancy.gui.back").resolve())
        .onLeftClick(clicked -> {
          channelPicker = false;
          render(record);
        }));
  }

  private void chooseChannel(DyeColor color) {
    channelPicker = false;
    edit(current -> current.toBuilder().channel(color).build());
  }

  private void paste() {
    int level = level(player);
    int maxRadius = GolemancyRuntime.nodeWeaving().map(policy -> policy.maxAreaRadius(level)).orElse(0);
    int slots = filterSlots(level);
    edit(current -> {
      Optional<NodeRecord> pasted = NodeSettingsClipboard.pasted(player.uuid(), current, level, maxRadius, slots);
      WorldRef world = AdaptApi.platform().host().world(key.world());
      if (pasted.isEmpty() || world == null || !NodeArea.inWorld(key, pasted.get().area(), world.minHeight(), world.maxHeight())) {
        GolemGuiSupport.warn(player, Text.key("adapt.golemancy.node.editor.paste_invalid"));
        return current;
      }
      GolemGuiSupport.notify(player, Text.key("adapt.golemancy.node.editor.pasted"));
      return pasted.get();
    });
  }

  private static int span(NodeCuboid area, NodeEdits.Axis axis) {
    return area.max(axis) - area.min(axis) + 1;
  }

  private String resizePreview(NodeCuboid current, NodeEdits.Axis axis, int delta, int maxRadius) {
    NodeCuboid next = NodeEdits.resizedArea(current, axis, delta, maxRadius);
    int size = span(next, axis);
    if (current.equals(next)) {
      return Text.key("adapt.golemancy.node.editor.size_limit").withMarkup("size", String.valueOf(size)).resolve();
    }
    WorldRef world = player.world();
    if (!NodeArea.inWorld(key, next, world.minHeight(), world.maxHeight())) {
      return Text.key("adapt.golemancy.node.editor.size_world_limit").withMarkup("size", String.valueOf(size)).resolve();
    }
    return Integer.toString(size);
  }

  private void beginTag(NodeRecord record, int slots) {
    if (record.filterCount() >= slots) {
      GolemGuiSupport.warn(player, Text.key("adapt.golemancy.node_message.filter_full").withMarkup("count", String.valueOf(slots)));
      return;
    }
    prompt.begin(player, key);
  }

  private static void cycleLabels(GolemElement element) {
    element.setActionLabel(ClickKind.LEFT, Text.key("adapt.gui.common.next").resolve())
        .setActionLabel(ClickKind.RIGHT, Text.key("adapt.gui.common.previous").resolve());
  }

  private static void lockedLore(GolemElement element, int level) {
    element.addLore(TextFormat.RED + Text.key("adapt.golemancy.node_gui.locked").withMarkup("level", String.valueOf(level)).resolve());
  }

  private static GolemElement itemElement(String id, ItemRef item) {
    return new GolemElement(id).setItem(item);
  }

  private static String signed(int value) {
    return value > 0 ? "+" + value : Integer.toString(value);
  }

  private static Id areaIcon(NodeEdits.Axis axis) {
    return switch (axis) {
      case X -> Id.minecraft("red_concrete");
      case Y -> Id.minecraft("lime_concrete");
      case Z -> Id.minecraft("blue_concrete");
    };
  }

  private static Id redstoneIcon(RedstoneMode mode) {
    return switch (mode) {
      case IGNORE -> Id.minecraft("redstone");
      case POWERED -> Id.minecraft("redstone_torch");
      case UNPOWERED -> Id.minecraft("lever");
    };
  }

  private static Text redstoneName(RedstoneMode mode) {
    return switch (mode) {
      case IGNORE -> Text.key("adapt.golemancy.node_gui.redstone_ignore");
      case POWERED -> Text.key("adapt.golemancy.node_gui.redstone_powered");
      case UNPOWERED -> Text.key("adapt.golemancy.node_gui.redstone_unpowered");
    };
  }
}
