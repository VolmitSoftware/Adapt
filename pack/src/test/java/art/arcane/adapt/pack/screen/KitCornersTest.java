package art.arcane.adapt.pack.screen;

import art.arcane.adapt.pack.PackResources;
import art.arcane.adapt.pack.TestCatalogs;
import art.arcane.adapt.pack.bake.BakeRequest;
import art.arcane.adapt.pack.bake.PackBake;
import art.arcane.adapt.pack.bake.PackManifest;
import art.arcane.adapt.pack.layout.PackLayoutReader;
import art.arcane.adapt.pack.manifest.PackManifestReader;
import art.arcane.adapt.pack.raster.Raster;
import art.arcane.adapt.pack.text.PackFontMetrics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Opens every built-in kit's pages from {@link ScreenSamples#everyKit()} (an adaptation page and the plain skill page,
 * the mutated page on the lead's and the partner's leaf, and the Mutate partner picker the kit leads) and draws them
 * from the built pack. With {@code adapt.pack.evidence} set, writes contact sheets of the page corners at four times
 * scale there: the top left and top right of the ribbon pages ({@code corners-left.png}, {@code corners-right.png}) and
 * all four corners of the plain skill page and the partner picker ({@code corners-four.png}, {@code corners-partners.png}).
 */
class KitCornersTest {
  private static final int SCALE = 4;
  private static final int CROP_WIDTH = 64;
  private static final int CROP_HEIGHT = 44;
  /** Container pixels of a crop outside the page's drawn extent. */
  private static final int OUTSIDE = 4;
  private static final int LABEL = 120;
  private static final int GAP = 6;
  private static Map<String, byte[]> files;
  private static PackResources resources;

  /** One kit page as a window shows it. */
  private record Page(String kit, String name, Raster screen, int[] bounds) {
  }

  @BeforeAll
  static void bake() {
    PackBake.Result result = PackBake.build(BakeRequest.of(TestCatalogs.builtin()).withThreads(4));
    files = unzip(result.zip());
    String json = new String(files.get(PackManifest.PATH), StandardCharsets.UTF_8);
    resources = new PackResources(PackManifestReader.read(new StringReader(json)), PackLayoutReader.builtIn(), PackFontMetrics.vanilla());
  }

  @Test
  void everyKitPageIsDrawnFromThePack() throws IOException {
    List<String> problems = new ArrayList<>();
    List<Page> pages = new ArrayList<>();
    for (int index = 0; index < ScreenSamples.KITS.size(); index++) {
      String kit = ScreenSamples.KITS.get(index);
      String before = ScreenSamples.KITS.get((index + ScreenSamples.KITS.size() - 1) % ScreenSamples.KITS.size());
      pages.add(page(kit, "adaptation", problems, (ScreenSamples samples) -> samples.screens.openAdaptation(samples.viewer,
          ScreenSamples.kitAdaptation(kit))));
      pages.add(page(kit, "lead", problems, (ScreenSamples samples) -> samples.screens.openSkill(samples.viewer, ScreenSamples.kitMutation(kit))));
      pages.add(page(kit, "partner", problems, (ScreenSamples samples) -> {
        samples.screens.openSkill(samples.viewer, ScreenSamples.kitMutation(before));
        samples.screens.page(samples.viewer, 1);
      }));
      pages.add(page(kit, "skill", problems, (ScreenSamples samples) -> samples.screens.openSkill(samples.viewer, ScreenSamples.skill(kit))));
      pages.add(page(kit, "partners", problems, (ScreenSamples samples) -> samples.screens.openMutate(samples.viewer, ScreenSamples.skill(kit))));
    }
    String evidence = System.getProperty("adapt.pack.evidence");
    if (evidence != null) {
      Path out = Path.of(evidence);
      Files.createDirectories(out);
      List<String> ribbon = List.of("adaptation", "lead", "partner");
      ImageIO.write(sheet(pages, ribbon, List.of(Corner.TOP_LEFT)), "png", out.resolve("corners-left.png").toFile());
      ImageIO.write(sheet(pages, ribbon, List.of(Corner.TOP_RIGHT)), "png", out.resolve("corners-right.png").toFile());
      ImageIO.write(sheet(pages, List.of("skill"), List.of(Corner.values())), "png", out.resolve("corners-four.png").toFile());
      ImageIO.write(sheet(pages, List.of("partners"), List.of(Corner.values())), "png", out.resolve("corners-partners.png").toFile());
    }
    assertEquals(List.of(), problems);
  }

  /** A page corner's crop, from the page's drawn extent. */
  private enum Corner {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

    int x(int[] bounds) {
      return this == TOP_LEFT || this == BOTTOM_LEFT ? bounds[0] - OUTSIDE : bounds[2] + 1 + OUTSIDE - CROP_WIDTH;
    }

    int y(int[] bounds) {
      return this == TOP_LEFT || this == TOP_RIGHT ? bounds[1] - OUTSIDE : bounds[3] + 1 + OUTSIDE - CROP_HEIGHT;
    }
  }

  private static Page page(String kit, String name, List<String> problems, Consumer<ScreenSamples> open) {
    ScreenSamples samples = ScreenSamples.everyKit();
    samples.screens(resources);
    open.accept(samples);
    if (samples.windows.menu == null) {
      throw new IllegalStateException(kit + " " + name + " opened nothing");
    }
    ScreenCompositor compositor = new ScreenCompositor(files, null);
    Raster layer = compositor.layer(samples.windows.menu, samples.windows.surface, 0L, null);
    compositor.problems().forEach((String problem) -> problems.add(kit + " " + name + ": " + problem));
    int[] bounds = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
    for (int y = 0; y < layer.height(); y++) {
      for (int x = 0; x < layer.width(); x++) {
        if (layer.get(x, y) >>> 24 != 0) {
          bounds[0] = Math.min(bounds[0], x + ScreenCompositor.LEFT);
          bounds[1] = Math.min(bounds[1], y + ScreenCompositor.TOP);
          bounds[2] = Math.max(bounds[2], x + ScreenCompositor.LEFT);
          bounds[3] = Math.max(bounds[3], y + ScreenCompositor.TOP);
        }
      }
    }
    return new Page(kit, name, compositor.draw(samples.windows.menu, samples.windows.surface, 0L, null), bounds);
  }

  /** One row per kit: its name, then each of {@code names}' pages cropped at each of {@code corners}. */
  private static BufferedImage sheet(List<Page> pages, List<String> names, List<Corner> corners) {
    int cell = CROP_WIDTH * SCALE;
    int columns = names.size() * corners.size();
    int rowHeight = CROP_HEIGHT * SCALE + GAP;
    BufferedImage image = new BufferedImage(LABEL + columns * (cell + GAP), 24 + ScreenSamples.KITS.size() * rowHeight, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    graphics.setColor(new Color(0x15171A));
    graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
    graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
    graphics.setColor(Color.WHITE);
    int column = 0;
    for (String name : names) {
      for (Corner corner : corners) {
        graphics.drawString(name + " " + corner.name().toLowerCase().replace('_', ' '), LABEL + column * (cell + GAP), 16);
        column++;
      }
    }
    for (int row = 0; row < ScreenSamples.KITS.size(); row++) {
      String kit = ScreenSamples.KITS.get(row);
      int top = 24 + row * rowHeight;
      graphics.setColor(Color.WHITE);
      graphics.drawString(kit, 6, top + 20);
      column = 0;
      for (String name : names) {
        Page page = pages.stream().filter((Page each) -> each.kit().equals(kit) && each.name().equals(name)).findFirst().orElseThrow();
        for (Corner corner : corners) {
          Raster crop = page.screen().crop(corner.x(page.bounds()) - ScreenCompositor.LEFT, corner.y(page.bounds()) - ScreenCompositor.TOP,
              CROP_WIDTH, CROP_HEIGHT);
          graphics.drawImage(scaled(crop).image(), LABEL + column * (cell + GAP), top, null);
          column++;
        }
      }
    }
    graphics.dispose();
    return image;
  }

  private static Raster scaled(Raster raster) {
    Raster out = new Raster(raster.width() * SCALE, raster.height() * SCALE);
    for (int y = 0; y < out.height(); y++) {
      for (int x = 0; x < out.width(); x++) {
        out.set(x, y, raster.get(x / SCALE, y / SCALE));
      }
    }
    return out;
  }

  private static Map<String, byte[]> unzip(byte[] zip) {
    Map<String, byte[]> entries = new TreeMap<>();
    try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
      for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
        entries.put(entry.getName(), in.readAllBytes());
      }
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
    return entries;
  }
}
