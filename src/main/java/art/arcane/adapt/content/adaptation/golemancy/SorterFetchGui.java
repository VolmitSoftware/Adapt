package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeContainers;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.data.MaterialBlock;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

  void open(Player player, NodeKey key) {
    open(player, key, 0, DEFAULT_AMOUNT);
  }

  private void open(Player player, NodeKey key, int page, int amount) {
    World world = Bukkit.getWorld(key.world());
    if (world != null) {
      J.runAt(key.center(world), () -> list(player, key, world, page, amount));
    }
  }

  private void list(Player player, NodeKey key, World world, int page, int amount) {
    NodeRecord record = GolemancyRuntime.nodes().flatMap(service -> service.node(key))
        .filter(found -> found.type() == NodeType.INPUT && found.owner().equals(player.getUniqueId())).orElse(null);
    int level = core.ownerLevel(player.getUniqueId());
    if (record == null || !SorterRules.unlocked(level, core.config().fetchLevel)) {
      return;
    }
    List<SorterSurvey.Available> listing = network.survey(access(player), world, key, record, level, M.ms())
        .map(SorterSurvey::available).orElse(List.of());
    J.runEntity(player, () -> render(player, key, listing, page, amount));
  }

  private SorterAccess access(Player player) {
    return new SorterAccess() {
      @Override
      public boolean owns(Block block) {
        return J.isOwnedByCurrentRegion(block.getLocation());
      }

      @Override
      public Optional<Inventory> container(Block block) {
        return core.canAccessChest(player, block.getLocation()) ? NodeContainers.inventory(block) : Optional.empty();
      }
    };
  }

  private void render(Player player, NodeKey key, List<SorterSurvey.Available> listing, int page, int amount) {
    if (!player.isOnline()) {
      return;
    }
    GuiLayout.PagePlan plan = GuiLayout.planWithSettings(listing.size(), true, false);
    int current = GuiLayout.clampPage(page, plan.pageCount());
    int start = current * plan.itemsPerPage();
    int end = Math.min(listing.size(), start + plan.itemsPerPage());
    try (LanguageAudience.Scope audience = LanguageAudience.open(player.getUniqueId())) {
      UIWindow window = new UIWindow(Adapt.instance, player);
      GuiTheme.apply(window, "golemancy-fetch");
      window.setViewportHeight(plan.rows());
      window.batch(() -> {
        if (listing.isEmpty()) {
          window.setElement(0, 0, GuiTheme.element("fetch-empty", Material.PAPER, "gui", "golemancy", "fetch_empty")
              .setName(C.GRAY + AdaptLanguage.text(GolemancyMessages.SORTER_CORE_FETCH_EMPTY)));
        }
        for (int index = start; index < end; index++) {
          int slot = index - start;
          window.setElement(LEFT_EDGE + slot % GuiLayout.WIDTH, slot / GuiLayout.WIDTH, entry(player, window, key, index, listing.get(index), amount));
        }
        placeControls(window, player, key, listing, plan.rows() - 1, current, plan.pageCount(), amount);
      });
      window.setTitle(AdaptLanguage.text(GolemancyMessages.SORTER_CORE_FETCH));
      window.onClosed(closed -> Adapt.instance.getGuiLeftovers().remove(player.getUniqueId().toString(), window));
      window.open();
      Adapt.instance.getGuiLeftovers().put(player.getUniqueId().toString(), window);
    }
  }

  private Element entry(Player player, UIWindow window, NodeKey key, int index, SorterSurvey.Available available, int amount) {
    ItemStack shown = available.prototype().clone();
    shown.setAmount(Math.max(1, Math.min(available.count(), shown.getMaxStackSize())));
    int requested = Math.min(amount, available.count());
    return new UIElement("fetch-" + index)
        .setMaterial(new MaterialBlock(shown.getType()))
        .setBaseItemStack(shown)
        .setProgress(1D)
        .addLore(C.GRAY + AdaptLanguage.text(GolemancyMessages.SORTER_CORE_FETCH_AVAILABLE, trusted("count", available.count())))
        .addLore(C.GREEN + AdaptLanguage.text(GolemancyMessages.SORTER_CORE_FETCH_REQUEST, trusted("amount", requested)))
        .onLeftClick(clicked -> request(player, window, key, available.prototype(), requested));
  }

  private void request(Player player, UIWindow window, NodeKey key, ItemStack item, int amount) {
    fetches.request(player.getUniqueId(), key, item, amount, M.ms());
    window.close();
    SorterFeedback.requested(player, item, amount);
  }

  private void placeControls(UIWindow window, Player player, NodeKey key, List<SorterSurvey.Available> listing, int row, int page, int pages,
                             int amount) {
    if (page > 0) {
      window.setElement(LEFT_EDGE, row, GuiTheme.element("fetch-prev", Material.ARROW, "gui", "navigation", "previous")
          .setName(C.WHITE + AdaptLanguage.text(GuiMessages.PREVIOUS))
          .onLeftClick(clicked -> render(player, key, listing, page - 1, amount)));
    }
    if (page < pages - 1) {
      window.setElement(RIGHT_EDGE, row, GuiTheme.element("fetch-next", Material.ARROW, "gui", "navigation", "next")
          .setName(C.WHITE + AdaptLanguage.text(GuiMessages.NEXT))
          .onLeftClick(clicked -> render(player, key, listing, page + 1, amount)));
    }
    for (int index = 0; index < AMOUNTS.size(); index++) {
      int choice = AMOUNTS.get(index);
      boolean selected = choice == amount;
      window.setElement(GuiLayout.centeredPosition(index, AMOUNTS.size()), row,
          GuiTheme.element("fetch-amount-" + choice, selected ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                  "gui", "golemancy", selected ? "fetch_amount_selected" : "fetch_amount")
              .setName((selected ? C.GREEN : C.GRAY) + AdaptLanguage.text(GolemancyMessages.SORTER_CORE_FETCH_AMOUNT, trusted("amount", choice)))
              .setCount(choice)
              .setProgress(1D)
              .onLeftClick(clicked -> render(player, key, listing, page, choice)));
    }
  }
}
