package art.arcane.adapt.engine.bridge;

import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.content.XpSource;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.engine.addon.BuildScope;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.TextBundle;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.world.NightSkipEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.volmlib.util.localization.TextKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComponentBuilderTest {
  private static final Id TINKER = Id.of("demo", "tinker");
  private static final Id SPARK = Id.of("demo", "tinker/spark");
  private static final Id GLOW = Id.of("demo", "tinker/glow");

  @TempDir
  Path data;

  private TestAdapt adapt;
  private FakePlayer player;

  @AfterEach
  void stop() {
    AdaptLanguage.texts().uninstall("demo");
    if (adapt != null) {
      adapt.close();
      assertThat(adapt.handlerFailures()).isEmpty();
    }
  }

  @Test
  void aPublicSkillIsBuiltWithItsAdaptationsAndReadsItsConfigFile() throws IOException {
    Path config = data.resolve("skills").resolve("demo").resolve("tinker.toml");
    Files.createDirectories(config.getParent());
    Files.writeString(config, "craftXp = 40.0\n");
    start();

    Skill<?> skill = build(DemoSkill::new);

    assertThat(skill.getId()).isEqualTo(TINKER);
    assertThat(skill.getAdaptations()).extracting(Adaptation::getId).containsExactly(SPARK, GLOW);
    DemoSkill shown = (DemoSkill) skill;
    assertThat(shown.getConfig().craftXp).isEqualTo(40D);
    assertThat(shown.getOwner()).isEqualTo("demo");
    assertThat(shown.getAdaptations()).hasSize(2);
    assertThat(((DemoSpark) shown.getAdaptations().getFirst()).getSkill()).isSameAs(shown);
    assertThat(skill.getAdaptations().getFirst().getMaxLevel()).isEqualTo(3);
  }

  @Test
  void handlersFireThroughTheBusAndAwardXpAndLevelsResolve() {
    start();
    AdaptPlayer profile = adapt.join(player);
    Engine.addons().install(new DemoAddon());
    Skill<?> skill = adapt.core().getAdaptServer().getSkillRegistry().getSkill(TINKER);
    Adaptation<?> spark = skill.getAdaptations().getFirst();
    profile.getData().getSkillLine(TINKER.toString()).setAdaptation(spark, 2);
    DemoSkill shown = (DemoSkill) skill;
    DemoSpark sparkShown = (DemoSpark) spark;

    adapt.post(new NightSkipEvent(player.world(), List.of(player)));

    assertThat(shown.nights).isEqualTo(1);
    assertThat(sparkShown.getLevel(player)).isEqualTo(2);
    assertThat(sparkShown.lastActive).isEqualTo(2);
    assertThat(profile.getData().getXpEarnings().snapshot())
        .anyMatch(snapshot -> snapshot.source().contains("demo:tinker"));
    ComponentHost.of(skill).retireComponent();
    assertThat(shown.isRuntimeRegistered()).isFalse();
  }

  @Test
  void textResolvesFromAnInstalledBundle() {
    start();
    AdaptLanguage.texts().install(TextBundle.declared("demo",
        List.of(TextKey.of("demo.skill.tinker.name", "Tinkering"),
            TextKey.of("demo.skill.tinker.greeting", "Hello {who}")), List.of()));
    Skill<?> skill = build(DemoSkill::new);
    DemoSkill shown = (DemoSkill) skill;

    assertThat(shown.getLocalizedName()).contains("Tinkering");
    assertThat(Text.key("demo.skill.tinker.greeting").with("who", "Ada").resolve()).contains("Hello Ada");
  }

  @Test
  void aFactoryThatBuildsTwoSkillsOrMisnamesAnAdaptationIsRefused() {
    start();
    assertThatThrownBy(() -> build(() -> {
      new DemoSkill();
      return new DemoSkill();
    })).isInstanceOf(IllegalStateException.class).hasMessageContaining("second skill");
    assertThatThrownBy(() -> build(MisnamedSkill::new)).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("demo:tinker/wrong");
  }

  @Test
  void contentCannotBeBuiltWithoutARunningCore() {
    assertThat(Engine.isRunning()).isFalse();
    assertThatThrownBy(Engine::bridge).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(DemoSkill::new).isInstanceOf(IllegalStateException.class);
  }

  private void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    player = platform.player("Tinkerer", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of());
  }

  private static final class DemoAddon implements AdaptAddon {
    @Override
    public String namespace() {
      return "demo";
    }

    @Override
    public void register(ContentRegistrar registrar) {
      registrar.skill(TINKER, DemoSkill::new);
    }
  }

  private static Skill<?> build(Supplier<? extends SimpleSkill<?>> factory) {
    return BuildScope.run("demo", () -> ComponentBuilder.buildSkill(factory));
  }

  public static final class DemoSkill extends SimpleSkill<DemoSkill.Config> {
    private final XpSource night = xpSource("night");
    int nights;

    public DemoSkill() {
      super(TINKER);
      registerConfiguration(Config.class);
      setColor(Rgb.of(0x7A4FD1));
      registerAdaptation(SPARK, DemoSpark::new);
      registerAdaptation(GLOW, DemoGlow::new);
    }

    @Handle(priority = EventPriority.MONITOR)
    public void on(NightSkipEvent event) {
      for (PlayerRef sleeper : event.sleepers()) {
        nights++;
        night.award(sleeper, getConfig().craftXp);
      }
    }

    public static final class Config extends SkillConfig {
      public double craftXp = 25D;
    }
  }

  public static final class MisnamedSkill extends SimpleSkill<SkillConfig> {
    public MisnamedSkill() {
      super(TINKER);
      registerConfiguration(SkillConfig.class);
      registerAdaptation(Id.of("demo", "tinker/wrong"), DemoGlow::new);
    }
  }

  public static final class DemoSpark extends SimpleAdaptation<DemoSpark.Config> {
    int lastActive = -1;

    public DemoSpark() {
      super(SPARK);
      registerConfiguration(Config.class);
    }

    @Handle(priority = EventPriority.MONITOR)
    public void on(NightSkipEvent event) {
      for (PlayerRef sleeper : event.sleepers()) {
        lastActive = getActiveLevel(sleeper);
      }
    }

    @Override
    public void addStats(int level, StatLines lines) {
      lines.addLore("+" + level);
    }

    public static final class Config extends AdaptationConfig {
      public Config() {
        maxLevel = 3;
      }
    }
  }

  public static final class DemoGlow extends SimpleAdaptation<AdaptationConfig> {
    public DemoGlow() {
      super(GLOW);
      registerConfiguration(AdaptationConfig.class);
    }

    @Override
    public void addStats(int level, StatLines lines) {
    }
  }
}
