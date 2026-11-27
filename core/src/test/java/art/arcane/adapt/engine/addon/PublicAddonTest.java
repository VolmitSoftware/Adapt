package art.arcane.adapt.engine.addon;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.AddonHandle;
import art.arcane.adapt.api.addon.AddonState;
import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.asset.AssetBundle;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.ContentVeto;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.event.AdaptationLearnEvent;
import art.arcane.adapt.api.event.AddonInstalledEvent;
import art.arcane.adapt.api.event.SkillXpEvent;
import art.arcane.adapt.api.player.LearnOptions;
import art.arcane.adapt.api.player.LearnResult;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.xp.XpAward;
import art.arcane.adapt.api.event.AddonUninstalledEvent;
import art.arcane.adapt.api.event.CatalogChangedEvent;
import art.arcane.adapt.api.event.EventSubscription;
import art.arcane.adapt.api.text.MessageBundle;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.engine.asset.AssetCatalog;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** A third-party addon with a public-typed skill, two adaptations, messages and art, from install to reinstall. */
class PublicAddonTest {
  static final Id TINKERING = Id.of("tinker", "tinkering");
  static final Id GEARS = Id.of("tinker", "tinkering/gears");
  static final Id SPRINGS = Id.of("tinker", "tinkering/springs");

  @TempDir
  Path data;

  private TestAdapt adapt;
  private EventSubscription subscription;

  @AfterEach
  void stop() {
    if (subscription != null) {
      subscription.unsubscribe();
    }
    if (adapt != null) {
      adapt.close();
    }
  }

  @Test
  void installedBeforeStartTheSkillIsInTheCatalogWithItsTextAndArt() {
    AddonHandle handle = AdaptApi.addons().install(new TinkerAddon());

    adapt = TestAdapt.start(TestPlatform.builder(data).build(), new BuiltinAddon());

    assertThat(handle.state()).isEqualTo(AddonState.ACTIVE);
    Skill<?> skill = AdaptApi.content().skill(TINKERING).orElseThrow();
    assertThat(skill).isInstanceOf(TinkerSkill.class);
    assertThat(skill.getAdaptations()).extracting(adaptation -> adaptation.getId()).containsExactly(GEARS, SPRINGS);
    assertThat(AdaptApi.content().adaptation(GEARS)).get().isInstanceOf(Gears.class);
    assertThat(AdaptApi.content().owner(TINKERING)).contains("tinker");
    assertThat(AdaptApi.content().owner(Id.adapt("swords"))).contains("adapt");
    assertThat(skill.getLocalizedName()).isEqualTo("Tinkering");
    assertThat(registry().getSkill(TINKERING)).isNotNull();
    assertThat(registry().getAdaptation(SPRINGS)).isNotNull();
    AssetCatalog assets = adapt.core().assets();
    assertThat(assets.sprite(Id.of("tinker", "gui/skill/tinkering"))).isPresent();
    assertThat(assets.themes()).contains(TINKERING);
  }

  @Test
  void installedAfterStartAppliesAtOnceAndRefreshRebuildsIt() {
    Recorder recorder = new Recorder();
    subscription = AdaptApi.events().subscribe(recorder);
    adapt = TestAdapt.start(TestPlatform.builder(data).build());
    long before = AdaptApi.content().revision();

    AddonHandle handle = AdaptApi.addons().install(new TinkerAddon());

    assertThat(handle.state()).isEqualTo(AddonState.ACTIVE);
    assertThat(AdaptApi.content().revision()).isGreaterThan(before);
    Skill<?> first = AdaptApi.content().skill(TINKERING).orElseThrow();
    assertThat(recorder.seen).contains("installed:tinker", "catalog");

    handle.refresh();

    assertThat(handle.state()).isEqualTo(AddonState.ACTIVE);
    assertThat(AdaptApi.content().skill(TINKERING)).get().isNotSameAs(first);
    assertThat(first.isRuntimeRegistered()).isFalse();
    assertThat(recorder.seen).containsSubsequence("installed:tinker", "uninstalled:tinker", "installed:tinker");
  }

  @Test
  void uninstallKeepsTheLearnersDataAndReinstallResumesIt() {
    TestPlatform platform = TestPlatform.builder(data).build();
    FakePlayer player = platform.player("Tinkerer", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform);
    AdaptPlayer profile = adapt.join(player);
    AddonHandle handle = AdaptApi.addons().install(new TinkerAddon());
    Adaptation<?> gears = registry().getAdaptation(GEARS);
    profile.getData().getSkillLine(TINKERING.toString()).setAdaptation(gears, 3);
    art.arcane.adapt.api.content.Adaptation<?> published = AdaptApi.content().adaptation(GEARS).orElseThrow();
    assertThat(published.getLevel(player)).isEqualTo(3);

    handle.uninstall();

    assertThat(AdaptApi.content().skill(TINKERING)).isEmpty();
    assertThat(AdaptApi.content().owner(GEARS)).isEmpty();
    assertThat(registry().getAnySkill(TINKERING)).isNull();
    assertThat(AdaptLanguage.texts().bundle("tinker")).isEmpty();
    assertThat(adapt.core().assets().themes()).doesNotContain(TINKERING);
    assertThat(profile.getData().getSkillLineNullable(TINKERING.toString()).getAdaptationLevel(GEARS.toString()))
        .isEqualTo(3);

    AdaptApi.addons().install(new TinkerAddon());

    assertThat(AdaptApi.content().adaptation(GEARS).orElseThrow().getLevel(player)).isEqualTo(3);
  }

  @Test
  void aVetoHidesThePublicAdaptationUntilLifted() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build());
    AdaptApi.addons().install(new TinkerAddon());
    AddonHandle warden = AdaptApi.addons().install(new Warden());

    ContentVeto veto = warden.veto(SPRINGS);

    assertThat(AdaptApi.content().skill(TINKERING).orElseThrow().getAdaptations())
        .extracting(adaptation -> adaptation.getId()).containsExactly(GEARS);
    veto.lift();
    assertThat(AdaptApi.content().skill(TINKERING).orElseThrow().getAdaptations())
        .extracting(adaptation -> adaptation.getId()).containsExactly(GEARS, SPRINGS);
  }

  @Test
  void aPublicAdaptationOnAnotherAddonsSkillLandsThereAndLeavesWithItsAddon() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build());
    AdaptApi.addons().install(new TinkerAddon());

    AddonHandle extra = AdaptApi.addons().install(new Extender());

    assertThat(extra.state()).isEqualTo(AddonState.ACTIVE);
    assertThat(AdaptApi.content().skill(TINKERING).orElseThrow().getAdaptations())
        .extracting(adaptation -> adaptation.getId()).containsExactly(GEARS, SPRINGS, Extender.LEVERS);
    assertThat(AdaptApi.content().owner(Extender.LEVERS)).contains("levers");

    extra.uninstall();

    assertThat(AdaptApi.content().skill(TINKERING).orElseThrow().getAdaptations())
        .extracting(adaptation -> adaptation.getId()).containsExactly(GEARS, SPRINGS);
  }

  @Test
  void aPublicSubscriberCancelsALearnAndDoublesAnXpAward() {
    TestPlatform platform = TestPlatform.builder(data).build();
    FakePlayer player = platform.player("Tinkerer", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform);
    adapt.join(player);
    AdaptApi.addons().install(new TinkerAddon());
    subscription = AdaptApi.events().subscribe(new Interceptor());
    PlayerProfile profile = AdaptApi.players().require(player);

    LearnResult refused = profile.learn(GEARS, 1, LearnOptions.forced().quietly());
    LearnResult learned = profile.learn(SPRINGS, 1, LearnOptions.forced().quietly());
    profile.skill(TINKERING).award(XpAward.of(40D));

    assertThat(refused.succeeded()).isFalse();
    assertThat(profile.level(GEARS)).isZero();
    assertThat(learned.succeeded()).isTrue();
    assertThat(profile.level(SPRINGS)).isEqualTo(1);
    assertThat(profile.skill(TINKERING).xp()).isEqualTo(80D);
  }

  public static final class Interceptor {
    @Handle
    public void on(AdaptationLearnEvent event) {
      if (event.adaptation().equals(GEARS)) {
        event.setCancelled(true);
      }
    }

    @Handle
    public void on(SkillXpEvent event) {
      if (event.skill().equals(TINKERING)) {
        event.setAmount(event.amount() * 2D);
      }
    }
  }

  private SkillRegistry registry() {
    return adapt.core().getAdaptServer().getSkillRegistry();
  }

  public static final class Recorder {
    private final List<String> seen = new CopyOnWriteArrayList<>();

    @Handle
    public void on(AddonInstalledEvent event) {
      seen.add("installed:" + event.namespace());
    }

    @Handle
    public void on(AddonUninstalledEvent event) {
      seen.add("uninstalled:" + event.namespace());
    }

    @Handle
    public void on(CatalogChangedEvent event) {
      seen.add("catalog");
    }
  }

  static final class TinkerAddon implements AdaptAddon {
    @Override
    public String namespace() {
      return "tinker";
    }

    @Override
    public void register(ContentRegistrar registrar) {
      registrar.messages(MessageBundle.resources(PublicAddonTest.class, "tinker/lang"));
      registrar.assets(AssetBundle.resources(PublicAddonTest.class, "assets/tinker"));
      registrar.skill(TINKERING, TinkerSkill::new);
    }
  }

  static final class Warden implements AdaptAddon {
    @Override
    public String namespace() {
      return "warden";
    }

    @Override
    public void register(ContentRegistrar registrar) {
    }
  }

  static final class Extender implements AdaptAddon {
    static final Id LEVERS = Id.of("levers", "tinkering/levers");

    @Override
    public String namespace() {
      return "levers";
    }

    @Override
    public void register(ContentRegistrar registrar) {
      registrar.adaptation(TINKERING, LEVERS, () -> new Plain(LEVERS));
    }
  }

  public static final class TinkerSkill extends SimpleSkill<TinkerSkill.Config> {
    public TinkerSkill() {
      super(TINKERING);
      registerConfiguration(Config.class);
      setColor(Rgb.of(0x7A4FD1));
      setIcon(Id.minecraft("piston"));
      registerAdaptation(GEARS, Gears::new);
      registerAdaptation(SPRINGS, () -> new Plain(SPRINGS));
    }

    public static final class Config extends SkillConfig {
    }
  }

  public static final class Gears extends SimpleAdaptation<AdaptationConfig> {
    public Gears() {
      super(GEARS);
      registerConfiguration(AdaptationConfig.class);
    }

    @Override
    public void addStats(int level, StatLines lines) {
    }
  }

  public static final class Plain extends SimpleAdaptation<AdaptationConfig> {
    public Plain(Id id) {
      super(id);
      registerConfiguration(AdaptationConfig.class);
    }

    @Override
    public void addStats(int level, StatLines lines) {
    }
  }
}
