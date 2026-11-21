package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.adapt.util.common.scheduling.Tasks;

import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeArea;
import art.arcane.adapt.api.golem.node.NodeEdits;
import art.arcane.adapt.api.golem.node.NodeFilters;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeRules;
import art.arcane.adapt.api.golem.node.RedstoneMode;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.localization.catalog.NodeEditorMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.ArrayList;
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

  private final PlayerRef player;
  private final NodeKey key;
  private final NodeTagPrompt prompt;
  private final NodeAreaSelector areas;
  private final LiveRefresh<NodeRecord> live;
  private GuiWindow window;
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
        (task, delay, retired) -> Tasks.runEntity(player, task, delay, retired),
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
    WorldRef world = AdaptCore.platform().host().world(key.world());
    if (world == null || !Tasks.runAt(key.center(world), task)) {
      GolemGuiSupport.warn(player, GolemancyMessages.NODE_GONE);
    }
  }

  private boolean runAtAnchor(Runnable task, int delayTicks, Runnable retired) {
    WorldRef world = AdaptCore.platform().host().world(key.world());
    return world != null && Tasks.runAt(key.center(world), task, delayTicks);
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
    Tasks.runEntity(player, () -> present(record.get()));
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
    try (LanguageAudience.Scope audience = LanguageAudience.open(player.uuid())) {
      return panelLines(panelControls(record, level));
    }
  }

  private void edit(UnaryOperator<NodeRecord> change) {
    onAnchor(() -> {
      WorldRef world = AdaptCore.platform().host().world(key.world());
      Optional<NodeRecord> updated = GolemancyRuntime.nodes().flatMap(service -> service.update(player.uuid(), key, current -> {
        NodeRecord next = change.apply(current);
        if (!next.area().equals(current.area()) && (world == null || !NodeArea.inWorld(key, next.area(), world.minHeight(), world.maxHeight()))) {
          GolemGuiSupport.warn(player, NodeEditorMessages.WORLD_LIMIT);
          return current;
        }
        return next;
      }));
      if (updated.isEmpty()) {
        gone();
        return;
      }
      Tasks.runEntity(player, () -> present(updated.get()));
    });
  }

  private void gone() {
    live.stop();
    GolemGuiSupport.warn(player, GolemancyMessages.NODE_GONE);
    Tasks.runEntity(player, () -> {
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
    try (LanguageAudience.Scope audience = LanguageAudience.open(player.uuid())) {
      List<NodePanelControl> controls = panelControls(record, level);
      GuiWindow next = new GuiWindow(player);
      GuiTheme.apply(next, "golemancy-node");
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
      String nodeTitle = AdaptLanguage.text(GolemancyMessages.NODE_NAME, trusted("node", AdaptLanguage.text(record.type().displayName())));
      next.setTitle(channelPicker ? AdaptLanguage.text(NodeEditorMessages.CHANNEL_TITLE) : nodeTitle);
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
    }
  }

  private void closed(GuiWindow closed) {
    if (closed == window) {
      live.stop();
    }
  }

  private void placeHeader(GuiWindow target, NodeRecord record, int level, int slots) {
    target.setElement(LEFT_EDGE, HEADER_ROW, itemElement("node-header", GolemItems.node(record.type()))
        .setName(C.AQUA + AdaptLanguage.text(GolemancyMessages.NODE_NAME, trusted("node", AdaptLanguage.text(record.type().displayName()))))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_POSITION, trusted("x", key.x()), trusted("y", key.y()), trusted("z", key.z())))
        .setRole(MenuRole.INFO));
    int mask = record.channel() == null ? ChannelMask.NONE : ChannelMask.of(record.channel());
    String channel = AdaptLanguage.text(GolemGuiActions.channelName(mask));
    target.setElement(-2, HEADER_ROW, GolemGuiActions.channelElement("node-channel", mask)
        .setName(C.YELLOW + AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL, trusted("channel", channel)))
        .addLore(C.GRAY + AdaptLanguage.text(NodeEditorMessages.CHANNEL_PICK))
        .onLeftClick(clicked -> {
          channelPicker = true;
          render(record);
        })
        .setRole(MenuRole.NAVIGATION)
        .setValue(channel)
        .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(NodeEditorMessages.CHANNEL_PICK)));
    target.setElement(-3, HEADER_ROW, GuiTheme.element("node-copy", Id.minecraft("writable_book"), "gui", "golemancy", "copy")
        .setName(C.YELLOW + AdaptLanguage.text(NodeEditorMessages.COPY))
        .addLore(C.GRAY + AdaptLanguage.text(NodeEditorMessages.COPY_LORE))
        .onLeftClick(clicked -> onAnchor(() -> read().ifPresent(current -> {
          if (NodeSettingsClipboard.copy(player.uuid(), current)) {
            GolemGuiSupport.notify(player, NodeEditorMessages.COPIED);
          }
        })))
        .setRole(MenuRole.ACTION)
        .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(NodeEditorMessages.COPY)));
    target.setElement(4, HEADER_ROW, GuiTheme.element("node-paste", Id.minecraft("book"), "gui", "golemancy", "paste")
        .setName(C.YELLOW + AdaptLanguage.text(NodeEditorMessages.PASTE))
        .addLore(C.GRAY + AdaptLanguage.text(NodeEditorMessages.PASTE_LORE))
        .onLeftClick(clicked -> paste())
        .setRole(MenuRole.ACTION)
        .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(NodeEditorMessages.PASTE)));
    GuiElement priority = GuiTheme.element("node-priority", Id.minecraft("gold_nugget"), "gui", "golemancy", "priority")
        .setName(C.YELLOW + AdaptLanguage.text(GolemancyMessages.NODE_GUI_PRIORITY, trusted("priority", record.priority())))
        .setCount(record.priority())
        .setValue(Integer.toString(record.priority()));
    if (NodeRules.allowsPriority(level)) {
      priority.addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_CYCLE))
          .onLeftClick(clicked -> edit(current -> NodeEdits.cyclePriority(current, level, true).orElse(current)))
          .onRightClick(clicked -> edit(current -> NodeEdits.cyclePriority(current, level, false).orElse(current)))
          .setHint(MenuHint.number(Integer.toString(record.priority()), (double) NodeRecord.MIN_PRIORITY, (double) NodeRecord.MAX_PRIORITY, 1D, null));
      cycleLabels(priority);
    } else {
      lockedLore(priority.setRole(MenuRole.INFO), NodeRules.PRIORITY_LEVEL);
    }
    target.setElement(-1, HEADER_ROW, priority);
    String redstoneMode = AdaptLanguage.text(redstoneName(record.redstone()));
    GuiElement redstone = GuiTheme.element("node-redstone", redstoneIcon(record.redstone()), "gui", "golemancy", "redstone")
        .setName(C.YELLOW + AdaptLanguage.text(GolemancyMessages.NODE_GUI_REDSTONE, trusted("mode", redstoneMode)))
        .setValue(redstoneMode);
    if (NodeRules.allowsRedstone(level)) {
      List<String> modes = new ArrayList<>();
      for (RedstoneMode mode : RedstoneMode.values()) {
        modes.add(AdaptLanguage.text(redstoneName(mode)));
      }
      redstone.addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_CYCLE))
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

  private void placeFilterMode(GuiWindow target, NodeRecord record, int level, int slots) {
    boolean unlocked = NodeRules.allowsBlacklistAndTags(level);
    String modeName = AdaptLanguage.text(record.whitelist() ? GolemancyMessages.NODE_GUI_WHITELIST : GolemancyMessages.NODE_GUI_BLACKLIST);
    GuiElement mode = GuiTheme.element("node-filter-mode", record.whitelist() ? Id.minecraft("white_wool") : Id.minecraft("black_wool"), "gui", "golemancy",
            record.whitelist() ? "whitelist" : "blacklist")
        .setName(C.YELLOW + modeName)
        .addLore(C.GRAY + AdaptLanguage.text(record.whitelist() ? GolemancyMessages.NODE_GUI_WHITELIST_LORE : GolemancyMessages.NODE_GUI_BLACKLIST_LORE))
        .addLore(C.DARK_GRAY + AdaptLanguage.text(record.type() == NodeType.FILTER ? GolemancyMessages.SORTER_CORE_EMPTY_FILTER
            : GolemancyMessages.NODE_GUI_EMPTY_FILTER))
        .setValue(modeName);
    if (unlocked) {
      mode.setDescription(mode.getLore())
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_TOGGLE))
          .onLeftClick(clicked -> edit(current -> current.toBuilder().whitelist(!current.whitelist()).build()))
          .setHint(MenuHint.choice(modeName, List.of(AdaptLanguage.text(GolemancyMessages.NODE_GUI_WHITELIST),
              AdaptLanguage.text(GolemancyMessages.NODE_GUI_BLACKLIST)), null))
          .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GolemancyMessages.NODE_GUI_TOGGLE));
    } else {
      lockedLore(mode.setRole(MenuRole.INFO), NodeRules.BLACKLIST_LEVEL);
    }
    target.setElement(2, HEADER_ROW, mode);
    GuiElement tag = GuiTheme.element("node-tag-add", Id.minecraft("name_tag"), "gui", "golemancy", "tag_add")
        .setName(C.GREEN + AdaptLanguage.text(GolemancyMessages.NODE_GUI_TAG_ADD));
    if (unlocked) {
      tag.addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_TAG_ADD_LORE))
          .onLeftClick(clicked -> beginTag(record, slots))
          .setHint(MenuHint.text("", prompt.input(key)))
          .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GolemancyMessages.NODE_GUI_TAG_ADD));
    } else {
      lockedLore(tag.setRole(MenuRole.INFO), NodeRules.BLACKLIST_LEVEL);
    }
    target.setElement(3, HEADER_ROW, tag);
  }

  private void placeArea(GuiWindow target, NodeRecord record, int maxRadius) {
    NodeEdits.Axis[] axes = NodeEdits.Axis.values();
    int controls = axes.length + 1;
    NodeCuboid area = record.area();
    for (int index = 0; index < axes.length; index++) {
      NodeEdits.Axis axis = axes[index];
      String size = C.GRAY + AdaptLanguage.text(NodeEditorMessages.SIZE, trusted("x", area.spanX()), trusted("y", area.spanY()), trusted("z", area.spanZ()));
      String preview = C.GRAY + AdaptLanguage.text(NodeEditorMessages.RESIZE_PREVIEW, trusted("axis", axis.name()),
          trusted("grow", resizePreview(area, axis, NodeEdits.STEP, maxRadius)),
          trusted("shrink", resizePreview(area, axis, -NodeEdits.STEP, maxRadius)));
      String cap = C.DARK_GRAY + AdaptLanguage.text(NodeEditorMessages.SIZE_CAP, trusted("radius", maxRadius));
      String extent = Integer.toString(span(area, axis));
      target.setElement(GuiLayout.centeredPosition(index, controls), AREA_ROW,
          GuiTheme.element("node-area-" + axis.name(), areaIcon(axis), "gui", "golemancy", "area_" + axis.name().toLowerCase(Locale.ROOT))
              .setName(C.YELLOW + AdaptLanguage.text(GolemancyMessages.NODE_GUI_AREA, trusted("axis", axis.name()),
                  trusted("min", Integer.toString(area.min(axis))), trusted("max", signed(area.max(axis)))))
              .addLore(size)
              .addLore(C.GRAY + AdaptLanguage.text(NodeEditorMessages.SIZE_LORE))
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
    target.setElement(GuiLayout.centeredPosition(axes.length, controls), AREA_ROW,
        GuiTheme.element("node-area-select", selecting ? Id.minecraft("barrier") : Id.minecraft("iron_hoe"), "gui", "golemancy", "area_select")
            .setName(C.GREEN + AdaptLanguage.text(selecting ? GolemancyMessages.NODE_AREA_STOP : NodeEditorMessages.SHIFT_TITLE))
            .addLore(C.GRAY + AdaptLanguage.text(NodeEditorMessages.SHIFT_LORE))
            .addLore(C.DARK_GRAY + AdaptLanguage.text(NodeEditorMessages.SIZE_CAP, trusted("radius", maxRadius)))
            .onLeftClick(clicked -> areas.toggle(player, key, record))
            .setRole(MenuRole.ACTION)
            .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(selecting ? GolemancyMessages.NODE_AREA_STOP : NodeEditorMessages.SHIFT_TITLE)));
  }

  private void placeFilter(GuiWindow target, NodeRecord record, int slots) {
    List<ItemRef> items = record.filterItems();
    List<String> tags = record.filterTags();
    for (int index = 0; index < NodeRecord.MAX_FILTER_ENTRIES; index++) {
      int position = LEFT_EDGE + index % GuiLayout.WIDTH;
      int row = FILTER_ROW + index / GuiLayout.WIDTH;
      target.setElement(position, row, filterSlot(index, items, tags, slots));
    }
  }

  private GuiElement filterSlot(int index, List<ItemRef> items, List<String> tags, int slots) {
    String id = "node-filter-" + index;
    if (index < items.size()) {
      ItemRef shown = items.get(index);
      return itemElement(id, shown.copy())
          .addLore(C.GRAY + AdaptLanguage.text(NodeEditorMessages.FILTER_SAMPLE))
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_FILTER_CLEAR))
          .onLeftClick(clicked -> replaceFromCursor(shown))
          .onRightClick(clicked -> edit(current -> NodeFilters.removeItem(current, shown)))
          .setRole(MenuRole.ENTRY)
          .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(NodeEditorMessages.FILTER_SAMPLE))
          .setActionLabel(ClickKind.RIGHT, AdaptLanguage.text(GolemancyMessages.NODE_GUI_FILTER_CLEAR));
    }
    if (index < items.size() + tags.size()) {
      String tag = tags.get(index - items.size());
      return GuiTheme.element(id, Id.minecraft("name_tag"), "gui", "golemancy", "filter_tag")
          .setName(C.YELLOW + tag)
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_TAG))
          .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_FILTER_CLEAR))
          .onRightClick(clicked -> edit(current -> NodeFilters.removeTag(current, tag)))
          .setRole(MenuRole.ENTRY)
          .setDescription(List.of(C.GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_TAG)))
          .setActionLabel(ClickKind.RIGHT, AdaptLanguage.text(GolemancyMessages.NODE_GUI_FILTER_CLEAR));
    }
    if (index < slots) {
      return GuiTheme.element(id, Id.minecraft("lime_stained_glass_pane"), "gui", "golemancy", "slots", "filter_empty")
          .setName(C.GREEN + AdaptLanguage.text(GolemancyMessages.NODE_GUI_FILTER_EMPTY))
          .addLore(C.GRAY + AdaptLanguage.text(NodeEditorMessages.FILTER_SAMPLE))
          .onLeftClick(clicked -> addFromCursor(slots))
          .setRole(MenuRole.ACTION)
          .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(NodeEditorMessages.FILTER_SAMPLE));
    }
    return GuiTheme.element(id, Id.minecraft("black_stained_glass_pane"), "gui", "golemancy", "slots", "filter_locked")
        .setName(C.RED + AdaptLanguage.text(GolemancyMessages.NODE_GUI_FILTER_LOCKED))
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.NODE_GUI_FILTER_LOCKED_LORE))
        .setRole(MenuRole.INFO);
  }

  private void placePanel(GuiWindow target, List<NodePanelControl> controls) {
    int shown = Math.min(NodePanels.MAX_CONTROLS, controls.size());
    for (int index = 0; index < shown; index++) {
      target.setElement(GuiLayout.centeredPosition(index, shown), PANEL_ROW, panelElement(controls.get(index)));
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
      AdaptLog.warn("The " + record.type().id() + " node panel failed to build for " + player.name() + "; the menu opens without it.", failure);
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

  private GuiElement panelElement(NodePanelControl control) {
    GuiElement element = itemElement("node-panel-" + control.id(), control.icon().copy()).setName(control.name());
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
      GolemGuiSupport.notify(player, NodeEditorMessages.FILTER_COPIED);
      return change.record();
    }));
  }

  private void addFromCursor(int slots) {
    cursorGhost().ifPresent(ghost -> edit(current -> {
      NodeFilters.FilterEdit change = NodeFilters.addItem(current, ghost, slots);
      if (change.outcome() == NodeFilters.Outcome.FULL) {
        GolemGuiSupport.warn(player, GolemancyMessages.NODE_FILTER_FULL, trusted("count", slots));
      } else {
        GolemGuiSupport.notify(player, NodeEditorMessages.FILTER_COPIED);
      }
      return change.record();
    }));
  }

  private Optional<ItemRef> cursorGhost() {
    ItemRef cursor = player.inventory().cursor();
    if (cursor.isEmpty()) {
      GolemGuiSupport.warn(player, NodeEditorMessages.FILTER_EMPTY_CURSOR);
      return Optional.empty();
    }
    return Optional.of(NodeFilters.ghost(cursor));
  }

  private void placeChannels(GuiWindow target, NodeRecord record) {
    DyeColor[] colors = DyeColor.values();
    for (int index = 0; index < colors.length; index++) {
      DyeColor color = colors[index];
      int mask = ChannelMask.of(color);
      target.setElement(-3 + index % 8, 1 + index / 8,
          GolemGuiActions.channelElement("node-channel-" + color.name(), mask)
              .setName((record.channel() == color ? C.GREEN : C.YELLOW) + AdaptLanguage.text(GolemGuiActions.channelName(mask)))
              .onLeftClick(clicked -> chooseChannel(color))
              .setRole(MenuRole.ENTRY)
              .setValue(record.channel() == color ? AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL_SELECTED) : "")
              .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL_SELECT)));
    }
    target.setElement(0, 3, GuiTheme.element("node-channel-clear", Id.minecraft("glass_bottle"), "gui", "golemancy", "channels_clear")
        .setName(C.YELLOW + AdaptLanguage.text(NodeEditorMessages.CHANNEL_CLEAR))
        .onLeftClick(clicked -> chooseChannel(null))
        .setRole(MenuRole.ENTRY)
        .setValue(record.channel() == null ? AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL_SELECTED) : "")
        .setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL_SELECT)));
    target.setElement(-4, 4, GuiTheme.element("node-channel-back", Id.minecraft("arrow"), "gui", "golemancy", "back")
        .setName(C.GRAY + AdaptLanguage.text(GolemancyMessages.GUI_BACK))
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
      WorldRef world = AdaptCore.platform().host().world(key.world());
      if (pasted.isEmpty() || world == null || !NodeArea.inWorld(key, pasted.get().area(), world.minHeight(), world.maxHeight())) {
        GolemGuiSupport.warn(player, NodeEditorMessages.PASTE_INVALID);
        return current;
      }
      GolemGuiSupport.notify(player, NodeEditorMessages.PASTED);
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
      return AdaptLanguage.text(NodeEditorMessages.SIZE_LIMIT, trusted("size", size));
    }
    WorldRef world = player.world();
    if (!NodeArea.inWorld(key, next, world.minHeight(), world.maxHeight())) {
      return AdaptLanguage.text(NodeEditorMessages.SIZE_WORLD_LIMIT, trusted("size", size));
    }
    return Integer.toString(size);
  }

  private void beginTag(NodeRecord record, int slots) {
    if (record.filterCount() >= slots) {
      GolemGuiSupport.warn(player, GolemancyMessages.NODE_FILTER_FULL, trusted("count", slots));
      return;
    }
    prompt.begin(player, key);
  }

  private static void cycleLabels(GuiElement element) {
    element.setActionLabel(ClickKind.LEFT, AdaptLanguage.text(GuiMessages.NEXT))
        .setActionLabel(ClickKind.RIGHT, AdaptLanguage.text(GuiMessages.PREVIOUS));
  }

  private static void lockedLore(GuiElement element, int level) {
    element.addLore(C.RED + AdaptLanguage.text(GolemancyMessages.NODE_GUI_LOCKED, trusted("level", level)));
  }

  private static GuiElement itemElement(String id, ItemRef item) {
    return new GuiElement(id).setItem(item);
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

  private static TextKey redstoneName(RedstoneMode mode) {
    return switch (mode) {
      case IGNORE -> GolemancyMessages.NODE_GUI_REDSTONE_IGNORE;
      case POWERED -> GolemancyMessages.NODE_GUI_REDSTONE_POWERED;
      case UNPOWERED -> GolemancyMessages.NODE_GUI_REDSTONE_UNPOWERED;
    };
  }
}
