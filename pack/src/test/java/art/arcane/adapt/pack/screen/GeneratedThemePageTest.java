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
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.content.skill.SkillPickaxes;
import art.arcane.adapt.engine.asset.PngImages;
import art.arcane.adapt.engine.asset.ThemeAssets;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.pack.DeliveredResources;
import art.arcane.adapt.pack.FakeMenuHost;
import art.arcane.adapt.pack.FakePackWindows;
import art.arcane.adapt.pack.PackResources;
import art.arcane.adapt.pack.TestCatalogs;
import art.arcane.adapt.pack.bake.PackBake;
import art.arcane.adapt.pack.layout.TextSpec;
import art.arcane.adapt.pack.menu.PackMenuHost;
import art.arcane.adapt.pack.runtime.PackBuilds;
import art.arcane.adapt.pack.runtime.PackDrops;
import art.arcane.adapt.pack.surface.TitleRun;
import art.arcane.adapt.pack.text.LegacyText;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An addon skill that ships no kit gets a generated theme framing its own icon, and its page and the page of a mutation
 * it leads lay out as authored pages do: the generated kit dresses the page, the name fits the identity row in the
 * default font, and no text on any leaf is cut.
 */
class GeneratedThemePageTest {
  private static final String NS = "strata";
  private static final Id GEOLOGY = Id.of(NS, "geology");
  private static final Id SURVEY = Id.of(NS, "geology/survey");
  private static final Id PICKAXE = Id.adapt("pickaxe");
  private static final SkillPair PAIR = new SkillPair(GEOLOGY, PICKAXE);
  private static final int ICON_ARGB = 0xFF7A5C3B;
  private static final String CUT = "..";
  private static final int LEAVES = 3;

  @TempDir
  Path data;

  @TempDir
  Path cache;

  @Test
  void anAddonSkillWithoutAKitAndTheMutationItLeadsLayOutAsAuthoredPages() {
    TestPlatform platform = TestPlatform.builder(data).build();
    FakePlayer player = platform.player("Viewer", 0.5D, 61D, 0.5D);
    try (TestAdapt adapt = TestAdapt.start(platform, List.of(SkillPickaxes.class))) {
      adapt.core().assetInstaller().install(TestCatalogs.builtinBundle(Set.of("pickaxe")), Id.ADAPT);
      AdaptPlayer profile = adapt.join(player);
      PackBuilds builds = new PackBuilds(adapt.core().assets(), "3.0.0", cache, new PackDrops(cache.resolve("drops")), Runnable::run, 4, PackBake::build);
      builds.start();
      AddonHandle strata = AdaptApi.addons().install(new StrataAddon());
      try {
        assertEquals(AddonState.ACTIVE, strata.state());
        ThemeAssets theme = adapt.core().assets().theme(GEOLOGY).orElseThrow();
        assertTrue(theme.generated(), "no kit shipped, so the theme is generated");
        int[] atlas = PngImages.decode(theme.atlasPng());
        assertTrue(Arrays.stream(atlas).anyMatch((int pixel) -> pixel == ICON_ARGB), "the generated kit frames the skill's icon");

        new MutationKit(adapt, profile).mutate(GEOLOGY.toString(), PICKAXE.toString());
        assertEquals(new PackResources.PageKit("strata:geology", false), PackResources.kit(PAIR.key(), 0));
        assertEquals(new PackResources.PageKit("adapt:pickaxe", true), PackResources.kit(PAIR.key(), 1));

        ServerPackBackend backend = new ServerPackBackend();
        FakePackWindows windows = new FakePackWindows();
        PackMenuHost host = new PackMenuHost(new FakeMenuHost(), windows, (PlayerRef viewer) -> true,
            new DeliveredResources(() -> builds.manifests().current()));
        PackScreens screens = new PackScreens(host, backend, backend, new ScreenFixtures.Prompts(), new ScreenFixtures.Clock());
        PackResources resources = new DeliveredResources(() -> builds.manifests().current()).get();
        TextSpec nameSpec = resources.layout().screen(PackSkillScreen.SCREEN, true).text("name");

        List<String> problems = new ArrayList<>();
        for (String skill : List.of(GEOLOGY.toString(), PAIR.key())) {
          SkillView view = backend.catalog(player).catalog().skills().stream()
              .filter((SkillView candidate) -> candidate.id().equals(skill)).findFirst().orElse(null);
          assertNotNull(view, skill + " is listed");
          if (!resources.fitsDefaultFont(nameSpec, LegacyText.plain(view.name()))) {
            problems.add(view.name() + " does not fit the identity row");
          }
          for (int leaf = 0; leaf < LEAVES; leaf++) {
            assertTrue(screens.openSkill(player, skill), skill + " opens");
            screens.page(player, leaf);
            FakePackWindows.Window window = windows.window(player);
            boolean dressed = false;
            for (TitleRun run : window.surface.title()) {
              dressed |= run.font().startsWith(NS + ":");
              if (run.text().contains(CUT)) {
                problems.add(skill + " leaf " + leaf + " cuts " + run.text().strip());
              }
            }
            if (leaf == 0 && !dressed) {
              problems.add(skill + " is not dressed in the generated kit");
            }
          }
        }
        assertEquals(List.of(), problems);
      } finally {
        if (strata.state() == AddonState.ACTIVE) {
          strata.uninstall();
        }
        builds.stop();
      }
    }
  }

  private record StrataAddon() implements AdaptAddon {
    @Override
    public String namespace() {
      return NS;
    }

    @Override
    public void register(ContentRegistrar registrar) {
      registrar.skill(GEOLOGY, Geology::new);
      Map<String, byte[]> files = new TreeMap<>();
      files.put("textures/gui/skill/geology.png", icon(ICON_ARGB));
      files.put("textures/gui/adaptation/geology/survey.png", icon(0xFF40C080));
      registrar.assets(TestCatalogs.memory(NS, files));
    }

    private static byte[] icon(int argb) {
      int[] pixels = new int[16 * 16];
      Arrays.fill(pixels, argb);
      return PngImages.encode(16, 16, pixels);
    }
  }

  public static final class Geology extends SimpleSkill<SkillConfig> {
    public Geology() {
      super(GEOLOGY);
      registerConfiguration(SkillConfig.class);
      setColor(Rgb.of(0x8A6F4E));
      registerAdaptation(SURVEY, Survey::new);
    }

    @Override
    public boolean isEnabled() {
      return true;
    }
  }

  public static final class Survey extends SimpleAdaptation<AdaptationConfig> {
    public Survey() {
      super(SURVEY);
      registerConfiguration(AdaptationConfig.class);
      setLocalizationKey("pickaxe.chisel");
    }

    @Override
    public void addStats(int level, StatLines lines) {
    }
  }
}
