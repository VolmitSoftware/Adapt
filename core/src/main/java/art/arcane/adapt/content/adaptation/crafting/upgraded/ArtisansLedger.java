package art.arcane.adapt.content.adaptation.crafting.upgraded;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.mutation.tool.ToolUse;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.content.adaptation.crafting.LedgerWorkbench;
import art.arcane.adapt.content.adaptation.crafting.PortableStations;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CraftingMessages;
import art.arcane.adapt.platform.entity.CraftingStation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuIcon;
import art.arcane.adapt.platform.menu.MenuSession;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * Field Workshop's Ledger. Right-click opens its pages. Drop a crafted item from the cursor onto an empty page to keep
 * its recipe, or a workstation to keep the station. Click a recipe page to craft it from the inventory and backpacks,
 * sneak-click to craft as many as the materials and batch allow, right-click to clear it. A station page opens the
 * station while you carry one. Parts stored on other pages are crafted on the way, up to the chain depth.
 */
public final class ArtisansLedger extends MutationTool<ArtisansLedger.Data> {
  static final String ID = "tool-crafting-artisans-ledger";

  private final FieldWorkshop workshop;
  private final LedgerWorkbench bench;

  ArtisansLedger(FieldWorkshop workshop) {
    super(ID, workshop, Id.minecraft("written_book"), 1, Data.class, CraftingMessages.LEDGER_NAME);
    this.workshop = workshop;
    this.bench = new LedgerWorkbench(workshop);
  }

  /** Stored pages; an empty page is null. */
  public static final class Data {
    List<Page> pages = new ArrayList<>();
  }

  /** A page holds a recipe result or a workstation. */
  public static final class Page {
    String result;
    String station;
  }

  @Override
  public List<RecipeSpec> recipes() {
    return List.of(new RecipeSpec.Shapeless(Id.adapt(ID), List.of(Ingredient.of(Id.minecraft("book")),
        Ingredient.of(Id.minecraft("leather")), Ingredient.of(Id.minecraft("crafting_table"))), create(new Data()), 1));
  }

  @Override
  protected void applyLore(Data data, List<String> lines) {
    int filled = 0;
    for (Page page : data.pages) {
      if (page != null) {
        filled++;
      }
    }
    lines.add(C.GRAY + AdaptLanguage.text(CraftingMessages.LEDGER_FILLED, trusted("pages", String.valueOf(filled))));
  }

  @Override
  protected void use(ToolUse use) {
    if (use.rightClick()) {
      AdaptCore.platform().menus().open(use.player(), menu(use.player(), use.level(), stored(use.item())));
    }
  }

  private Data stored(ItemRef item) {
    Data data = read(item);
    return data == null ? new Data() : data;
  }

  private ChestMenu menu(PlayerRef p, int level, Data data) {
    int pages = workshop.pages(level);
    List<Id> results = results(data, pages);
    int depth = workshop.chainDepth(level);
    LedgerWorkbench.Bench carried = LedgerWorkbench.Bench.of(p.inventory());
    ChestMenu.Builder menu = ChestMenu.builder(C.DARK_AQUA + AdaptLanguage.text(CraftingMessages.LEDGER_NAME),
        Math.min(ChestMenu.MAX_ROWS, (pages + ChestMenu.COLUMNS - 1) / ChestMenu.COLUMNS)).freePlayerInventory(true);
    for (int index = 0; index < Math.min(pages, ChestMenu.MAX_ROWS * ChestMenu.COLUMNS); index++) {
      int page = index;
      menu.set(index, new MenuButton(icon(p, carried, page(data, index), results, depth),
          (MenuClick click) -> click(click.viewer(), click.session(), page, click.kind())));
    }
    return menu.build();
  }

  private MenuIcon icon(PlayerRef p, LedgerWorkbench.Bench carried, Page page, List<Id> results, int depth) {
    if (page == null) {
      return MenuIcon.of(Id.minecraft("paper"), C.GRAY + AdaptLanguage.text(CraftingMessages.LEDGER_EMPTY), List.of());
    }
    if (page.station != null) {
      return MenuIcon.of(AdaptCore.platform().host().items().create(Id.parse(page.station), 1));
    }
    Id result = Id.parse(page.result);
    ItemRef shown = AdaptCore.platform().host().items().create(result, 1);
    Map<Id, Integer> missing = bench.missing(p, carried, result, results, depth);
    if (!missing.isEmpty()) {
      shown.setLore(List.of(C.RED + missingText(missing)));
    }
    return MenuIcon.of(shown);
  }

  private void click(PlayerRef p, MenuSession session, int index, ClickKind kind) {
    ItemRef hand = p.inventory().mainHand();
    int level = level(p);
    if (!is(hand) || level <= 0) {
      session.close();
      return;
    }
    Data data = stored(hand);
    Page page = page(data, index);
    ItemRef cursor = p.inventory().cursor();
    if (page == null) {
      if (cursor != null && !cursor.isEmpty() && store(p, data, index, cursor.type())) {
        save(p, hand, data);
      }
    } else if (kind.isRight()) {
      data.pages.set(index, null);
      save(p, hand, data);
    } else if (page.station != null) {
      openStation(p, Id.parse(page.station));
      return;
    } else {
      craft(p, Id.parse(page.result), data, level, kind.isShift());
    }
    if (session.isOpen()) {
      session.update(menu(p, level, data));
    }
  }

  private boolean store(PlayerRef p, Data data, int index, Id type) {
    Page page = new Page();
    if (PortableStations.station(type) != null) {
      page.station = type.toString();
    } else if (!bench.recipes(p, type).isEmpty()) {
      page.result = type.toString();
    } else {
      AdaptHud.actionBar(p, C.RED + AdaptLanguage.text(CraftingMessages.LEDGER_UNKNOWN));
      return false;
    }
    while (data.pages.size() <= index) {
      data.pages.add(null);
    }
    data.pages.set(index, page);
    return true;
  }

  private void craft(PlayerRef p, Id result, Data data, int level, boolean batch) {
    int limit = batch ? bench.batchLimit(p, result) : 1;
    LedgerWorkbench.Result made = bench.craft(p, result, results(data, workshop.pages(level)), workshop.chainDepth(level),
        limit);
    if (made.crafts() > 0) {
      workshop.crafted(p, made.crafts());
    } else if (!made.missing().isEmpty()) {
      AdaptHud.actionBar(p, C.RED + missingText(made.missing()));
    }
  }

  private void openStation(PlayerRef p, Id type) {
    CraftingStation station = PortableStations.station(type);
    if (station == null) {
      return;
    }
    if (!LedgerWorkbench.carries(p, type)) {
      AdaptHud.actionBar(p, C.RED + missingText(Map.of(type, 1)));
      return;
    }
    workshop.openStation(p, type, station);
  }

  private void save(PlayerRef p, ItemRef hand, Data data) {
    write(hand, data);
    p.inventory().setMainHand(hand);
  }

  private static Page page(Data data, int index) {
    return index < data.pages.size() ? data.pages.get(index) : null;
  }

  private static List<Id> results(Data data, int pages) {
    List<Id> results = new ArrayList<>();
    for (int index = 0; index < Math.min(pages, data.pages.size()); index++) {
      Page page = data.pages.get(index);
      if (page != null && page.result != null) {
        results.add(Id.parse(page.result));
      }
    }
    return results;
  }

  private static String missingText(Map<Id, Integer> missing) {
    List<String> parts = new ArrayList<>();
    for (Map.Entry<Id, Integer> part : missing.entrySet()) {
      parts.add(part.getValue() + " " + Form.capitalizeWords(part.getKey().path().toLowerCase(Locale.ROOT).replace('_', ' ')));
    }
    return AdaptLanguage.text(CraftingMessages.LEDGER_MISSING, trusted("items", String.join(", ", parts)));
  }
}
