package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemBrain;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemVitals;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.math.M;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
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
  private MockedStatic<ConfiguredRecipeParser> parsers;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<J> scheduling;
  private MockedStatic<M> time;
  private MockedStatic<GolemVitals> vitals;
  private GolemGuardianCore adaptation;
  private World world;
  private Player owner;

  @BeforeAll
  static void initializeEffects() {
    RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
    try (MockedStatic<RegistryAccess> registryAccess = mockStatic(RegistryAccess.class)) {
      registryAccess.when(RegistryAccess::registryAccess).thenReturn(access);
      Registry<PotionEffectType> effects = Registry.MOB_EFFECT;
      doAnswer(call -> mock(PotionEffectType.class)).when(effects).getOrThrow(any(Key.class));
      assertThat(PotionEffectType.RESISTANCE).isNotNull();
    }
  }

  @BeforeEach
  void construct() {
    when(plugin.namespace()).thenReturn("adapt");
    ConfiguredRecipeParser parser = mock(ConfiguredRecipeParser.class);
    when(parser.builder(any(), any())).thenReturn(Optional.empty());
    parsers = mockStatic(ConfiguredRecipeParser.class);
    parsers.when(() -> ConfiguredRecipeParser.bukkit(any(), any())).thenReturn(parser);
    runtime = mockStatic(GolemancyRuntime.class);
    GolemRegistry registry = mock(GolemRegistry.class);
    lenient().when(registry.loadedOf(OWNER)).thenAnswer(invocation -> List.copyOf(loaded));
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
    runtime.when(() -> GolemancyRuntime.handle(any())).thenReturn(Optional.empty());
    scheduling = mockStatic(J.class);
    scheduling.when(() -> J.isOwnedByCurrentRegion(any(Entity.class))).thenReturn(true);
    time = mockStatic(M.class);
    time.when(M::ms).thenAnswer(invocation -> clock.get());
    vitals = mockStatic(GolemVitals.class);
    vitals.when(() -> GolemVitals.maxHealth(any())).thenReturn(100D);
    adaptation = new GolemGuardianCore();
    world = mock(World.class);
    owner = living(Player.class, at(0, 64, 0));
    lenient().when(owner.getUniqueId()).thenReturn(OWNER);
  }

  @AfterEach
  void close() {
    vitals.close();
    time.close();
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
    assertThat(config.requires).containsExactly("golem-animus-binding:1");
    assertThat(config.maxLevel).isEqualTo(5);
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
    verify(gatherer.entity(), never()).getLocation();
  }

  @Test
  void lastStandGivesAGuardianThatDropsBelowItsThresholdResistanceOncePerCooldown() {
    IronGolem golem = living(IronGolem.class, at(2, 64, 0));
    bound(golem, GolemCoreType.GUARDIAN);
    when(golem.getHealth()).thenReturn(30D);
    GolemGuardianCore learned = golemOwner(5);

    learned.guardianHurt(golemDamage(golem, 12D));
    learned.guardianHurt(golemDamage(golem, 12D));
    clock.addAndGet(59_999L);
    learned.guardianHurt(golemDamage(golem, 12D));
    ArgumentCaptor<PotionEffect> braced = ArgumentCaptor.forClass(PotionEffect.class);
    verify(golem, times(1)).addPotionEffect(braced.capture());
    assertThat(braced.getValue().getType()).isSameAs(PotionEffectType.RESISTANCE);
    assertThat(braced.getValue().getDuration()).isEqualTo(100);
    assertThat(braced.getValue().getAmplifier()).isEqualTo(1);

    clock.addAndGet(1L);
    learned.guardianHurt(golemDamage(golem, 12D));
    verify(golem, times(2)).addPotionEffect(any(PotionEffect.class));
  }

  @Test
  void lastStandNeedsItsLevelAGuardianCoreAndAHitTheGolemSurvives() {
    IronGolem guardian = living(IronGolem.class, at(2, 64, 0));
    bound(guardian, GolemCoreType.GUARDIAN);
    when(guardian.getHealth()).thenReturn(30D);
    IronGolem gatherer = living(IronGolem.class, at(4, 64, 0));
    bound(gatherer, GolemCoreType.GATHERER);
    when(gatherer.getHealth()).thenReturn(30D);

    golemOwner(4).guardianHurt(golemDamage(guardian, 12D));
    GolemGuardianCore learned = golemOwner(5);
    learned.guardianHurt(golemDamage(gatherer, 12D));
    learned.guardianHurt(golemDamage(guardian, 5D));
    learned.guardianHurt(golemDamage(guardian, 31D));

    verify(guardian, never()).addPotionEffect(any(PotionEffect.class));
    verify(gatherer, never()).addPotionEffect(any(PotionEffect.class));
  }

  @Test
  void threatsToTheOwnerOrAGuardianWakeTheOwnersGuardiansIdleScans() {
    GolemHandle near = guardian(at(3, 64, 0), false, GolemCoreType.GUARDIAN);
    GolemHandle sleeping = guardian(at(4, 64, 0), true, GolemCoreType.GUARDIAN);
    GolemHandle gatherer = guardian(at(5, 64, 0), false, GolemCoreType.GATHERER);
    GolemBrain nearBrain = brain(near);
    GolemBrain sleepingBrain = brain(sleeping);
    GolemBrain gathererBrain = brain(gatherer);
    Zombie zombie = living(Zombie.class, at(8, 64, 0));

    IronGolem nearBody = (IronGolem) near.entity();
    runtime.when(() -> GolemancyRuntime.handle(nearBody)).thenReturn(Optional.of(near));

    owner(1).on(hit(zombie, owner));
    adaptation.on(new EntityTargetLivingEntityEvent(zombie, owner, EntityTargetEvent.TargetReason.CLOSEST_PLAYER));
    adaptation.on(new EntityTargetLivingEntityEvent(zombie, nearBody, EntityTargetEvent.TargetReason.CLOSEST_ENTITY));
    golemOwner(1).guardianHurt(golemDamage(nearBody, 1D));

    verify(nearBrain, times(4)).alert();
    verify(sleepingBrain, never()).alert();
    verify(gathererBrain, never()).alert();
  }

  @Test
  void theAttackerIsRememberedBeforeTheGuardiansAreWoken() {
    GolemBrain brain = brain(guardian(at(3, 64, 0), false, GolemCoreType.GUARDIAN));
    GolemGuardianCore learned = owner(3);
    Zombie zombie = living(Zombie.class, at(2, 64, 0));
    AtomicReference<LivingEntity> rememberedAtAlert = new AtomicReference<>();
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
    Zombie zombie = living(Zombie.class, at(2, 64, 0));

    learned.on(hit(zombie, owner));

    assertThat(learned.recentAttacker(OWNER, clock.get())).isNull();
  }

  @Test
  void whateverHurtsTheOwnerIsRememberedForTheRetaliationWindow() {
    GolemGuardianCore learned = owner(3);
    Zombie zombie = living(Zombie.class, at(2, 64, 0));
    Skeleton archer = living(Skeleton.class, at(9, 64, 0));
    Arrow arrow = mock(Arrow.class);
    when(arrow.getShooter()).thenReturn(archer);

    learned.on(hit(zombie, owner));
    assertThat(learned.recentAttacker(OWNER, clock.get())).isSameAs(zombie);

    learned.on(hit(arrow, owner));
    assertThat(learned.recentAttacker(OWNER, clock.get())).isSameAs(archer);
    assertThat(learned.recentAttacker(OWNER, clock.get() + 4_999L)).isSameAs(archer);
    assertThat(learned.recentAttacker(OWNER, clock.get() + 5_000L)).isNull();
  }

  @Test
  void attacksAreRememberedOnlyOnceRetaliationIsUnlockedAndNeverFromTheOwner() {
    Zombie zombie = living(Zombie.class, at(2, 64, 0));
    Arrow ownArrow = mock(Arrow.class);
    when(ownArrow.getShooter()).thenReturn(owner);

    owner(2).on(hit(zombie, owner));
    GolemGuardianCore learned = owner(3);
    learned.on(hit(ownArrow, owner));

    assertThat(adaptation.recentAttacker(OWNER, clock.get())).isNull();
    assertThat(learned.recentAttacker(OWNER, clock.get())).isNull();
  }

  @Test
  void theTickSweepsExpiredAttacksAndCooldowns() {
    GolemGuardianCore learned = owner(3);
    learned.on(hit(living(Zombie.class, at(2, 64, 0)), owner));
    IronGolem golem = living(IronGolem.class, at(2, 64, 0));
    bound(golem, GolemCoreType.GUARDIAN);
    when(golem.getHealth()).thenReturn(30D);
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
    Mob body = handle.entity();
    runtime.when(() -> GolemancyRuntime.brain(body)).thenReturn(Optional.of(brain));
    return brain;
  }

  private GolemHandle guardian(Location location, boolean dormant, GolemCoreType core) {
    IronGolem golem = living(IronGolem.class, location);
    GolemHandle handle = handle(golem, core);
    lenient().when(handle.isDormant()).thenReturn(dormant);
    loaded.add(handle);
    return handle;
  }

  private void bound(IronGolem golem, GolemCoreType core) {
    GolemHandle handle = handle(golem, core);
    runtime.when(() -> GolemancyRuntime.handle(golem)).thenReturn(Optional.of(handle));
  }

  private GolemHandle handle(IronGolem golem, GolemCoreType core) {
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

  private <T extends LivingEntity> T living(Class<T> type, Location location) {
    T entity = mock(type);
    lenient().when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    lenient().when(entity.getLocation()).thenAnswer(invocation -> location.clone());
    lenient().when(entity.getWorld()).thenReturn(world);
    lenient().when(entity.isValid()).thenReturn(true);
    return entity;
  }

  private static EntityDamageEvent damage(Entity victim, DamageCause cause, double amount) {
    EntityDamageEvent event = mock(EntityDamageEvent.class);
    lenient().when(event.getEntity()).thenReturn(victim);
    lenient().when(event.getCause()).thenReturn(cause);
    lenient().when(event.getDamage()).thenReturn(amount);
    lenient().when(event.getFinalDamage()).thenReturn(amount);
    return event;
  }

  private static EntityDamageEvent golemDamage(IronGolem golem, double amount) {
    return damage(golem, DamageCause.ENTITY_ATTACK, amount);
  }

  private static EntityDamageByEntityEvent hit(Entity damager, Player victim) {
    EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
    lenient().when(event.getDamager()).thenReturn(damager);
    lenient().when(event.getEntity()).thenReturn(victim);
    return event;
  }

  private Location at(double x, double y, double z) {
    return new Location(world, x + 0.5D, y, z + 0.5D);
  }
}
