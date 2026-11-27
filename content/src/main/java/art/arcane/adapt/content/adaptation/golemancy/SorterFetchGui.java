package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuPaging;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.content.golem.gui.GolemElement;
import art.arcane.adapt.content.golem.gui.GolemTheme;
import art.arcane.adapt.content.golem.gui.GolemWindow;

import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.content.golem.gui.GolemGuiSupport;
import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.content.golem.gui.GolemLayout;

import java.util.List;
import java.util.Objects;


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
    WorldRef world = AdaptApi.platform().host().world(key.world());
    if (world == null || !AdaptApi.scheduler().runAt(key.center(world), () -> list(player, key, world, page, amount))) {
      GolemGuiSupport.warn(player, Text.key("adapt.golemancy.node_message.gone"));
    }
  }

  private void list(PlayerRef player, NodeKey key, WorldRef world, int page, int amount) {
    NodeRecord record = GolemancyRuntime.nodes().flatMap(service -> service.node(key))
        .filter(found -> found.type() == NodeType.INPUT && found.owner().equals(player.uuid())).orElse(null);
    if (record == null) {
      GolemGuiSupport.warn(player, Text.key("adapt.golemancy.node_message.gone"));
      return;
    }
    int level = core.ownerLevel(player.uuid());
    if (!GolemancyRules.unlocked(level, core.config().fetchLevel)) {
      return;
    }
    List<SorterSurvey.Available> listing = network.survey(access(player), world, key, record, level, System.currentTimeMillis())
        .map(SorterSurvey::available).orElse(List.of());
    AdaptApi.scheduler().runEntity(player, () -> render(player, key, listing, page, amount));
  }

  private SorterAccess access(PlayerRef player) {
    return new SorterAccess() {
      @Override
      public boolean owns(BlockRef block) {
        return AdaptApi.scheduler().owns(block.location());
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
    GolemLayout.PagePlan plan = GolemLayout.planWithSettings(listing.size(), true, false);
    int current = GolemLayout.clampPage(page, plan.pageCount());
    int start = current * plan.itemsPerPage();
    int end = Math.min(listing.size(), start + plan.itemsPerPage());
    Text.renderFor(player.uuid(), () -> {
      GolemWindow window = new GolemWindow(player);
      GolemTheme.apply(window, "golemancy-fetch");
      window.setViewportHeight(plan.rows());
      window.setLayout(MenuLayout.LIST);
      window.setPaging(new MenuPaging(current, plan.pageCount(), listing.size()));
      if (listing.isEmpty()) {
        window.setElement(0, 0, GolemTheme.element("fetch-empty", Id.minecraft("paper"), "gui", "golemancy", "fetch_empty")
            .setName(TextFormat.GRAY + Text.key("adapt.golemancy.sorter_core.fetch_empty").resolve())
            .setRole(MenuRole.INFO));
      }
      for (int index = start; index < end; index++) {
        int slot = index - start;
        window.setElement(LEFT_EDGE + slot % GolemLayout.WIDTH, slot / GolemLayout.WIDTH, entry(player, window, key, index, listing.get(index), amount));
      }
      placeControls(window, player, key, listing, plan.rows() - 1, current, plan.pageCount(), amount);
      window.setTitle(Text.key("adapt.golemancy.sorter_core.fetch").resolve());
      window.open();
    });
  }

  private GolemElement entry(PlayerRef player, GolemWindow window, NodeKey key, int index, SorterSurvey.Available available, int amount) {
    ItemRef shown = available.prototype().copy();
    shown.setAmount(Math.max(1, Math.min(available.count(), shown.maxStackSize())));
    int requested = Math.min(amount, available.count());
    String request = Text.key("adapt.golemancy.sorter_core.fetch_request").withMarkup("amount", String.valueOf(requested)).resolve();
    String stock = TextFormat.GRAY + Text.key("adapt.golemancy.sorter_core.fetch_available").withMarkup("count", String.valueOf(available.count())).resolve();
    return new GolemElement("fetch-" + index)
        .setItem(shown)
        .addLore(stock)
        .addLore(TextFormat.GREEN + request)
        .onLeftClick(clicked -> request(player, window, key, available.prototype(), requested))
        .setRole(MenuRole.ENTRY)
        .setValue(Integer.toString(available.count()))
        .setDescription(List.of(stock))
        .setActionLabel(ClickKind.LEFT, request);
  }

  private void request(PlayerRef player, GolemWindow window, NodeKey key, ItemRef item, int amount) {
    fetches.request(player.uuid(), key, item, amount, System.currentTimeMillis());
    window.close();
    SorterFeedback.requested(player, item, amount);
  }

  private void placeControls(GolemWindow window, PlayerRef player, NodeKey key, List<SorterSurvey.Available> listing, int row, int page, int pages,
                             int amount) {
    if (page > 0) {
      window.setElement(LEFT_EDGE, row, GolemTheme.element("fetch-prev", Id.minecraft("arrow"), "gui", "navigation", "previous")
          .setName(TextFormat.WHITE + Text.key("adapt.gui.common.previous").resolve())
          .onLeftClick(clicked -> render(player, key, listing, page - 1, amount)));
    }
    if (page < pages - 1) {
      window.setElement(RIGHT_EDGE, row, GolemTheme.element("fetch-next", Id.minecraft("arrow"), "gui", "navigation", "next")
          .setName(TextFormat.WHITE + Text.key("adapt.gui.common.next").resolve())
          .onLeftClick(clicked -> render(player, key, listing, page + 1, amount)));
    }
    for (int index = 0; index < AMOUNTS.size(); index++) {
      int choice = AMOUNTS.get(index);
      boolean selected = choice == amount;
      window.setElement(GolemLayout.centeredPosition(index, AMOUNTS.size()), row,
          GolemTheme.element("fetch-amount-" + choice, selected ? Id.minecraft("lime_stained_glass_pane") : Id.minecraft("white_stained_glass_pane"),
                  "gui", "golemancy", "fetch", selected ? "amount_selected" : "amount_available")
              .setName((selected ? TextFormat.GREEN : TextFormat.WHITE) + Text.key("adapt.golemancy.sorter_core.fetch_amount").withMarkup("amount", String.valueOf(choice)).resolve())
              .setCount(choice)
              .setEnchanted(selected)
              .onLeftClick(clicked -> render(player, key, listing, page, choice))
              .setRole(MenuRole.ENTRY)
              .setValue(selected ? Text.key("adapt.golemancy.gui.channel_selected").resolve() : ""));
    }
  }
}
