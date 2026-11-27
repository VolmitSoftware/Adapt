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
import art.arcane.adapt.engine.asset.PngImages;
import art.arcane.adapt.pack.DeliveredResources;
import art.arcane.adapt.pack.FakeMenuHost;
import art.arcane.adapt.pack.FakePackWindows;
import art.arcane.adapt.pack.TestCatalogs;
import art.arcane.adapt.pack.bake.PackBake;
import art.arcane.adapt.pack.bake.PackManifest;
import art.arcane.adapt.pack.menu.PackMenuHost;
import art.arcane.adapt.pack.runtime.PackBuilds;
import art.arcane.adapt.pack.runtime.PackDrops;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A third-party addon installed and uninstalled while the server runs, seen through the pack build and an open page. */
class AddonPackRebuildTest {
  private static final String NS = "orbit";
  private static final Id COMET = Id.of(NS, "comet");
  private static final Id DASH = Id.of(NS, "comet/dash");

  @TempDir
  Path data;

  @TempDir
  Path cache;

  @Test
  void anAddonArrivingLateIsBakedInAndItsUninstallBakesItOutAndClosesItsOpenPage() {
    TestPlatform platform = TestPlatform.builder(data).build();
    FakePlayer player = platform.player("Viewer", 0.5D, 61D, 0.5D);
    try (TestAdapt adapt = TestAdapt.start(platform)) {
      // The test classpath has no asset index, so the core starts with an empty adapt bundle.
      adapt.core().assetInstaller().install(TestCatalogs.builtinBundle(Set.of()), Id.ADAPT);
      adapt.join(player);
      PackBuilds builds = new PackBuilds(adapt.core().assets(), "3.0.0", cache, new PackDrops(cache.resolve("drops")), Runnable::run, 4, PackBake::build);
      builds.start();
      AddonHandle orbit = null;
      try {
        assertFalse(orbit(pack(builds)), "orbit art before the addon");
        FakePackWindows windows = new FakePackWindows();
        PackMenuHost host = new PackMenuHost(new FakeMenuHost(), windows, (PlayerRef viewer) -> true,
            new DeliveredResources(() -> builds.manifests().current()));
        ServerPackBackend backend = new ServerPackBackend();
        ScreenFixtures.Clock clock = new ScreenFixtures.Clock();
        PackScreens screens = new PackScreens(host, backend, backend, new ScreenFixtures.Prompts(), clock);

        orbit = AdaptApi.addons().install(new OrbitAddon());

        assertEquals(AddonState.ACTIVE, orbit.state());
        Map<String, byte[]> arrived = pack(builds);
        assertTrue(arrived.containsKey("assets/orbit/textures/gui/skill/comet.png"), "skill icon");
        assertTrue(arrived.containsKey("assets/orbit/items/adapt_ui/gui/adaptation/comet/dash.json"), "adaptation icon item");
        assertTrue(arrived.keySet().stream().anyMatch((String path) -> path.startsWith("assets/orbit/font/page/comet/")), "page font");
        assertTrue(manifest(arrived).contains("\"orbit:comet\""), "manifest names the kit");
        assertTrue(screens.openSkill(player, COMET.toString()), "comet page opens");
        FakePackWindows.Window page = windows.window(player);

        orbit.uninstall();

        Map<String, byte[]> gone = pack(builds);
        assertFalse(orbit(gone), "orbit art after the uninstall");
        assertFalse(manifest(gone).contains("orbit:"), "manifest still names orbit");
        assertDoesNotThrow(() -> {
          screens.refresh(player);
          clock.run();
        });
        assertFalse(page.open, "the page of the removed skill stays open");
        assertTrue(screens.openSkills(player), "Skills opens");
        assertNotSame(page, windows.window(player));
      } finally {
        if (orbit != null && orbit.state() == AddonState.ACTIVE) {
          orbit.uninstall();
        }
        builds.stop();
      }
    }
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

  private static boolean orbit(Map<String, byte[]> pack) {
    return pack.keySet().stream().anyMatch((String path) -> path.startsWith("assets/orbit/"));
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

    /** The herbalism kit and atlas as comet's, its skill icon and one adaptation icon. */
    private static Map<String, byte[]> files() {
      Path builtin = TestCatalogs.builtinRoot().resolve("assets").resolve(Id.ADAPT);
      try {
        String kit = Files.readString(builtin.resolve("themes/herbalism.json"), StandardCharsets.UTF_8)
            .replace("\"skill\":\"herbalism\"", "\"skill\":\"comet\"");
        Map<String, byte[]> files = new TreeMap<>();
        files.put("themes/comet.json", kit.getBytes(StandardCharsets.UTF_8));
        files.put("textures/gui/theme/comet.png", Files.readAllBytes(builtin.resolve("textures/gui/theme/herbalism.png")));
        files.put("textures/gui/skill/comet.png", icon(0xFF30A0E0));
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
