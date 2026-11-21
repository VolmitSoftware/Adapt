package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuPaging;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.adapt.util.common.scheduling.Tasks;

import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.gui.GolemGuiSupport;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.math.M;

import java.util.List;
import java.util.Objects;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

final class SorterFetchGui {
  private static final List<Integer> AMOUNTS = List.of(1, 8, 16, 32, 64);
  private static final int DEFAULT_AMOUNT = 64;
  private static final int LEFT_EDGE = -4;
  private static final int RIGHT_EDGE = 4;

  private final GolemSorterCore core;
  private final SorterNetwork network;
  private final SorterFetches fetches;

  SorterFetchGui(GolemSorterCore core, SorterNetwork network, SorterFetches fetches) {
    this.core = Objects.requireNonNull(core);
    this.network = Objects.requireNonNull(network);
    this.fetches = Objects.requireNonNull(fetches);
  }

  void open(PlayerRef player, NodeKey key) {
    open(player, key, 0, DEFAULT_AMOUNT);
  }

  private void open(PlayerRef player, NodeKey key, int page, int amount) {
    WorldRef world = AdaptCore.platform().host().world(key.world());
    if (world == null || !Tasks.runAt(key.center(world), () -> list(player, key, world, page, amount))) {
      GolemGuiSupport.warn(player, GolemancyMessages.NODE_GONE);
    }
  }

  private void list(PlayerRef player, NodeKey key, WorldRef world, int page, int amount) {
    NodeRecord record = GolemancyRuntime.nodes().flatMap(service -> service.node(key))
        .filter(found -> found.type() == NodeType.INPUT && found.owner().equals(player.uuid())).orElse(null);
    if (record == null) {
      GolemGuiSupport.warn(player, GolemancyMessages.NODE_GONE);
      return;
    }
    int level = core.ownerLevel(player.uuid());
    if (!GolemancyRules.unlocked(level, core.config().fetchLevel)) {
      return;
    }
    List<SorterSurvey.Available> listing = network.survey(access(player), world, key, record, level, M.ms())
        .map(SorterSurvey::available).orElse(List.of());
    Tasks.runEntity(player, () -> render(player, key, listing, page, amount));
  }

  private SorterAccess access(PlayerRef player) {
    return new SorterAccess() {
      @Override
      public boolean owns(BlockRef block) {
        return Tasks.isOwnedByCurrentRegion(block.location());
      }

      @Override
      public PlayerRef owner() {
        return player;
      }
    };
  }

  private void render(PlayerRef player, NodeKey key, List<SorterSurvey.Available> listing, int page, int amount) {
    if (!player.isOnline()) {
      return;
    }
    GuiLayout.PagePlan plan = GuiLayout.planWithSettings(listing.size(), true, false);
    int current = GuiLayout.clampPage(page, plan.pageCount());
    int start = current * plan.itemsPerPage();
    int end = Math.min(listing.size(), start + plan.itemsPerPage());
    try (LanguageAudience.Scope audience = LanguageAudience.open(player.uuid())) {
      GuiWindow window = new GuiWindow(player);
      GuiTheme.apply(window, "golemancy-fetch");
      window.setViewportHeight(plan.rows());
      window.setLayout(MenuLayout.LIST);
      window.setPaging(new MenuPaging(current, plan.pageCount(), listing.size()));
      if (listing.isEmpty()) {
        window.setElement(0, 0, GuiTheme.element("fetch-empty", Id.minecraft("paper"), "gui", "golemancy", "fetch_empty")
            .setName(C.GRAY + AdaptLanguage.text(GolemancyMessages.SORTER_CORE_FETCH_EMPTY))
            .setRole(MenuRole.INFO));
      }
      for (int index = start; index < end; index++) {
        int slot = index - start;
        window.setElement(LEFT_EDGE + slot % GuiLayout.WIDTH, slot / GuiLayout.WIDTH, entry(player, window, key, index, listing.get(index), amount));
      }
      placeControls(window, player, key, listing, plan.rows() - 1, current, plan.pageCount(), amount);
      window.setTitle(AdaptLanguage.text(GolemancyMessages.SORTER_CORE_FETCH));
      window.open();
    }
  }

  private GuiElement entry(PlayerRef player, GuiWindow window, NodeKey key, int index, SorterSurvey.Available available, int amount) {
    ItemRef shown = available.prototype().copy();
    shown.setAmount(Math.max(1, Math.min(available.count(), shown.maxStackSize())));
    int requested = Math.min(amount, available.count());
    String request = AdaptLanguage.text(GolemancyMessages.SORTER_CORE_FETCH_REQUEST, trusted("amount", requested));
    String stock = C.GRAY + AdaptLanguage.text(GolemancyMessages.SORTER_CORE_FETCH_AVAILABLE, trusted("count", available.count()));
    return new GuiElement("fetch-" + index)
        .setItem(shown)
        .addLore(stock)
        .addLore(C.GREEN + request)
        .onLeftClick(clicked -> request(player, window, key, available.prototype(), requested))
        .setRole(MenuRole.ENTRY)
        .setValue(Integer.toString(available.count()))
        .setDescription(List.of(stock))
        .setActionLabel(ClickKind.LEFT, request);
  }

  private void request(PlayerRef player, GuiWindow window, NodeKey key, ItemRef item, int amount) {
    fetches.request(player.uuid(), key, item, amount, M.ms());
    window.close();
    SorterFeedback.requested(player, item, amount);
  }

  private void placeControls(GuiWindow window, PlayerRef player, NodeKey key, List<SorterSurvey.Available> listing, int row, int page, int pages,
                             int amount) {
    if (page > 0) {
      window.setElement(LEFT_EDGE, row, GuiTheme.element("fetch-prev", Id.minecraft("arrow"), "gui", "navigation", "previous")
          .setName(C.WHITE + AdaptLanguage.text(GuiMessages.PREVIOUS))
          .onLeftClick(clicked -> render(player, key, listing, page - 1, amount)));
    }
    if (page < pages - 1) {
      window.setElement(RIGHT_EDGE, row, GuiTheme.element("fetch-next", Id.minecraft("arrow"), "gui", "navigation", "next")
          .setName(C.WHITE + AdaptLanguage.text(GuiMessages.NEXT))
          .onLeftClick(clicked -> render(player, key, listing, page + 1, amount)));
    }
    for (int index = 0; index < AMOUNTS.size(); index++) {
      int choice = AMOUNTS.get(index);
      boolean selected = choice == amount;
      window.setElement(GuiLayout.centeredPosition(index, AMOUNTS.size()), row,
          GuiTheme.element("fetch-amount-" + choice, selected ? Id.minecraft("lime_stained_glass_pane") : Id.minecraft("white_stained_glass_pane"),
                  "gui", "golemancy", "fetch", selected ? "amount_selected" : "amount_available")
              .setName((selected ? C.GREEN : C.WHITE) + AdaptLanguage.text(GolemancyMessages.SORTER_CORE_FETCH_AMOUNT, trusted("amount", choice)))
              .setCount(choice)
              .setEnchanted(selected)
              .onLeftClick(clicked -> render(player, key, listing, page, choice))
              .setRole(MenuRole.ENTRY)
              .setValue(selected ? AdaptLanguage.text(GolemancyMessages.GUI_CHANNEL_SELECTED) : ""));
    }
  }
}
