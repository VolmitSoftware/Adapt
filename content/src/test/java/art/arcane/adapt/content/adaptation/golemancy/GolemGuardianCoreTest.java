package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.item.ShapedRecipeParser;
import art.arcane.adapt.content.skill.SkillGolemancy;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.testkit.PlacedAdaptations;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.TestClocks;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.content.golem.GolemBody;
import art.arcane.adapt.content.golem.GolemBrain;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemRegistry;
import art.arcane.adapt.content.golem.GolemState;
import art.arcane.adapt.content.golem.GolemVitals;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemGuardianCoreTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

  private final AtomicLong clock = new AtomicLong(100_000L);
  private final List<GolemHandle> loaded = new ArrayList<>();
  private MockedStatic<ShapedRecipeParser> parsers;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<AdaptApi> scheduling;
  private final AdaptScheduler adaptScheduler = mock(AdaptScheduler.class);
  private MockedStatic<GolemVitals> vitals;
  private GolemGuardianCore adaptation;
  private WorldRef world;
  private PlayerRef owner;

  @BeforeEach
  void construct() {
    ShapedRecipeParser parser = mock(ShapedRecipeParser.class);
    when(parser.recipe(any(), any())).thenReturn(Optional.empty());
    parsers = mockStatic(ShapedRecipeParser.class);
    parsers.when(() -> ShapedRecipeParser.platform(any())).thenReturn(parser);
    runtime = mockStatic(GolemancyRuntime.class);
    GolemRegistry registry = mock(GolemRegistry.class);
    lenient().when(registry.loadedOf(OWNER)).thenAnswer(invocation -> List.copyOf(loaded));
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
    runtime.when(() -> GolemancyRuntime.handle(any())).thenReturn(Optional.empty());
    scheduling = mockStatic(AdaptApi.class, CALLS_REAL_METHODS);
    scheduling.when(AdaptApi::scheduler).thenReturn(adaptScheduler);
    when(adaptScheduler.owns(any(EntityRef.class))).thenReturn(true);
    when(core.clock()).thenReturn(TestClocks.reading(clock::get));
    vitals = mockStatic(GolemVitals.class);
    vitals.when(() -> GolemVitals.maxHealth(any())).thenReturn(100D);
    adaptation = PlacedAdaptations.on(SkillGolemancy.ID, GolemGuardianCore::new);
    world = mock(WorldRef.class);
    owner = living(PlayerRef.class, at(0, 64, 0));
    lenient().when(owner.uuid()).thenReturn(OWNER);
  }

  @AfterEach
  void close() {
    vitals.close();
    scheduling.close();
    runtime.close();
    parsers.close();
  }

  @Test
  void defaultsMatchTheBrief() {
    GolemGuardianCore.Config config = new GolemGuardianCore.Config();

    assertThat(config.perceptionByLevel).containsExactly(8, 12, 16, 20, 24);
    assertThat(config.targetCreepers).isFalse();
    assertThat(config.ignoreNamedMobs).isTrue();
    assertThat(config.interceptLevel).isEqualTo(2);
    assertThat(config.interceptIntervalMillis).isEqualTo(3_000L);
    assertThat(config.retaliateLevel).isEqualTo(3);
    assertThat(config.retaliateWindowMillis).isEqualTo(5_000L);
    assertThat(config.bulwarkLevel).isEqualTo(4);
    assertThat(config.bulwarkRadius).isEqualTo(4D);
    assertThat(config.bulwarkDamageReduction).isEqualTo(0.15D);
    assertThat(config.lastStandLevel).isEqualTo(5);
    assertThat(config.lastStandHealthFraction).isEqualTo(0.2D);
    assertThat(config.lastStandTicks).isEqualTo(100);
    assertThat(config.lastStandAmplifier).isEqualTo(1);
    assertThat(config.lastStandCooldownMillis).isEqualTo(60_000L);
    assertThat(config.sentryRange).isEqualTo(10D);
    assertThat(config.requires).containsExactly("adapt:golem/animus_binding@1");
    assertThat(config.maxLevel).isEqualTo(5);
  }

  @Test
  void interceptAndPlayerRetaliationControlsAppearOnceTheirLevelsUnlock() {
    assertThat(adaptation.getPlayerPreferences())
        .containsExactly(CommonPreferences.ENABLED, GolemancyPreferences.INTERCEPT, GolemancyPreferences.RETALIATE_PLAYERS);
    GolemancyPreferenceAssertions.assertVisibleFrom(adaptation, GolemancyPreferences.INTERCEPT, 2);
    GolemancyPreferenceAssertions.assertVisibleFrom(adaptation, GolemancyPreferences.RETALIATE_PLAYERS, 3);
    GolemancyPreferenceAssertions.assertVisibleFrom(adaptation, CommonPreferences.ENABLED, 0);
  }

  @Test
  void interceptAndPlayerRetaliationDefaultOnAndFollowTheServerDefault() {
    when(core.getAdaptServer()).thenReturn(mock(AdaptServer.class));

    assertThat(adaptation.intercepts(OWNER)).isTrue();
    assertThat(adaptation.retaliatesAgainstPlayers(OWNER)).isTrue();

    GolemancyPreferenceAssertions.serverDefault(adaptation, GolemancyPreferences.INTERCEPT, "OFF");
    assertThat(adaptation.intercepts(OWNER)).isFalse();
    assertThat(adaptation.retaliatesAgainstPlayers(OWNER)).isTrue();

    GolemancyPreferenceAssertions.serverDefault(adaptation, GolemancyPreferences.RETALIATE_PLAYERS, "OFF");
    assertThat(adaptation.retaliatesAgainstPlayers(OWNER)).isFalse();
  }

  @Test
  void bulwarkPresenceCutsTheOwnersDamageNearAnyGuardianWithoutStacking() {
    guardian(at(3, 64, 0), false, GolemCoreType.GUARDIAN);
    guardian(at(0, 64, 2), false, GolemCoreType.GUARDIAN);
    EntityDamageEvent hit = damage(owner, DamageCause.ENTITY_ATTACK, 10D);

    owner(4).shieldOwner(hit);

    verify(hit).setDamage(8.5D);
    verify(hit, times(1)).setDamage(anyDouble());
  }

  @Test
  void bulwarkPresenceNeedsItsLevelAnAwakeGuardianWithinReachAndAShieldableCause() {
    guardian(at(5, 64, 0), false, GolemCoreType.GUARDIAN);
    guardian(at(1, 64, 0), true, GolemCoreType.GUARDIAN);
    GolemHandle gatherer = guardian(at(0, 64, 1), false, GolemCoreType.GATHERER);
    EntityDamageEvent tooFar = damage(owner, DamageCause.ENTITY_ATTACK, 10D);
    owner(4).shieldOwner(tooFar);

    guardian(at(2, 64, 0), false, GolemCoreType.GUARDIAN);
    EntityDamageEvent lowLevel = damage(owner, DamageCause.ENTITY_ATTACK, 10D);
    owner(3).shieldOwner(lowLevel);
    EntityDamageEvent voidDamage = damage(owner, DamageCause.VOID, 10D);
    owner(5).shieldOwner(voidDamage);

    verify(tooFar, never()).setDamage(anyDouble());
    verify(lowLevel, never()).setDamage(anyDouble());
    verify(voidDamage, never()).setDamage(anyDouble());
    verify(gatherer.entity(), never()).location();
  }

  @Test
  void lastStandGivesAGuardianThatDropsBelowItsThresholdResistanceOncePerCooldown() {
    MobRef golem = living(MobRef.class, at(2, 64, 0));
    bound(golem, GolemCoreType.GUARDIAN);
    when(golem.health()).thenReturn(30D);
    GolemGuardianCore learned = golemOwner(5);

    learned.guardianHurt(golemDamage(golem, 12D));
    learned.guardianHurt(golemDamage(golem, 12D));
    clock.addAndGet(59_999L);
    learned.guardianHurt(golemDamage(golem, 12D));
    ArgumentCaptor<EffectInstance> braced = ArgumentCaptor.forClass(EffectInstance.class);
    verify(golem, times(1)).addEffect(braced.capture());
    assertThat(braced.getValue().type()).isEqualTo(Id.minecraft("resistance"));
    assertThat(braced.getValue().durationTicks()).isEqualTo(100);
    assertThat(braced.getValue().amplifier()).isEqualTo(1);

    clock.addAndGet(1L);
    learned.guardianHurt(golemDamage(golem, 12D));
    verify(golem, times(2)).addEffect(any(EffectInstance.class));
  }

  @Test
  void lastStandNeedsItsLevelAGuardianCoreAndAHitTheGolemSurvives() {
    MobRef guardian = living(MobRef.class, at(2, 64, 0));
    bound(guardian, GolemCoreType.GUARDIAN);
    when(guardian.health()).thenReturn(30D);
    MobRef gatherer = living(MobRef.class, at(4, 64, 0));
    bound(gatherer, GolemCoreType.GATHERER);
    when(gatherer.health()).thenReturn(30D);

    golemOwner(4).guardianHurt(golemDamage(guardian, 12D));
    GolemGuardianCore learned = golemOwner(5);
    learned.guardianHurt(golemDamage(gatherer, 12D));
    learned.guardianHurt(golemDamage(guardian, 5D));
    learned.guardianHurt(golemDamage(guardian, 31D));

    verify(guardian, never()).addEffect(any(EffectInstance.class));
    verify(gatherer, never()).addEffect(any(EffectInstance.class));
  }

  @Test
  void threatsToTheOwnerOrAGuardianWakeTheOwnersGuardiansIdleScans() {
    GolemHandle near = guardian(at(3, 64, 0), false, GolemCoreType.GUARDIAN);
    GolemHandle sleeping = guardian(at(4, 64, 0), true, GolemCoreType.GUARDIAN);
    GolemHandle gatherer = guardian(at(5, 64, 0), false, GolemCoreType.GATHERER);
    GolemBrain nearBrain = brain(near);
    GolemBrain sleepingBrain = brain(sleeping);
    GolemBrain gathererBrain = brain(gatherer);
    MobRef zombie = living(MobRef.class, at(8, 64, 0));

    MobRef nearBody = (MobRef) near.entity();
    runtime.when(() -> GolemancyRuntime.handle(nearBody)).thenReturn(Optional.of(near));

    owner(1).on(hit(zombie, owner));
    adaptation.on(new EntityTargetEvent(zombie, owner, EntityTargetEvent.Reason.CLOSEST_PLAYER));
    adaptation.on(new EntityTargetEvent(zombie, nearBody, EntityTargetEvent.Reason.CLOSEST_ENTITY));
    golemOwner(1).guardianHurt(golemDamage(nearBody, 1D));

    verify(nearBrain, times(4)).alert();
    verify(sleepingBrain, never()).alert();
    verify(gathererBrain, never()).alert();
  }

  @Test
  void theAttackerIsRememberedBeforeTheGuardiansAreWoken() {
    GolemBrain brain = brain(guardian(at(3, 64, 0), false, GolemCoreType.GUARDIAN));
    GolemGuardianCore learned = owner(3);
    MobRef zombie = living(MobRef.class, at(2, 64, 0));
    AtomicReference<LivingRef> rememberedAtAlert = new AtomicReference<>();
    doAnswer(call -> {
      rememberedAtAlert.set(learned.recentAttacker(OWNER, clock.get()));
      return null;
    }).when(brain).alert();

    learned.on(hit(zombie, owner));

    verify(brain).alert();
    assertThat(rememberedAtAlert.get()).isSameAs(zombie);
  }

  @Test
  void aNegativeRetaliationWindowRemembersNoAttacker() {
    GolemGuardianCore learned = owner(3);
    GolemGuardianCore.Config config = new GolemGuardianCore.Config();
    config.retaliateWindowMillis = -1L;
    doReturn(config).when(learned).getConfig();
    MobRef zombie = living(MobRef.class, at(2, 64, 0));

    learned.on(hit(zombie, owner));

    assertThat(learned.recentAttacker(OWNER, clock.get())).isNull();
  }

  @Test
  void whateverHurtsTheOwnerIsRememberedForTheRetaliationWindow() {
    GolemGuardianCore learned = owner(3);
    MobRef zombie = living(MobRef.class, at(2, 64, 0));
    MobRef archer = living(MobRef.class, at(9, 64, 0));
    ProjectileRef arrow = mock(ProjectileRef.class);
    when(arrow.shooter()).thenReturn(archer);

    learned.on(hit(zombie, owner));
    assertThat(learned.recentAttacker(OWNER, clock.get())).isSameAs(zombie);

    learned.on(hit(arrow, owner));
    assertThat(learned.recentAttacker(OWNER, clock.get())).isSameAs(archer);
    assertThat(learned.recentAttacker(OWNER, clock.get() + 4_999L)).isSameAs(archer);
    assertThat(learned.recentAttacker(OWNER, clock.get() + 5_000L)).isNull();
  }

  @Test
  void attacksAreRememberedOnlyOnceRetaliationIsUnlockedAndNeverFromTheOwner() {
    MobRef zombie = living(MobRef.class, at(2, 64, 0));
    ProjectileRef ownArrow = mock(ProjectileRef.class);
    when(ownArrow.shooter()).thenReturn(owner);

    owner(2).on(hit(zombie, owner));
    GolemGuardianCore learned = owner(3);
    learned.on(hit(ownArrow, owner));

    assertThat(adaptation.recentAttacker(OWNER, clock.get())).isNull();
    assertThat(learned.recentAttacker(OWNER, clock.get())).isNull();
  }

  @Test
  void theTickSweepsExpiredAttacksAndCooldowns() {
    GolemGuardianCore learned = owner(3);
    learned.on(hit(living(MobRef.class, at(2, 64, 0)), owner));
    MobRef golem = living(MobRef.class, at(2, 64, 0));
    bound(golem, GolemCoreType.GUARDIAN);
    when(golem.health()).thenReturn(30D);
    doReturn(5).when(learned).ownerLevel(any(GolemHandle.class));
    learned.guardianHurt(golemDamage(golem, 12D));
    assertThat(learned.hasTickDemand()).isTrue();

    clock.addAndGet(5_000L);
    learned.onTick();
    assertThat(learned.hasTickDemand()).isTrue();

    clock.addAndGet(55_000L);
    learned.onTick();
    assertThat(learned.hasTickDemand()).isFalse();
  }

  private GolemGuardianCore owner(int level) {
    GolemGuardianCore learned = spy(adaptation);
    doReturn(level).when(learned).getActiveLevel(owner);
    return learned;
  }

  private GolemGuardianCore golemOwner(int level) {
    GolemGuardianCore learned = spy(adaptation);
    doReturn(level).when(learned).ownerLevel(any(GolemHandle.class));
    return learned;
  }

  private GolemBrain brain(GolemHandle handle) {
    GolemBrain brain = mock(GolemBrain.class);
    MobRef body = handle.entity();
    runtime.when(() -> GolemancyRuntime.brain(body)).thenReturn(Optional.of(brain));
    return brain;
  }

  private GolemHandle guardian(Loc location, boolean dormant, GolemCoreType core) {
    MobRef golem = living(MobRef.class, location);
    GolemHandle handle = handle(golem, core);
    lenient().when(handle.isDormant()).thenReturn(dormant);
    loaded.add(handle);
    return handle;
  }

  private void bound(MobRef golem, GolemCoreType core) {
    GolemHandle handle = handle(golem, core);
    runtime.when(() -> GolemancyRuntime.handle(golem)).thenReturn(Optional.of(handle));
  }

  private GolemHandle handle(MobRef golem, GolemCoreType core) {
    GolemState state = mock(GolemState.class);
    lenient().when(state.cores()).thenReturn(List.of(core));
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.id()).thenReturn(UUID.randomUUID());
    lenient().when(handle.owner()).thenReturn(OWNER);
    lenient().when(handle.body()).thenReturn(GolemBody.IRON);
    lenient().when(handle.core()).thenReturn(core);
    lenient().when(handle.entity()).thenReturn(golem);
    lenient().when(handle.state()).thenReturn(state);
    return handle;
  }

  private <T extends LivingRef> T living(Class<T> type, Loc location) {
    T entity = mock(type);
    lenient().when(entity.uuid()).thenReturn(UUID.randomUUID());
    lenient().when(entity.location()).thenAnswer(invocation -> location);
    lenient().when(entity.world()).thenReturn(world);
    lenient().when(entity.isValid()).thenReturn(true);
    return entity;
  }

  private static EntityDamageEvent damage(EntityRef victim, DamageCause cause, double amount) {
    EntityDamageEvent event = mock(EntityDamageEvent.class);
    lenient().when(event.entity()).thenReturn(victim);
    lenient().when(event.cause()).thenReturn(cause);
    lenient().when(event.damage()).thenReturn(amount);
    lenient().when(event.finalDamage()).thenReturn(amount);
    return event;
  }

  private static EntityDamageEvent golemDamage(MobRef golem, double amount) {
    return damage(golem, DamageCause.ENTITY_ATTACK, amount);
  }

  private static EntityDamageByEntityEvent hit(EntityRef damager, PlayerRef victim) {
    EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
    lenient().when(event.damager()).thenReturn(damager);
    lenient().when(event.entity()).thenReturn(victim);
    return event;
  }

  private Loc at(double x, double y, double z) {
    return new Loc(world, x + 0.5D, y, z + 0.5D);
  }
}
