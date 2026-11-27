package art.arcane.adapt.engine.bridge;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.AdaptClock;
import art.arcane.adapt.api.ability.AbilityContext;
import art.arcane.adapt.api.ability.AbilityUseDecision;
import art.arcane.adapt.api.ability.AbilityUsePolicy;
import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.AddonHandle;
import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.platform.protection.ProtectionCheck;
import art.arcane.adapt.api.protection.Protector;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.TextBundle;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.volmlib.util.localization.TextKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/** The engine side of the second API amendment: names, locales, the clock, extensions and owned hooks. */
class ApiSeamTest {
  private static final Id SWORDS = Id.adapt("swords");
  private static final Id EDGE = Id.of("edge", "swords/edge");

  @TempDir
  Path data;

  private TestAdapt adapt;

  @AfterEach
  void stop() {
    AdaptLanguage.texts().uninstall("edge");
    AdaptLanguage.texts().uninstall("demo");
    if (adapt != null) {
      adapt.close();
      assertThat(adapt.handlerFailures()).isEmpty();
    }
  }

  @Test
  void aReplacementNameTextIsTheEngineDisplayName() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build(), new BuiltinAddon());
    AdaptApi.addons().install(new EdgeAddon());
    AdaptLanguage.texts().install(TextBundle.declared("edge",
        List.of(TextKey.of("edge.renamed", "Keen Edge")), List.of()));

    art.arcane.adapt.api.content.Adaptation<?> engine =
        adapt.core().getAdaptServer().getSkillRegistry().getAdaptation(EDGE);
    assertThat(engine.getDisplayName()).contains("Keen Edge");
  }

  @Test
  void anExtensionOnABuiltInSkillReachesThatSkill() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build(), new BuiltinAddon());

    AdaptApi.addons().install(new EdgeAddon());

    Adaptation<?> edge = AdaptApi.content().adaptation(EDGE).orElseThrow();
    assertThat(edge.getSkillId()).isEqualTo(SWORDS);
    assertThat(edge.getSkill()).isSameAs(AdaptApi.content().skill(SWORDS).orElseThrow());
  }

  @Test
  void effectsOfAPublicAdaptationRunThroughTheEngine() {
    TestPlatform platform = TestPlatform.builder(data).build();
    PlayerRef player = platform.player("Viewer", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, new BuiltinAddon());
    AdaptApi.addons().install(new EdgeAddon());
    Edge edge = (Edge) AdaptApi.content().adaptation(EDGE).orElseThrow();

    assertThat(edge.emitterAt(player.location())).isNotNull();
    assertThat(edge.timelineOn(player)).isInstanceOf(art.arcane.adapt.api.fx.FxTimeline.class);
    assertThat(Fx.now(edge, player, FxPriority.GAMEPLAY).color()).isNotNull();
    assertThat(FxTimeline.at(edge, player.location())).isInstanceOf(art.arcane.adapt.api.fx.FxTimeline.class);
    FxPresets.failFizzle(edge, player);
    FxPresets.chargeRing(edge, player.location(), 6);
  }

  @Test
  void textResolvesInTheNamedLocaleWhoeverIsRendering() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build());
    AdaptLanguage.texts().install(TextBundle.resources("demo", getClass().getClassLoader(), "/text-bundles/demo/"));

    assertThat(Text.key("demo.skill.name").resolve("de_DE")).isEqualTo("Basteln");
    assertThat(Text.key("demo.skill.name").resolve("en_US")).isEqualTo("Tinkering");
    assertThat(Text.key("demo.skill.name").resolve()).isEqualTo("Tinkering");
  }

  @Test
  void testsDriveTheApiClockThroughTheCore() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build());
    AdaptCore stubbed = spy(adapt.core());
    when(stubbed.clock()).thenReturn(new AdaptClock() {
      @Override
      public long millis() {
        return 1_000L;
      }

      @Override
      public long tick() {
        return 20L;
      }
    });
    TestCores.use(stubbed);
    try {
      assertThat(AdaptApi.clock().millis()).isEqualTo(1_000L);
      assertThat(AdaptApi.clock().tick()).isEqualTo(20L);
    } finally {
      TestCores.use(adapt.core());
    }
    assertThat(AdaptApi.clock().millis()).isNotEqualTo(1_000L);
  }

  @Test
  void hooksRegisteredWithANamespaceLeaveWithTheirAddonOnASharedClassLoader() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build());
    AddonHandle handle = AdaptApi.addons().install(new EdgeAddon());
    Protector keeper = new Allowing("keeper");
    AbilityUsePolicy policy = (AbilityContext context) -> AbilityUseDecision.allow();
    AdaptApi.protection().register("edge", new Allowing("edge-guard"));
    AdaptApi.protection().register(keeper);
    AdaptApi.abilities().registerPolicy("edge", policy);
    try {
      handle.uninstall();

      assertThat(AdaptApi.protection().names()).containsExactly("keeper");
      assertThat(Engine.abilities().policies(HostKeys.key())).doesNotContain(policy);
    } finally {
      AdaptApi.protection().unregister(keeper);
    }
  }

  @Test
  void hooksAreDroppedOnUninstallWhileNoCoreRuns() {
    AddonHandle handle = AdaptApi.addons().install(new EdgeAddon());
    AdaptApi.protection().register("edge", new Allowing("edge-guard"));

    handle.uninstall();

    assertThat(AdaptApi.protection().names()).doesNotContain("edge-guard");
    assertThatThrownBy(() -> AdaptApi.protection().register("Not A Namespace", new Allowing("bad")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static final class EdgeAddon implements AdaptAddon {
    @Override
    public String namespace() {
      return "edge";
    }

    @Override
    public void register(ContentRegistrar registrar) {
      registrar.adaptation(SWORDS, EDGE, Edge::new);
    }
  }

  public static final class Edge extends SimpleAdaptation<AdaptationConfig> {
    public Edge() {
      super(EDGE);
      registerConfiguration(AdaptationConfig.class);
      setNameText(Text.key("edge.renamed"));
    }

    @Override
    public void addStats(int level, StatLines lines) {
    }

    FxEmitter emitterAt(Loc location) {
      return fx(location, FxPriority.COMBAT);
    }

    FxTimeline timelineOn(PlayerRef player) {
      return timeline(player).duration(4).frame((emitter, tick, progress) -> emitter.burst(Particles.CRIT, 2, 0.1D));
    }
  }

  private record Allowing(String name) implements Protector {
    @Override
    public boolean enabledByDefault() {
      return true;
    }

    @Override
    public boolean allows(ProtectionCheck check, PlayerRef player, Loc location, EntityRef victim, Id adaptation) {
      return true;
    }
  }
}
