package art.arcane.adapt.pack.screen;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.AddonHandle;
import art.arcane.adapt.api.addon.AddonState;
import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.player.OrphanedLine;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.CatalogIndex;
import art.arcane.adapt.client.view.IconSprites;
import art.arcane.adapt.client.view.MutationIcons;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.content.skill.SkillPickaxes;
import art.arcane.adapt.engine.asset.PngImages;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.mutation.blocks.MutatedText;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.pack.DeliveredResources;
import art.arcane.adapt.pack.FakeMenuHost;
import art.arcane.adapt.pack.FakePackWindows;
import art.arcane.adapt.pack.PackResources;
import art.arcane.adapt.pack.TestCatalogs;
import art.arcane.adapt.pack.bake.PackBake;
import art.arcane.adapt.pack.bake.PackManifest;
import art.arcane.adapt.pack.layout.PackLayoutReader;
import art.arcane.adapt.pack.manifest.PackManifestReader;
import art.arcane.adapt.pack.menu.PackMenuHost;
import art.arcane.adapt.pack.runtime.PackBuilds;
import art.arcane.adapt.pack.runtime.PackDrops;
import art.arcane.adapt.pack.surface.SlotLook;
import art.arcane.adapt.pack.surface.TitleRun;
import art.arcane.adapt.pack.text.LegacyText;
import art.arcane.adapt.pack.text.PackFontMetrics;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A mutated skill whose lead is an addon skill that ships no mutation art, through the pack, and its line suspended. */
class AddonMutationPackTest {
  private static final String NS = "orbit";
  private static final Id COMET = Id.of(NS, "comet");
  private static final Id DASH = Id.of(NS, "comet/dash");
  private static final Id PICKAXE = Id.adapt("pickaxe");
  private static final SkillPair PAIR = new SkillPair(COMET, PICKAXE);
  private static final int COMET_RGB = 0x2E5FD0;
  private static final String EMBLEM = "orbit:gui/emblem/comet";
  private static final String TRIM = "orbit:gui/trim/comet";

  @TempDir
  Path data;

  @TempDir
  Path cache;

  @Test
  void anAddonLedMutationGetsGeneratedArtItsLeadTintItsNameAndBothKits() {
    TestPlatform platform = TestPlatform.builder(data).build();
    FakePlayer player = platform.player("Viewer", 0.5D, 61D, 0.5D);
    try (TestAdapt adapt = TestAdapt.start(platform, List.of(SkillPickaxes.class))) {
      adapt.core().assetInstaller().install(TestCatalogs.builtinBundle(Set.of("pickaxe")), Id.ADAPT);
      AdaptPlayer profile = adapt.join(player);
      PackBuilds builds = new PackBuilds(adapt.core().assets(), "3.0.0", cache, new PackDrops(cache.resolve("drops")), Runnable::run, 4, PackBake::build);
      builds.start();
      AddonHandle orbit = AdaptApi.addons().install(new OrbitAddon());
      try {
        assertEquals(AddonState.ACTIVE, orbit.state());
        new MutationKit(adapt, profile).mutate(COMET.toString(), PICKAXE.toString());
        MutatedSkill mutated = adapt.core().getAdaptServer().getSkillRegistry().getMutatedSkill(PAIR);
        assertNotNull(mutated);

        Map<String, byte[]> pack = pack(builds);
        for (String sprite : List.of(EMBLEM, TRIM)) {
          String path = sprite.substring(NS.length() + 1);
          assertTrue(pack.containsKey("assets/orbit/textures/" + path + ".png"), "generated " + sprite);
          assertTrue(pack.containsKey("assets/orbit/items/adapt_ui/" + path + ".json"), "item of " + sprite);
        }
        PackResources resources = resources(pack);

        ServerPackBackend backend = new ServerPackBackend();
        SkillView view = skill(backend.catalog(player), PAIR.key());
        assertNotNull(view, "the mutated skill is listed");
        assertEquals(MutatedText.mutationName(MutatedText.ENGLISH, mutated.parents()), LegacyText.plain(view.name()));
        assertFalse(view.name().contains("{") || view.name().contains("mutated."), view.name());

        assertEquals(MutationIcons.tile(PAIR), view.icon().spriteId());
        SlotLook tile = resources.icon(view.icon(), PackResources.kit(PAIR.key(), 0).kit(), false);
        assertEquals(List.of(EMBLEM, "adapt:gui/trim/pickaxe"), tile.strings());

        int tint = MutationIcons.tint(mutated.parents().lead());
        assertNotEquals(IconSprites.NO_TINT, tint);
        assertTrue(MutationIcons.tints(adapt.core().assets()).contains(tint), "the lead's tint is baked");
        AdaptationView glyph = view.adaptations().stream()
            .filter((AdaptationView adaptation) -> IconSprites.layers(adaptation.icon().spriteId()).size() == 3)
            .findFirst().orElseThrow();
        SlotLook drawn = resources.icon(glyph.icon(), PackResources.kit(PAIR.key(), 0).kit(), false);
        assertTrue(drawn.colours().contains(tint), "glyph drawn in the lead's tint: " + drawn.colours());

        assertEquals(new PackResources.PageKit("orbit:comet", false), PackResources.kit(PAIR.key(), 0));
        assertEquals(new PackResources.PageKit("adapt:pickaxe", true), PackResources.kit(PAIR.key(), 1));
        FakePackWindows windows = new FakePackWindows();
        PackScreens screens = screens(windows, builds, backend, new ScreenFixtures.Clock());
        assertTrue(screens.openSkill(player, PAIR.key()), "the mutated page opens");
        assertTrue(windows.window(player).surface.title().stream().anyMatch((TitleRun run) -> run.font().startsWith("orbit:")),
            "the lead's kit dresses the page");
      } finally {
        if (orbit.state() == AddonState.ACTIVE) {
          orbit.uninstall();
        }
        builds.stop();
      }
    }
  }

  @Test
  void aSuspendedLineDrawsNoTileAndItsOpenPageCloses() {
    TestPlatform platform = TestPlatform.builder(data).build();
    FakePlayer player = platform.player("Viewer", 0.5D, 61D, 0.5D);
    try (TestAdapt adapt = TestAdapt.start(platform, List.of(SkillPickaxes.class))) {
      adapt.core().assetInstaller().install(TestCatalogs.builtinBundle(Set.of("pickaxe")), Id.ADAPT);
      AdaptPlayer profile = adapt.join(player);
      PackBuilds builds = new PackBuilds(adapt.core().assets(), "3.0.0", cache, new PackDrops(cache.resolve("drops")), Runnable::run, 4, PackBake::build);
      builds.start();
      AddonHandle orbit = AdaptApi.addons().install(new OrbitAddon());
      try {
        new MutationKit(adapt, profile).mutate(COMET.toString(), PICKAXE.toString());
        FakePackWindows windows = new FakePackWindows();
        ServerPackBackend backend = new ServerPackBackend();
        ScreenFixtures.Clock clock = new ScreenFixtures.Clock();
        PackScreens screens = screens(windows, builds, backend, clock);
        String name = LegacyText.plain(skill(backend.catalog(player), PAIR.key()).name());
        assertTrue(screens.openSkills(player));
        assertTrue(drawn(windows.window(player)).contains(name), "the mutated tile before the uninstall");
        assertTrue(screens.openSkill(player, PAIR.key()));
        FakePackWindows.Window page = windows.window(player);

        orbit.uninstall();

        assertTrue(profile.getData().getMutations().contains(PAIR), "the line is kept in the save");
        assertTrue(AdaptApi.players().find(player.uuid()).orElseThrow().orphans().stream()
            .anyMatch((OrphanedLine line) -> line.line().equals(new OrphanedLine.Pair(PAIR))), "the line is listed as orphaned");
        assertEquals(null, skill(backend.catalog(player), PAIR.key()), "the suspended line is not listed");
        screens.refresh(player);
        clock.run();
        assertFalse(page.open, "the suspended line's page stays open");
        assertFalse(screens.openSkill(player, PAIR.key()), "the suspended page opens");
        assertTrue(screens.openSkills(player));
        assertFalse(drawn(windows.window(player)).contains(name), "a tile for the suspended line");
        assertFalse(manifest(pack(builds)).contains("orbit:"), "orbit art after the uninstall");
      } finally {
        if (orbit.state() == AddonState.ACTIVE) {
          orbit.uninstall();
        }
        builds.stop();
      }
    }
  }

  private static PackScreens screens(FakePackWindows windows, PackBuilds builds, ServerPackBackend backend, ScreenFixtures.Clock clock) {
    PackMenuHost host = new PackMenuHost(new FakeMenuHost(), windows, (PlayerRef viewer) -> true,
        new DeliveredResources(() -> builds.manifests().current()));
    return new PackScreens(host, backend, backend, new ScreenFixtures.Prompts(), clock, new ScreenFixtures.Speaker());
  }

  private static SkillView skill(CatalogIndex catalog, String id) {
    return catalog.catalog().skills().stream().filter((SkillView view) -> view.id().equals(id)).findFirst().orElse(null);
  }

  /** The plain names of every item on the open window, top and lower slots. */
  private static List<String> drawn(FakePackWindows.Window window) {
    List<String> names = new ArrayList<>();
    List<MenuButton> buttons = new ArrayList<>(window.menu.buttons().values());
    buttons.addAll(window.surface.lower().values());
    for (MenuButton button : buttons) {
      if (button.icon().name() != null) {
        names.add(LegacyText.plain(button.icon().name()));
      }
    }
    return names;
  }

  private static PackResources resources(Map<String, byte[]> pack) {
    return new PackResources(PackManifestReader.read(new StringReader(manifest(pack))), PackLayoutReader.builtIn(),
        PackFontMetrics.vanilla());
  }

  private static Map<String, byte[]> pack(PackBuilds builds) {
    Map<String, byte[]> files = new TreeMap<>();
    try (InputStream zip = builds.source().current().orElseThrow().open();
         ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip.readAllBytes()))) {
      ZipEntry entry;
      while ((entry = in.getNextEntry()) != null) {
        files.put(entry.getName(), in.readAllBytes());
      }
      return files;
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private static String manifest(Map<String, byte[]> pack) {
    return new String(pack.get(PackManifest.PATH), StandardCharsets.UTF_8);
  }

  private record OrbitAddon() implements AdaptAddon {
    @Override
    public String namespace() {
      return NS;
    }

    @Override
    public void register(ContentRegistrar registrar) {
      registrar.skill(COMET, Comet::new);
      registrar.assets(TestCatalogs.memory(NS, files()));
    }

    /** The herbalism kit and atlas as comet's, its skill icon and one adaptation icon; no emblem or trim. */
    private static Map<String, byte[]> files() {
      Path builtin = TestCatalogs.builtinRoot().resolve("assets").resolve(Id.ADAPT);
      try {
        String kit = Files.readString(builtin.resolve("themes/herbalism.json"), StandardCharsets.UTF_8)
            .replace("\"skill\":\"herbalism\"", "\"skill\":\"comet\"");
        Map<String, byte[]> files = new TreeMap<>();
        files.put("themes/comet.json", kit.getBytes(StandardCharsets.UTF_8));
        files.put("textures/gui/theme/comet.png", Files.readAllBytes(builtin.resolve("textures/gui/theme/herbalism.png")));
        files.put("textures/gui/skill/comet.png", icon(0xFF000000 | COMET_RGB));
        files.put("textures/gui/adaptation/comet/dash.png", icon(0xFF40C080));
        return files;
      } catch (IOException failure) {
        throw new UncheckedIOException(failure);
      }
    }

    private static byte[] icon(int argb) {
      int[] pixels = new int[16 * 16];
      Arrays.fill(pixels, argb);
      return PngImages.encode(16, 16, pixels);
    }
  }

  public static final class Comet extends SimpleSkill<SkillConfig> {
    public Comet() {
      super(COMET);
      registerConfiguration(SkillConfig.class);
      setColor(Rgb.of(COMET_RGB));
      registerAdaptation(DASH, Dash::new);
    }

    @Override
    public boolean isEnabled() {
      return true;
    }
  }

  public static final class Dash extends SimpleAdaptation<AdaptationConfig> {
    public Dash() {
      super(DASH);
      registerConfiguration(AdaptationConfig.class);
      setLocalizationKey("pickaxe.chisel");
    }

    @Override
    public void addStats(int level, StatLines lines) {
    }
  }
}
