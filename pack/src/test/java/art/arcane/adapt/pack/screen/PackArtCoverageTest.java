package art.arcane.adapt.pack.screen;

import art.arcane.adapt.engine.asset.AssetCatalog;
import art.arcane.adapt.pack.PackResources;
import art.arcane.adapt.pack.TestCatalogs;
import art.arcane.adapt.pack.bake.BakeRequest;
import art.arcane.adapt.pack.bake.KitBake;
import art.arcane.adapt.pack.bake.PackBake;
import art.arcane.adapt.pack.bake.PackManifest;
import art.arcane.adapt.pack.layout.PackArt;
import art.arcane.adapt.pack.layout.PackLayoutReader;
import art.arcane.adapt.pack.layout.ScreenLayout;
import art.arcane.adapt.pack.manifest.PackManifestReader;
import art.arcane.adapt.pack.menu.LedgerSkin;
import art.arcane.adapt.pack.raster.Raster;
import art.arcane.adapt.pack.surface.PackSlot;
import art.arcane.adapt.pack.surface.PackSupport;
import art.arcane.adapt.pack.surface.PackSurface;
import art.arcane.adapt.pack.surface.SlotLook;
import art.arcane.adapt.pack.text.PackFontMetrics;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuIcon;
import art.arcane.adapt.platform.menu.MenuSession;
import art.arcane.adapt.platform.value.Id;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds the built-in pack and draws every pack screen from sample views over it, full and top-rows-only: every item
 * model, tooltip style, font and glyph a page names must exist in the built pack, and every key of {@link PackArt} must
 * be baked. With {@code adapt.pack.evidence} set, writes each screen there as PNGs of the 426x240 and 480x270 GUI
 * screens (text in the vanilla sheet when {@code adapt.pack.font} names it).
 */
class PackArtCoverageTest {
  private static Built built;
  private static final Map<String, KitBake.Result> kits = new ConcurrentHashMap<>();
  static final int GUARANTEED_WIDTH = 320;
  static final int GUARANTEED_HEIGHT = 240;
  /** The GUI screens of common windows at auto scale: 2560x1440 and 1280x720, and 1920x1080. */
  private static final int[][] EVIDENCE_SCREENS = {{426, 240}, {480, 270}};
  private static final long[] FIT_TICKS = {0, 37, 113, 260, 517, 799};

  private record Built(Map<String, byte[]> files, PackManifest manifest, PackResources resources) {
  }

  /** One screen as a window shows it, and the slot whose tooltip the evidence draws. */
  private record Screen(String name, ChestMenu menu, PackSurface surface, PackSlot hover, long tick) {
  }

  @BeforeAll
  static void bake() {
    AssetCatalog catalog = TestCatalogs.builtin();
    BakeRequest.KitCache cache = new BakeRequest.KitCache() {
      @Override
      public KitBake.Result get(String key) {
        return kits.get(key);
      }

      @Override
      public void put(String key, KitBake.Result result) {
        kits.put(key, result);
      }
    };
    PackBake.Result result = PackBake.build(BakeRequest.of(catalog).withThreads(4).withCache(cache));
    Map<String, byte[]> files = unzip(result.zip());
    String json = new String(files.get(PackManifest.PATH), StandardCharsets.UTF_8);
    String condensed = System.getProperty("adapt.pack.condensed");
    if (condensed != null) {
      json = withCondensed(json, files, Path.of(condensed));
    }
    PackResources resources = new PackResources(PackManifestReader.read(new StringReader(json)), PackLayoutReader.builtIn(),
        PackFontMetrics.vanilla());
    built = new Built(files, result.manifest(), resources);
  }

  /**
   * For composed evidence before the bake ships the condensed font: adds it from the design folder {@code source}
   * ({@code widths.json}, {@code condensed.json} and its sheets) as one font per text row, the row's ascents lowered as
   * the text rows' are, and names it in the manifest.
   */
  private static String withCondensed(String json, Map<String, byte[]> files, Path source) {
    try {
      JsonObject manifest = JsonParser.parseString(json).getAsJsonObject();
      JsonObject widths = JsonParser.parseString(Files.readString(source.resolve("widths.json"))).getAsJsonObject();
      JsonObject provider = JsonParser.parseString(Files.readString(source.resolve("condensed.json"))).getAsJsonObject();
      for (String sheet : List.of("condensed_ascii.png", "condensed_accent.png")) {
        files.put("assets/adapt/textures/font/" + sheet, Files.readAllBytes(source.resolve(sheet)));
      }
      JsonArray fonts = new JsonArray();
      for (JsonElement element : manifest.getAsJsonArray("textFonts")) {
        JsonObject text = element.getAsJsonObject();
        int dy = text.get("dy").getAsInt();
        String id = text.get("font").getAsString().replace("/text/", "/condensed/");
        JsonObject font = provider.deepCopy();
        for (JsonElement entry : font.getAsJsonArray("providers")) {
          JsonObject bitmap = entry.getAsJsonObject();
          if (bitmap.has("ascent")) {
            bitmap.addProperty("ascent", bitmap.get("ascent").getAsInt() - dy);
          }
        }
        int colon = id.indexOf(':');
        files.put("assets/" + id.substring(0, colon) + "/font/" + id.substring(colon + 1) + ".json", font.toString().getBytes(StandardCharsets.UTF_8));
        JsonObject row = new JsonObject();
        row.addProperty("dy", dy);
        row.addProperty("font", id);
        fonts.add(row);
      }
      JsonObject block = new JsonObject();
      block.add("advances", widths.getAsJsonObject("advances"));
      block.add("fonts", fonts);
      manifest.add("condensed", block);
      return manifest.toString();
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  @Test
  void everyScreenNamesOnlyWhatThePackHas() throws IOException {
    String evidence = System.getProperty("adapt.pack.evidence");
    String font = System.getProperty("adapt.pack.font");
    Raster sheet = font == null ? null : Raster.decode(Files.readAllBytes(Path.of(font)));
    List<String> problems = new ArrayList<>();
    for (Screen screen : screens(built.resources())) {
      ScreenCompositor compositor = new ScreenCompositor(built.files(), sheet);
      Raster layer = compositor.layer(screen.menu(), screen.surface(), screen.tick(), screen.hover());
      compositor.problems().forEach((String problem) -> problems.add(screen.name() + ": " + problem));
      if (evidence != null) {
        Files.createDirectories(Path.of(evidence));
        for (int[] size : EVIDENCE_SCREENS) {
          Path out = Path.of(evidence).resolve(screen.name() + "-" + size[0] + "x" + size[1] + ".png");
          Files.write(out, scaled(ScreenCompositor.screen(layer, screen.menu(), size[0], size[1]), 2).png());
        }
        Files.writeString(Path.of(evidence).resolve(screen.name() + ".items"), named(screen));
      }
    }
    if (evidence != null) {
      Files.write(Path.of(evidence).resolve(PackManifest.PATH), built.files().get(PackManifest.PATH));
    }
    assertEquals(List.of(), problems);
  }

  /**
   * At auto GUI scale a client guarantees only a 320x240 screen with the window centred: every screen, at every sampled
   * tick, draws nothing outside it (container y -9 to 230 and x -72 to 247 for the six-row window) and leaves no pixel of
   * the vanilla window showing through.
   */
  @Test
  void everyScreenFitsTheGuaranteedScreenAndCoversTheWindow() {
    List<String> problems = new ArrayList<>();
    for (Screen screen : screens(built.resources())) {
      int windowHeight = ScreenCompositor.height(screen.menu());
      int above = (GUARANTEED_HEIGHT - windowHeight) / 2;
      int side = (GUARANTEED_WIDTH - 176) / 2;
      for (long tick : FIT_TICKS) {
        Raster layer = new ScreenCompositor(built.files(), null).layer(screen.menu(), screen.surface(), tick, null);
        int top = Integer.MAX_VALUE;
        int bottom = Integer.MIN_VALUE;
        int left = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int uncovered = 0;
        for (int y = 0; y < layer.height(); y++) {
          for (int x = 0; x < layer.width(); x++) {
            int cx = x + ScreenCompositor.LEFT;
            int cy = y + ScreenCompositor.TOP;
            int alpha = layer.get(x, y) >>> 24;
            if (alpha != 0) {
              top = Math.min(top, cy);
              bottom = Math.max(bottom, cy);
              left = Math.min(left, cx);
              right = Math.max(right, cx);
            }
            if (alpha != 0xFF && cx >= 0 && cy >= 0 && cx < 176 && cy < windowHeight) {
              uncovered++;
            }
          }
        }
        if (top < -above || bottom >= windowHeight + above || left < -side || right >= 176 + side) {
          problems.add(screen.name() + " tick " + tick + ": drawn over x " + left + ".." + right + ", y " + top + ".." + bottom);
        }
        if (uncovered > 0) {
          problems.add(screen.name() + " tick " + tick + ": " + uncovered + " window pixels show through");
        }
      }
    }
    assertEquals(List.of(), problems);
  }

  /**
   * Every baked kit page (lead, partner, top-rows-only and their still copies, so the mutated pair too) stays inside the
   * guaranteed screen and covers the window through every frame of every moving region, not only at the sampled ticks.
   */
  @Test
  void everyKitPageFitsTheGuaranteedScreenInEveryFrame() {
    List<String> problems = new ArrayList<>();
    StringBuilder extents = new StringBuilder();
    List<KitBake.Result> results = new ArrayList<>(kits.values());
    results.sort((KitBake.Result a, KitBake.Result b) -> a.kit().skill().compareTo(b.kit().skill()));
    for (KitBake.Result result : results) {
      for (KitBake.Page page : result.pages()) {
        int[] extent = PageExtent.of(page);
        int uncovered = PageExtent.uncovered(page);
        String name = result.kit().skill().path() + " " + page.layout();
        extents.append(name).append(" book ").append(page.originY()).append(": x ").append(extent[0]).append("..").append(extent[2])
            .append(", y ").append(extent[1]).append("..").append(extent[3]).append('\n');
        if (extent[0] < -72 || extent[1] < -9 || extent[2] > 247 || extent[3] > 230) {
          problems.add(name + " reaches x " + extent[0] + ".." + extent[2] + ", y " + extent[1] + ".." + extent[3]);
        }
        if (uncovered > 0) {
          problems.add(name + ": " + uncovered + " window pixels show through in some frame");
        }
      }
    }
    System.out.print(extents);
    assertEquals(24 * 6, results.stream().mapToInt((KitBake.Result result) -> result.pages().size()).sum());
    assertEquals(List.of(), problems);
  }

  /**
   * No slot item of any kit page draws in the header row (container y 0 to 17, from the crumb ribbon's left edge to the
   * chips' right edge at x 195) in any frame, so ribbon text and chips are never crossed.
   */
  @Test
  void everyKitPageLeavesTheHeaderRowToTheTitle() {
    int left = Integer.MAX_VALUE;
    for (ScreenLayout screen : PackLayoutReader.builtIn().screens().values()) {
      if (screen.text("crumbs") != null) {
        left = Math.min(left, screen.text("crumbs").x() - PackArt.RIBBON_LEAD);
      }
    }
    List<String> problems = new ArrayList<>();
    for (KitBake.Result result : kits.values()) {
      for (KitBake.Page page : result.pages()) {
        int moving = PageExtent.movingInHeader(page, left);
        if (moving > 0) {
          problems.add(result.kit().skill().path() + " " + page.layout() + ": " + moving + " item pixels in the header row");
        }
      }
    }
    assertEquals(List.of(), problems);
  }

  @Test
  void everyArtKeyIsBaked() {
    PackManifest manifest = built.manifest();
    Set<String> art = new TreeSet<>();
    manifest.art().forEach((PackManifest.Art piece) -> art.add(piece.name()));
    Set<String> paint = new TreeSet<>();
    manifest.paint().forEach((PackManifest.Paint piece) -> paint.add(piece.key()));
    Set<String> pages = new TreeSet<>();
    manifest.pages().forEach((PackManifest.PageFont page) -> pages.add(page.kit() + "/" + page.layout()));
    Set<String> icons = new TreeSet<>();
    manifest.icons().forEach((PackManifest.Icon icon) -> icons.add(icon.sprite()));
    List<String> missing = new ArrayList<>();
    for (PackArt.Item item : PackArt.items()) {
      if (!art.contains(item.key())) {
        missing.add("item " + item.key());
      }
    }
    for (PackArt.Paint piece : PackArt.paint(PackLayoutReader.builtIn())) {
      if (!paint.contains(piece.key())) {
        missing.add("paint " + piece.key());
      }
    }
    List<String> tome = new ArrayList<>(List.of(PackArt.SINGLE, PackArt.FALLBACK));
    for (int rows = 1; rows <= PackArt.LEDGER_ROWS; rows++) {
      tome.add(PackArt.ledger(rows));
    }
    for (String layout : tome) {
      if (!pages.contains(PackArt.TOME + "/" + layout)) {
        missing.add("page " + PackArt.TOME + "/" + layout);
      }
    }
    for (PackManifest.KitStyle kit : manifest.kits()) {
      for (String layout : List.of(PackArt.SINGLE, PackArt.still(PackArt.SINGLE), PackArt.PARTNER, PackArt.FALLBACK,
          PackArt.still(PackArt.FALLBACK))) {
        if (!pages.contains(kit.kit() + "/" + layout)) {
          missing.add("page " + kit.kit() + "/" + layout);
        }
      }
      if (kit.tooltipStyle() == null) {
        missing.add("tooltip style of " + kit.kit());
      }
      if (!paint.contains(PackArt.ribbonHead(kit.kit())) || !paint.contains(PackArt.ribbonTail(kit.kit()))) {
        missing.add("ribbon of " + kit.kit());
      }
      if (kit.toastModel() == null) {
        missing.add("toast icon of " + kit.kit());
      }
      String icon = kit.kit().replace(":", ":gui/skill/");
      if (!icons.contains(icon) || !icons.contains(icon + PackArt.DIM)) {
        missing.add("icon " + icon);
      }
    }
    if (!paint.contains(PackArt.ribbonHead(PackArt.TOME))) {
      missing.add("ribbon of " + PackArt.TOME);
    }
    if (!manifest.tooltipStyles().contains(PackArt.TOME)) {
      missing.add("tooltip style " + PackArt.TOME);
    }
    for (String sprite : List.of(PackArt.KNOWLEDGE_SPRITE, PackArt.POWER_SPRITE, PackArt.SWAP_SPRITE)) {
      if (!icons.contains(sprite)) {
        missing.add("icon " + sprite);
      }
    }
    assertEquals(List.of(), missing);
    assertNotEquals(0, manifest.kits().size());
  }

  @Test
  void aKitPageMovesBetweenTicks() {
    Screen page = screens(built.resources()).stream().filter((Screen screen) -> screen.name().equals("skill-herbalism")).findFirst()
        .orElseThrow();
    Raster first = new ScreenCompositor(built.files(), null).draw(page.menu(), page.surface(), 0L, null);
    int moved = 0;
    for (long tick : new long[]{10L, 40L}) {
      Raster later = new ScreenCompositor(built.files(), null).draw(page.menu(), page.surface(), tick, null);
      for (int at = 0; at < first.pixels().length; at++) {
        moved += first.pixels()[at] != later.pixels()[at] ? 1 : 0;
      }
    }
    assertTrue(moved > 0, "the host items' strips draw the same at ticks 0, 10 and 40");
  }

  @Test
  void everySampleScreenIsDrawn() {
    List<String> names = new ArrayList<>();
    screens(built.resources()).forEach((Screen screen) -> names.add(screen.name()));
    assertTrue(names.containsAll(List.of("skills", "skill-hunter", "skill-mutated", "adaptation-levels", "adaptation-recipes",
        "adaptation-settings", "mutate", "mutate-armed", "ledger-6", "skills-top", "skill-hunter-top", "mutate-armed-top")), names.toString());
  }

  /** Every sample screen, laid out over the player slots and again on the top rows only. */
  private static List<Screen> screens(PackResources resources) {
    List<Screen> screens = new ArrayList<>();
    for (PackSupport support : List.of(PackSupport.FULL, PackSupport.NONE)) {
      String suffix = support.lowerSlots() ? "" : "-top";
      shot(screens, resources, support, "skills" + suffix, null, (ScreenSamples samples) -> samples.screens.openSkills(samples.viewer));
      shot(screens, resources, support, "skill-hunter" + suffix, null,
          (ScreenSamples samples) -> samples.screens.openSkill(samples.viewer, ScreenSamples.HUNTER));
      shot(screens, resources, support, "skill-hunter-tooltip" + suffix, PackSlot.top(9),
          (ScreenSamples samples) -> samples.screens.openSkill(samples.viewer, ScreenSamples.HUNTER));
      shot(screens, resources, support, "skill-herbalism" + suffix, null,
          (ScreenSamples samples) -> samples.screens.openSkill(samples.viewer, ScreenSamples.HERBALISM));
      for (String kit : List.of(ScreenSamples.CHRONOS, ScreenSamples.TRAGOUL, ScreenSamples.RANGED)) {
        shot(screens, resources, support, "skill-" + Id.parse(kit).path() + suffix, null,
            (ScreenSamples samples) -> samples.screens.openSkill(samples.viewer, kit));
      }
      shot(screens, resources, support, ScreenSamples.everyKit(), "skill-stealth" + suffix, null,
          (ScreenSamples samples) -> samples.screens.openSkill(samples.viewer, ScreenSamples.skill("stealth")));
      shot(screens, resources, support, "skill-mutated" + suffix, null,
          (ScreenSamples samples) -> samples.screens.openSkill(samples.viewer, ScreenSamples.MUTATED));
      shot(screens, resources, support, "skill-mutated-leaf2" + suffix, null, (ScreenSamples samples) -> {
        samples.screens.openSkill(samples.viewer, ScreenSamples.MUTATED);
        samples.screens.page(samples.viewer, 1);
      });
      shot(screens, resources, support, "adaptation-levels" + suffix, null,
          (ScreenSamples samples) -> samples.screens.openAdaptation(samples.viewer, ScreenSamples.ADRENALINE));
      shot(screens, resources, support, "adaptation-levels-armed" + suffix, null, (ScreenSamples samples) -> {
        samples.screens.openAdaptation(samples.viewer, ScreenSamples.ADRENALINE);
        samples.click(resources.layout().screen(PackAdaptationScreen.SCREEN + "." + PackAdaptationScreen.LEVELS, support.lowerSlots()).slot(PackAdaptationScreen.UNLEARN));
      });
      shot(screens, resources, support, "adaptation-recipes" + suffix, null, (ScreenSamples samples) -> {
        samples.screens.openAdaptation(samples.viewer, ScreenSamples.COBWEB);
        samples.screens.tab(samples.viewer, 1);
      });
      shot(screens, resources, support, "adaptation-settings" + suffix, null, (ScreenSamples samples) -> {
        samples.screens.openAdaptation(samples.viewer, ScreenSamples.ADRENALINE);
        samples.screens.tab(samples.viewer, 1);
      });
      shot(screens, resources, support, "mutate" + suffix, null, (ScreenSamples samples) -> {
        samples.screens.openMutate(samples.viewer, ScreenSamples.PICKAXE);
        samples.screens.partner(samples.viewer, ScreenSamples.PICKAXE, ScreenSamples.BREWING);
      });
      shot(screens, resources, support, "mutate-armed" + suffix, null, (ScreenSamples samples) -> {
        samples.screens.openMutate(samples.viewer, ScreenSamples.PICKAXE);
        samples.screens.partner(samples.viewer, ScreenSamples.PICKAXE, ScreenSamples.BREWING);
        samples.click(resources.layout().screen(PackMutateScreen.SCREEN, support.lowerSlots()).slot("seal.mark"));
      });
      shot(screens, resources, support, "mutate-sealed" + suffix, null, (ScreenSamples samples) -> {
        samples.screens.openMutate(samples.viewer, ScreenSamples.PICKAXE);
        samples.screens.partner(samples.viewer, ScreenSamples.PICKAXE, ScreenSamples.BREWING);
        samples.click(resources.layout().screen(PackMutateScreen.SCREEN, support.lowerSlots()).slot("seal.mark"));
        samples.now += PackScreens.SEAL_STEP_MS * 2;
        samples.screens.page(samples.viewer, 0);
      });
      shot(screens, resources, support, "mutate-partners" + suffix, null,
          (ScreenSamples samples) -> samples.screens.openMutate(samples.viewer, ScreenSamples.PICKAXE));
      shot(screens, resources, support, "mutate-partners-page2" + suffix, null, (ScreenSamples samples) -> {
        samples.screens.openMutate(samples.viewer, ScreenSamples.PICKAXE);
        samples.screens.page(samples.viewer, 1);
      });
      shot(screens, resources, support, "mutate-leads" + suffix, null,
          (ScreenSamples samples) -> samples.screens.openMutateLeads(samples.viewer));
      shot(screens, resources, support, "skill-mutated-tooltip" + suffix, PackSlot.top(9),
          (ScreenSamples samples) -> samples.screens.openSkill(samples.viewer, ScreenSamples.MUTATED));
    }
    for (int rows = 1; rows <= PackArt.LEDGER_ROWS; rows++) {
      LedgerSkin.Skinned skinned = LedgerSkin.skin(ledger(rows), resources, null, (MenuSession session) -> { });
      screens.add(new Screen("ledger-" + rows, skinned.menu(), skinned.surface(), null, 0L));
    }
    return screens;
  }

  private static void shot(List<Screen> screens, PackResources resources, PackSupport support, String name, PackSlot hover,
                           Consumer<ScreenSamples> open) {
    shot(screens, resources, support, new ScreenSamples(), name, hover, open);
  }

  private static void shot(List<Screen> screens, PackResources resources, PackSupport support, ScreenSamples samples, String name,
                           PackSlot hover, Consumer<ScreenSamples> open) {
    samples.windows.support = support;
    samples.screens(resources);
    open.accept(samples);
    if (samples.windows.menu == null) {
      throw new IllegalStateException(name + " opened nothing");
    }
    screens.add(new Screen(name, samples.windows.menu, samples.windows.surface, hover, 0L));
  }

  /** The window slots (chest 0 to 53, then player 54 on) whose item has a name, so it shows a tooltip; one per line. */
  private static String named(Screen screen) {
    StringBuilder out = new StringBuilder();
    screen.menu().buttons().forEach((Integer slot, MenuButton button) -> {
      if (button.icon().name() != null && !button.icon().name().isBlank()) {
        out.append(slot).append('\n');
      }
    });
    screen.surface().lower().forEach((Integer slot, MenuButton button) -> {
      if (button.icon().name() != null && !button.icon().name().isBlank()) {
        out.append(54 + slot).append('\n');
      }
    });
    return out.toString();
  }

  /** A config-like menu of {@code rows}: entries, filler, back, paging and page information. */
  private static ChestMenu ledger(int rows) {
    ChestMenu.Builder builder = ChestMenu.builder("Config", rows);
    int last = (rows - 1) * 9;
    for (int slot = 0; slot < rows * 9; slot++) {
      MenuIcon icon = MenuIcon.of(Id.minecraft(slot < last ? "writable_book" : "gray_stained_glass_pane"), slot < last ? "Entry " + slot : " ",
          List.of());
      builder.set(slot, slot < last ? new MenuButton(icon, (MenuClick click) -> { }) : MenuButton.display(icon));
    }
    Map<Integer, String> chrome = new LinkedHashMap<>();
    chrome.put(last, "back");
    chrome.put(last + 3, "previous");
    chrome.put(last + 4, "page-info");
    chrome.put(last + 5, "next");
    for (Map.Entry<Integer, String> entry : chrome.entrySet()) {
      MenuIcon icon = MenuIcon.of(Id.minecraft("arrow"), entry.getValue(), List.of());
      MenuButton button = entry.getValue().equals("previous") ? MenuButton.display(icon) : new MenuButton(icon, (MenuClick click) -> { });
      builder.set(entry.getKey(), button.withHint(MenuHint.navigation(entry.getValue())));
    }
    return builder.build();
  }

  private static Raster scaled(Raster raster, int scale) {
    Raster out = new Raster(raster.width() * scale, raster.height() * scale);
    for (int y = 0; y < out.height(); y++) {
      for (int x = 0; x < out.width(); x++) {
        out.set(x, y, raster.get(x / scale, y / scale));
      }
    }
    return out;
  }

  private static Map<String, byte[]> unzip(byte[] zip) {
    Map<String, byte[]> files = new TreeMap<>();
    try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
      for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
        files.put(entry.getName(), in.readAllBytes());
      }
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
    return files;
  }
}
