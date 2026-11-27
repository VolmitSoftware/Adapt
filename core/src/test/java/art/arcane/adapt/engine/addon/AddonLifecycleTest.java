package art.arcane.adapt.engine.addon;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.ability.AbilityContext;
import art.arcane.adapt.api.ability.AbilityUseDecision;
import art.arcane.adapt.api.ability.AbilityUsePolicy;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.engine.bridge.HostKeys;
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
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.event.AdaptStartedEvent;
import art.arcane.adapt.api.event.AdaptStoppingEvent;
import art.arcane.adapt.api.event.AddonInstalledEvent;
import art.arcane.adapt.api.event.AddonUninstalledEvent;
import art.arcane.adapt.api.event.CatalogChangedEvent;
import art.arcane.adapt.api.event.EventSubscription;
import art.arcane.adapt.api.text.MessageBundle;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.engine.asset.AssetCatalog;
import art.arcane.adapt.engine.asset.AssetSubscription;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Installing, refreshing, uninstalling and vetoing addons on a running core, with engine-typed content. */
class AddonLifecycleTest {
  private static final Id GIZMOS = Id.of("gizmo", "gizmos");
  private static final Id COG = Id.of("gizmo", "gizmos/cog");
  private static final Id RIVALRY = Id.of("rival", "rivalry");

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
  void anAddonInstalledBeforeStartIsAppliedWithItsTextAndArt() {
    Recorder recorder = new Recorder();
    subscription = AdaptApi.events().subscribe(recorder);
    AddonHandle handle = AdaptApi.addons().install(new GizmoAddon(true));
    assertThat(handle.state()).isEqualTo(AddonState.WAITING);

    adapt = TestAdapt.start(TestPlatform.builder(data).build());

    assertThat(handle.state()).isEqualTo(AddonState.ACTIVE);
    assertThat(registry().getSkill(GIZMOS)).isInstanceOf(Gizmos.class);
    assertThat(registry().getAdaptation(COG)).isInstanceOf(Cog.class);
    assertThat(AdaptApi.content().owner(GIZMOS)).contains("gizmo");
    assertThat(AdaptApi.content().owner(COG)).contains("gizmo");
    assertThat(AdaptLanguage.texts().bundle("gizmo")).isPresent();
    assertThat(adapt.core().assets().bundles()).anyMatch(bundle -> bundle.namespace().equals("gizmo"));
    assertThat(recorder.seen).containsSubsequence("installed:gizmo", "catalog", "started");
  }

  @Test
  void anAddonInstalledWhileRunningAppliesAtOnceAndUninstallTakesEverythingBack() {
    Recorder recorder = new Recorder();
    subscription = AdaptApi.events().subscribe(recorder);
    TestPlatform platform = TestPlatform.builder(data).build();
    FakePlayer player = platform.player("Tinkerer", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform);
    AdaptPlayer profile = adapt.join(player);

    AddonHandle handle = AdaptApi.addons().install(new GizmoAddon(true));

    assertThat(handle.state()).isEqualTo(AddonState.ACTIVE);
    Adaptation<?> cog = registry().getAdaptation(COG);
    profile.getData().getSkillLine(GIZMOS.toString()).setAdaptation(cog, 2);
    assertThat(cog.getLevel(player)).isEqualTo(2);
    long installed = AdaptApi.content().revision();

    handle.uninstall();

    assertThat(handle.state()).isEqualTo(AddonState.UNINSTALLED);
    assertThat(registry().getAnySkill(GIZMOS)).isNull();
    assertThat(registry().getAdaptation(COG)).isNull();
    assertThat(((SimpleAdaptation<?>) cog).isRuntimeRegistered()).isFalse();
    assertThat(AdaptApi.content().revision()).isGreaterThan(installed);
    assertThat(AdaptLanguage.texts().bundle("gizmo")).isEmpty();
    assertThat(adapt.core().assets().bundles()).noneMatch(bundle -> bundle.namespace().equals("gizmo"));
    assertThat(profile.getData().getSkillLineNullable(GIZMOS.toString())).isNotNull();
    assertThat(recorder.seen).contains("uninstalled:gizmo");

    AdaptApi.addons().install(new GizmoAddon(true));

    Adaptation<?> returned = registry().getAdaptation(COG);
    assertThat(returned).isNotSameAs(cog);
    assertThat(returned.getLevel(player)).isEqualTo(2);
  }

  @Test
  void aSkillReadsItsAddonsTextWhileItRegisters() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build());

    AdaptApi.addons().install(new SelfNamingAddon());

    assertThat(SelfNaming.named).isEqualTo("Tinkering");
  }

  @Test
  void refreshRebuildsTheAddonsContent() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build());
    AddonHandle handle = AdaptApi.addons().install(new GizmoAddon(false));
    Object first = registry().getSkill(GIZMOS);

    handle.refresh();

    assertThat(handle.state()).isEqualTo(AddonState.ACTIVE);
    assertThat(registry().getSkill(GIZMOS)).isNotNull().isNotSameAs(first);
  }

  @Test
  void refreshKeepsHooksTheAddonRegisteredOutsideRegisterAndUninstallDropsThem() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build());
    AddonHandle handle = AdaptApi.addons().install(new GizmoAddon(false));
    AbilityUsePolicy policy = (AbilityContext context) -> AbilityUseDecision.allow();
    AdaptApi.abilities().registerPolicy("gizmo", policy);

    handle.refresh();

    assertThat(Engine.abilities().policies(HostKeys.key())).contains(policy);
    handle.uninstall();
    assertThat(Engine.abilities().policies(HostKeys.key())).doesNotContain(policy);
  }

  @Test
  void aFailingAddonLeavesNothingBehindAndDoesNotStopAnother() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build());
    AddonHandle broken = AdaptApi.addons().install(new BrokenAddon());
    AddonHandle good = AdaptApi.addons().install(new GizmoAddon(false));

    assertThat(broken.state()).isEqualTo(AddonState.REJECTED);
    assertThat(broken.rejection().orElseThrow()).contains("Addon broken");
    assertThat(registry().getAnySkill(Id.of("broken", "first"))).isNull();
    assertThat(AdaptLanguage.texts().bundle("broken")).isEmpty();
    assertThat(adapt.core().assets().bundles()).noneMatch(bundle -> bundle.namespace().equals("broken"));
    assertThat(good.state()).isEqualTo(AddonState.ACTIVE);
    assertThat(registry().getSkill(GIZMOS)).isNotNull();
  }

  @Test
  void anIdCollisionIsRejectedNamingBothOwners() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build());
    AdaptApi.addons().install(new GizmoAddon(false));

    AddonHandle rival = AdaptApi.addons().install(new RivalAddon());

    assertThat(rival.state()).isEqualTo(AddonState.REJECTED);
    assertThat(rival.rejection().orElseThrow())
        .contains(COG.toString()).contains("addon gizmo").contains("addon rival");
    assertThat(registry().getAnySkill(RIVALRY)).isNull();
    assertThat(registry().getAdaptation(COG).getSkill()).isInstanceOf(Gizmos.class);
  }

  @Test
  void aVetoDisablesContentUntilLiftedOrItsAddonLeaves() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build());
    AdaptApi.addons().install(new GizmoAddon(false));
    AddonHandle warden = AdaptApi.addons().install(new EmptyAddon("warden"));

    ContentVeto cogVeto = warden.veto(COG);

    assertThat(registry().getAdaptation(COG)).isNull();
    assertThat(registry().getSkill(GIZMOS)).isNotNull();
    assertThat(AdaptApi.content().vetoes()).containsExactly(cogVeto);
    cogVeto.lift();
    assertThat(cogVeto.isActive()).isFalse();
    assertThat(registry().getAdaptation(COG)).isNotNull();

    ContentVeto skillVeto = warden.veto(GIZMOS);
    assertThat(registry().getSkill(GIZMOS)).isNull();
    assertThat(registry().getAnySkill(GIZMOS)).isNotNull();

    warden.uninstall();

    assertThat(skillVeto.isActive()).isFalse();
    assertThat(AdaptApi.content().vetoes()).isEmpty();
    assertThat(registry().getSkill(GIZMOS)).isNotNull();
    assertThatThrownBy(() -> warden.veto(GIZMOS)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Engine.addons().handle(HostKeys.key(), "gizmo").orElseThrow().veto(Id.of("gizmo", "nothing")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aVetoIsRefusedWhenTheConfigProtectsTheNamespace() throws Exception {
    adapt = TestAdapt.start(TestPlatform.builder(data).build());
    AdaptApi.addons().install(new GizmoAddon(false));
    AddonHandle warden = AdaptApi.addons().install(new EmptyAddon("warden"));
    AdaptConfig previous = AdaptConfig.get();
    AdaptConfig protecting = new AdaptConfig();
    Field namespaces = AdaptConfig.Addons.class.getDeclaredField("vetoProtectedNamespaces");
    namespaces.setAccessible(true);
    namespaces.set(protecting.getAddons(), List.of("gizmo"));
    Field current = AdaptConfig.class.getDeclaredField("config");
    current.setAccessible(true);
    current.set(null, protecting);
    try {
      assertThatThrownBy(() -> warden.veto(GIZMOS))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("gizmo");
      assertThat(registry().getSkill(GIZMOS)).isNotNull();
      assertThat(AdaptApi.content().vetoes()).isEmpty();
    } finally {
      current.set(null, previous);
    }
  }

  @Test
  void aSkillRegisteredAfterStartWithoutArtRaisesTheAssetRevision() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build());
    AssetCatalog assets = adapt.core().assets();
    AtomicInteger changes = new AtomicInteger();
    AssetSubscription listening = assets.subscribe(catalog -> changes.incrementAndGet());
    try {
      long before = assets.revision();

      AddonHandle handle = AdaptApi.addons().install(new GizmoAddon(false));

      assertThat(assets.revision()).isGreaterThan(before);
      assertThat(changes.get()).isPositive();
      assertThat(assets.themes()).contains(GIZMOS);
      assertThat(assets.theme(GIZMOS).orElseThrow().generated()).isTrue();
      long installed = assets.revision();
      int heard = changes.get();

      handle.uninstall();

      assertThat(assets.revision()).isGreaterThan(installed);
      assertThat(changes.get()).isGreaterThan(heard);
      assertThat(assets.themes()).doesNotContain(GIZMOS);
    } finally {
      listening.cancel();
    }
  }

  @Test
  void subscribedListenersHearTheLifecycleAcrossRestarts() {
    Recorder recorder = new Recorder();
    subscription = AdaptApi.events().subscribe(recorder);
    AdaptApi.addons().install(new GizmoAddon(false));
    adapt = TestAdapt.start(TestPlatform.builder(data).build());
    adapt.close();
    adapt = null;

    assertThat(recorder.seen).containsSubsequence("installed:gizmo", "started", "stopping");
    recorder.seen.clear();
    AdaptApi.addons().install(new GizmoAddon(false));
    adapt = TestAdapt.start(TestPlatform.builder(data).build());

    assertThat(recorder.seen).containsSubsequence("installed:gizmo", "started");
  }

  private SkillRegistry registry() {
    return adapt.core().getAdaptServer().getSkillRegistry();
  }


  public static final class Recorder {
    private final List<String> seen = new CopyOnWriteArrayList<>();

    @Handle
    public void on(AdaptStartedEvent event) {
      seen.add("started");
    }

    @Handle
    public void on(AdaptStoppingEvent event) {
      seen.add("stopping");
    }

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

  record GizmoAddon(boolean withTextAndArt) implements AdaptAddon {
    @Override
    public String namespace() {
      return "gizmo";
    }

    @Override
    public void register(ContentRegistrar registrar) {
      registrar.skill(GIZMOS, Gizmos::new);
      if (withTextAndArt) {
        registrar.messages(MessageBundle.resources(AddonLifecycleTest.class, "tinker/lang"));
        registrar.assets(AssetBundle.resources(AddonLifecycleTest.class, "assets/gizmo"));
      }
    }
  }

  private static final class SelfNamingAddon implements AdaptAddon {
    @Override
    public String namespace() {
      return "gizmo";
    }

    @Override
    public void register(ContentRegistrar registrar) {
      registrar.skill(GIZMOS, SelfNaming::new);
      registrar.messages(MessageBundle.resources(AddonLifecycleTest.class, "tinker/lang"));
    }
  }

  public static final class SelfNaming extends SimpleSkill<SkillConfig> {
    static volatile String named;

    public SelfNaming() {
      super(GIZMOS);
      registerConfiguration(SkillConfig.class);
      named = Text.key("gizmo.skill.tinkering.name").resolve();
    }

    @Override
    public boolean isEnabled() {
      return true;
    }
  }

  private record EmptyAddon(String namespace) implements AdaptAddon {
    @Override
    public void register(ContentRegistrar registrar) {
    }
  }

  private static final class BrokenAddon implements AdaptAddon {
    @Override
    public String namespace() {
      return "broken";
    }

    @Override
    public void register(ContentRegistrar registrar) {
      registrar.messages(MessageBundle.resources(AddonLifecycleTest.class, "tinker/lang"));
      registrar.skill(Id.of("broken", "first"), Gizmos::new);
      registrar.skill(Id.of("broken", "second"), () -> {
        throw new IllegalStateException("breaks on purpose");
      });
    }
  }

  private static final class RivalAddon implements AdaptAddon {
    @Override
    public String namespace() {
      return "rival";
    }

    @Override
    public void register(ContentRegistrar registrar) {
      registrar.skill(RIVALRY, Rivalry::new);
    }
  }

  public static final class Gizmos extends SimpleSkill<SkillConfig> {
    public Gizmos() {
      super(GIZMOS);
      registerConfiguration(SkillConfig.class);
      registerAdaptation(COG, Cog::new);
    }

    @Override
    public boolean isEnabled() {
      return true;
    }
  }

  public static final class Rivalry extends SimpleSkill<SkillConfig> {
    public Rivalry() {
      super(RIVALRY);
      registerConfiguration(SkillConfig.class);
      registerAdaptation(COG, Cog::new);
    }

    @Override
    public boolean isEnabled() {
      return true;
    }
  }

  public static final class Cog extends SimpleAdaptation<AdaptationConfig> {
    public Cog() {
      super(COG);
      registerConfiguration(AdaptationConfig.class);
      setLocalizationKey("pickaxe.chisel");
    }

    @Override
    public void addStats(int level, StatLines lines) {
    }
  }
}
