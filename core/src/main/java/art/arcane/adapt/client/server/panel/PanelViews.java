package art.arcane.adapt.client.server.panel;

import art.arcane.adapt.client.protocol.FrameLimits;
import art.arcane.adapt.client.protocol.HintKind;
import art.arcane.adapt.client.protocol.PanelLayout;
import art.arcane.adapt.client.protocol.PanelPaging;
import art.arcane.adapt.client.protocol.PanelTile;
import art.arcane.adapt.client.protocol.PanelView;
import art.arcane.adapt.client.protocol.TileAction;
import art.arcane.adapt.client.protocol.TileHint;
import art.arcane.adapt.client.protocol.TileRole;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuIcon;
import art.arcane.adapt.platform.menu.MenuMeta;
import art.arcane.adapt.platform.menu.MenuPaging;
import art.arcane.adapt.platform.value.Id;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Turns a {@link ChestMenu} into the {@link PanelView} the native panel draws. Text that would break
 * {@link FrameLimits} is cut short instead of failing: strings at their byte limit, lore at its line limit, and lore
 * across all tiles so the whole frame stays under the server-to-client size limit. Roles and the layout the menu
 * did not set are inferred: blank, inert, unhinted tiles are {@link TileRole#DECORATION}; hinted values are
 * settings; a menu whose remaining tiles all have names is a list (a form when it holds a setting), else a grid.
 */
public final class PanelViews {
  static final int FRAME_HEADROOM_BYTES = 4096;
  static final String FALLBACK_ITEM = "minecraft:barrier";
  private static final int STRING_OVERHEAD = 3;
  private static final int TILE_OVERHEAD = 48;
  private static final int OPTION_BYTES = 128;
  private static final int OPTIONS_BUDGET_BYTES = 2048;
  /** Navigation targets the native panel draws in its title bar and footer instead of the body. */
  public static final Set<String> CHROME_TARGETS = Set.of("back", "first", "previous", "next", "last", "page-info");

  private PanelViews() {
  }

  public static PanelView view(int sessionId, int revision, ChestMenu menu) {
    String title = clip(menu.title(), FrameLimits.MAX_TEXT_BYTES);
    List<Draft> drafts = new ArrayList<>(menu.buttons().size());
    long fixed = bytes(title);
    for (Map.Entry<Integer, MenuButton> entry : menu.buttons().entrySet()) {
      if (drafts.size() == FrameLimits.MAX_TILES) {
        break;
      }
      Draft draft = draft(entry.getKey(), entry.getValue());
      fixed += draft.fixedBytes();
      drafts.add(draft);
    }
    long budget = FrameLimits.MAX_S2C_BYTES - FRAME_HEADROOM_BYTES - fixed;
    int perTile = (int) Math.max(0L, budget / Math.max(1, drafts.size()));
    List<PanelTile> tiles = new ArrayList<>(drafts.size());
    for (Draft draft : drafts) {
      int share = draft.description() == null ? perTile : perTile / 2;
      List<String> lore = fit(draft.lore(), share);
      List<String> description = draft.description() == null ? null : fit(draft.description(), share);
      tiles.add(new PanelTile(draft.slot(), draft.icon(), draft.amount(), draft.name(), lore, draft.glint(), draft.hint(),
          draft.role(), draft.section(), draft.value(), description, draft.actions()));
    }
    List<String> breadcrumb = new ArrayList<>();
    for (String crumb : menu.breadcrumb()) {
      if (breadcrumb.size() == FrameLimits.MAX_BREADCRUMB) {
        break;
      }
      breadcrumb.add(clip(crumb, FrameLimits.MAX_ID_BYTES));
    }
    MenuPaging paging = menu.paging();
    PanelPaging pages = paging == null ? null : new PanelPaging(paging.page(), paging.pages(), paging.total());
    return new PanelView(sessionId, revision, title, menu.rows(), layout(menu, tiles), pages, breadcrumb, tiles);
  }

  /** The menu's own layout, or one inferred from its tiles. */
  static PanelLayout layout(ChestMenu menu, List<PanelTile> tiles) {
    if (menu.layout() != null) {
      return switch (menu.layout()) {
        case GRID -> PanelLayout.GRID;
        case LIST -> PanelLayout.LIST;
        case FORM -> PanelLayout.FORM;
      };
    }
    boolean setting = false;
    for (PanelTile tile : tiles) {
      if (tile.role() == TileRole.DECORATION || chromeTarget(tile) != null) {
        continue;
      }
      if (plain(tile.name()).isBlank()) {
        return PanelLayout.GRID;
      }
      setting |= tile.role() == TileRole.SETTING;
    }
    return setting ? PanelLayout.FORM : PanelLayout.LIST;
  }

  /** The title-bar or footer target of a navigation tile, or null for tiles drawn in the body. */
  public static String chromeTarget(PanelTile tile) {
    TileHint hint = tile.hint();
    if (hint == null || hint.kind() != HintKind.NAVIGATION) {
      return null;
    }
    String target = hint.value().toLowerCase(Locale.ROOT);
    return CHROME_TARGETS.contains(target) ? target : null;
  }

  static TileRole role(MenuButton button, String name, List<String> lore) {
    MenuMeta meta = button.meta();
    if (meta.role() != null) {
      return switch (meta.role()) {
        case ENTRY -> TileRole.ENTRY;
        case SETTING -> TileRole.SETTING;
        case ACTION -> TileRole.ACTION;
        case NAVIGATION -> TileRole.NAVIGATION;
        case HEADER -> TileRole.HEADER;
        case DECORATION -> TileRole.DECORATION;
        case INFO -> TileRole.INFO;
      };
    }
    MenuHint hint = button.hint();
    if (hint != null) {
      return switch (hint.kind()) {
        case TOGGLE, NUMBER, TEXT, CHOICE -> TileRole.SETTING;
        case NAVIGATION -> TileRole.NAVIGATION;
        case DESTRUCTIVE -> TileRole.ACTION;
      };
    }
    if (!button.clickable()) {
      boolean blank = plain(name).isBlank() && lore.stream().allMatch((String line) -> plain(line).isBlank());
      return blank ? TileRole.DECORATION : TileRole.INFO;
    }
    return TileRole.ENTRY;
  }

  private static String plain(String text) {
    StringBuilder out = new StringBuilder(text.length());
    for (int index = 0; index < text.length(); index++) {
      char c = text.charAt(index);
      if (c == '§' && index + 1 < text.length()) {
        index++;
        continue;
      }
      out.append(c);
    }
    return out.toString();
  }

  static IconRef icon(MenuIcon icon) {
    ItemRef item = icon.item();
    Id model = icon.itemModel();
    if (model == null && item != null) {
      model = item.itemModel();
    }
    String itemId = icon.type().toString();
    if (bytes(itemId) > FrameLimits.MAX_ID_BYTES) {
      itemId = FALLBACK_ITEM;
    }
    String modelId = model == null ? null : model.toString();
    if (modelId != null && bytes(modelId) > FrameLimits.MAX_ID_BYTES) {
      modelId = null;
    }
    return new IconRef(itemId, icon.customModelData(), modelId, null);
  }

  static TileHint hint(MenuHint hint) {
    if (hint == null) {
      return null;
    }
    HintKind kind = switch (hint.kind()) {
      case TOGGLE -> HintKind.TOGGLE;
      case NUMBER -> HintKind.NUMBER;
      case TEXT -> HintKind.TEXT;
      case NAVIGATION -> HintKind.NAVIGATION;
      case DESTRUCTIVE -> HintKind.DESTRUCTIVE;
      case CHOICE -> HintKind.CHOICE;
    };
    List<String> options = new ArrayList<>();
    int optionBytes = 0;
    for (String option : hint.options()) {
      String clipped = clip(option, OPTION_BYTES);
      optionBytes += bytes(clipped);
      if (options.size() == FrameLimits.MAX_CHOICES || optionBytes > OPTIONS_BUDGET_BYTES) {
        break;
      }
      options.add(clipped);
    }
    int valueLimit = kind == HintKind.TEXT ? FrameLimits.MAX_TEXT_BYTES : FrameLimits.MAX_ID_BYTES;
    return new TileHint(kind, clip(hint.value(), valueLimit), hint.input() != null, options,
        finite(hint.min()), finite(hint.max()), finite(hint.step()));
  }

  private static Double finite(Double value) {
    return value == null || !Double.isFinite(value) ? null : value;
  }

  /** Cuts {@code text} to at most {@code maxBytes} of UTF-8 without splitting a character or a colour code. */
  static String clip(String text, int maxBytes) {
    if (text == null) {
      return "";
    }
    if ((long) text.length() * 3L <= maxBytes) {
      return text;
    }
    int used = 0;
    int end = 0;
    while (end < text.length()) {
      int codePoint = text.codePointAt(end);
      int size = utf8Size(codePoint);
      if (used + size > maxBytes) {
        break;
      }
      used += size;
      end += Character.charCount(codePoint);
    }
    if (end == text.length()) {
      return text;
    }
    if (end > 0 && text.charAt(end - 1) == '§') {
      end--;
    }
    return text.substring(0, end);
  }

  private static Draft draft(int slot, MenuButton button) {
    MenuIcon icon = button.icon();
    ItemRef item = icon.item();
    String name = button.meta().label() != null ? button.meta().label()
        : icon.name() != null ? icon.name() : item == null ? "" : Objects.requireNonNullElse(item.displayName(), "");
    List<String> lore = !icon.lore().isEmpty() || item == null ? icon.lore() : item.lore();
    boolean glint = icon.glint() || (item != null && item.hasGlint());
    IconRef ref = icon(icon);
    TileHint hint = hint(button.hint());
    name = clip(name, FrameLimits.MAX_TEXT_BYTES);
    List<String> lines = lines(lore);
    MenuMeta meta = button.meta();
    TileRole role = role(button, name, lines);
    String section = clip(meta.section(), FrameLimits.MAX_ID_BYTES);
    String value = clip(meta.value(), FrameLimits.MAX_ID_BYTES);
    List<String> description = meta.description() == null ? null : lines(meta.description());
    List<TileAction> actions = new ArrayList<>();
    for (Map.Entry<ClickKind, String> entry : meta.actions().entrySet()) {
      if (actions.size() == FrameLimits.MAX_TILE_ACTIONS) {
        break;
      }
      actions.add(new TileAction(entry.getKey(), clip(entry.getValue(), FrameLimits.MAX_ID_BYTES)));
    }
    long fixed = TILE_OVERHEAD + bytes(name) + bytes(ref.itemId()) + (ref.itemModelId() == null ? 0 : bytes(ref.itemModelId()))
        + bytes(section) + bytes(value);
    if (hint != null) {
      fixed += bytes(hint.value()) + STRING_OVERHEAD + 30;
      for (String option : hint.options()) {
        fixed += bytes(option) + STRING_OVERHEAD;
      }
    }
    for (TileAction action : actions) {
      fixed += bytes(action.label()) + STRING_OVERHEAD + 1;
    }
    return new Draft(slot, ref, icon.amount(), name, lines, glint, hint, role, section, value, description, actions, fixed);
  }

  private static List<String> lines(List<String> source) {
    int limit = Math.min(source.size(), FrameLimits.MAX_LINES);
    List<String> lines = new ArrayList<>(limit);
    for (int i = 0; i < limit; i++) {
      lines.add(clip(source.get(i), FrameLimits.MAX_TEXT_BYTES));
    }
    return lines;
  }

  private static List<String> fit(List<String> lore, int budget) {
    int used = 0;
    for (int i = 0; i < lore.size(); i++) {
      int size = bytes(lore.get(i)) + STRING_OVERHEAD;
      if (used + size <= budget) {
        used += size;
        continue;
      }
      List<String> kept = new ArrayList<>(lore.subList(0, i));
      int room = budget - used - STRING_OVERHEAD;
      if (room > 0) {
        kept.add(clip(lore.get(i), room));
      }
      return kept;
    }
    return lore;
  }

  private static int bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8).length;
  }

  private static int utf8Size(int codePoint) {
    if (codePoint < 0x80) {
      return 1;
    }
    if (codePoint < 0x800) {
      return 2;
    }
    return codePoint < 0x10000 ? 3 : 4;
  }

  private record Draft(int slot, IconRef icon, int amount, String name, List<String> lore, boolean glint,
                       TileHint hint, TileRole role, String section, String value, List<String> description,
                       List<TileAction> actions, long fixedBytes) {
  }
}
