package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.testkit.FakeProjectile;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.MockedEngine;
import art.arcane.adapt.testkit.RuntimeSpy;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RangedPlayerPreferencesTest extends AdaptTestBase {
  private static final DataKey<Byte> LAUNCH_SNEAK = DataKey.adapt("fetch-launch-sneak", DataCodecs.BYTE);
  private final FakeWorld world = new FakeWorld("world");

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void fetchUsesSneakingAtLaunchInsteadOfImpact(boolean sneakingAtLaunch) {
    RangedFetchShot original = built(RangedFetchShot::new);
    ComponentHost.of(original).useFixedConfig(new RangedFetchShot.Config());
    RangedFetchShot adaptation = spy(original);
    doReturn(1).when(adaptation).getActiveLevel(any(PlayerRef.class));
    doReturn(5).when(adaptation).getMaxLevel();
    doReturn(true).when(adaptation).canAccessChest(any(PlayerRef.class), any(Loc.class));
    doReturn(true).when(adaptation).preferenceEnabled(any(PlayerRef.class), any());
    PlayerRef player = mock(PlayerRef.class);
    FakeProjectile projectile = new FakeProjectile(Id.minecraft("arrow"), new Loc(world, 1, 64, 1));
    when(player.isOnline()).thenReturn(true);
    when(player.isSneaking()).thenReturn(sneakingAtLaunch);
    projectile.setShooter(player);
    try (MockedStatic<Tasks> scheduler = SchedulerForwarding.mockTasks()) {
      scheduler.when(() -> Tasks.isOwnedByCurrentRegion(player)).thenReturn(true);
      scheduler.when(() -> Tasks.runEntity(eq(player), any(Runnable.class))).thenAnswer(call -> {
        call.<Runnable>getArgument(1).run();
        return true;
      });
      adaptation.on(new ProjectileLaunchEvent(projectile));
      assertThat(projectile.data().get(LAUNCH_SNEAK)).isEqualTo(sneakingAtLaunch ? (byte) 1 : (byte) 0);
      when(player.isSneaking()).thenReturn(!sneakingAtLaunch);
      adaptation.on(new ProjectileHitEvent(projectile, null, null, null));
      scheduler.verify(() -> Tasks.runAt(any(Loc.class), any(Runnable.class)),
          sneakingAtLaunch ? times(1) : never());
    }
  }

  @Test
  void fetchLaunchDoesNotReadSneakingAcrossPlayerRegions() {
    RangedFetchShot adaptation = built(RangedFetchShot::new);
    PlayerRef player = mock(PlayerRef.class);
    FakeProjectile projectile = new FakeProjectile(Id.minecraft("arrow"), new Loc(world, 1, 64, 1));
    projectile.setShooter(player);
    try (MockedStatic<Tasks> scheduler = SchedulerForwarding.mockTasks()) {
      adaptation.on(new ProjectileLaunchEvent(projectile));
    }
    verify(player, never()).isSneaking();
    assertThat(projectile.data().keys()).isEmpty();
  }

  @Test
  void shotCategoriesKeepTridentsAndCrossbowsDistinct() {
    FakeProjectile arrow = new FakeProjectile(Id.minecraft("arrow"), new Loc(world, 0, 64, 0));
    FakeProjectile trident = new FakeProjectile(Id.minecraft("trident"), new Loc(world, 0, 64, 0));
    assertThat(RangedPreferences.ShotScope.BOW.accepts(arrow)).isTrue();
    arrow.setShotFromCrossbow(true);
    assertThat(RangedPreferences.ShotScope.BOW.accepts(arrow)).isFalse();
    assertThat(RangedPreferences.ShotScope.CROSSBOW.accepts(arrow)).isTrue();
    assertThat(RangedPreferences.ShotScope.ARROWS.accepts(trident)).isFalse();
    assertThat(RangedPreferences.ShotScope.TRIDENTS.accepts(trident)).isTrue();
    assertThat(RangedPreferences.ShotScope.THROWN.accepts(
        new FakeProjectile(Id.minecraft("snowball"), new Loc(world, 0, 64, 0)))).isTrue();
  }

  @Test
  void personalProjectileFilterChangesOnlyThatPlayersLaunch() {
    HeavyDrawFixture adaptation = new HeavyDrawFixture(built(RangedHeavyDraw::new));
    PlayerRef bowOnly = mock(PlayerRef.class);
    PlayerRef crossbowOnly = mock(PlayerRef.class);
    adaptation.shots.put(bowOnly, RangedPreferences.ShotScope.BOW);
    adaptation.shots.put(crossbowOnly, RangedPreferences.ShotScope.CROSSBOW);
    FakeProjectile rejected = arrow(bowOnly);
    FakeProjectile accepted = arrow(crossbowOnly);
    rejected.setShotFromCrossbow(true);
    accepted.setShotFromCrossbow(true);

    adaptation.draw.on(new ProjectileLaunchEvent(rejected));
    adaptation.draw.on(new ProjectileLaunchEvent(accepted));

    assertThat(rejected.velocity()).isEqualTo(new Vec3(3, 0, 0));
    assertThat(RangedHeavyDraw.readHeavyLevel(rejected)).isZero();
    assertThat(accepted.velocity().length()).isLessThan(3D);
    assertThat(RangedHeavyDraw.readHeavyLevel(accepted)).isEqualTo(3);
  }

  @Test
  void committedHeavyShotKeepsPenaltyAndDamageAfterDisabling() {
    HeavyDrawFixture adaptation = new HeavyDrawFixture(built(RangedHeavyDraw::new));
    PlayerRef owner = mock(PlayerRef.class);
    FakeProjectile arrow = arrow(owner);
    adaptation.draw.on(new ProjectileLaunchEvent(arrow));
    double launchSpeed = arrow.velocity().length();
    adaptation.enabled = false;
    adaptation.shots.put(owner, RangedPreferences.ShotScope.TRIDENTS);
    EntityDamageByEntityEvent hit = new EntityDamageByEntityEvent(mock(EntityRef.class), arrow, owner,
        DamageCause.PROJECTILE, Id.minecraft("arrow"), 4D, false, FinalDamage.IDENTITY);
    try (MockedStatic<Tasks> scheduler = SchedulerForwarding.mockTasks()) {
      adaptation.draw.on(hit);
    }
    assertThat(launchSpeed).isLessThan(3D);
    assertThat(arrow.velocity().length()).isEqualTo(launchSpeed);
    assertThat(hit.damage()).isGreaterThan(4D);
  }

  private <A extends SimpleAdaptation<?>> A built(Supplier<A> factory) {
    try (MockedEngine engine = new MockedEngine(core)) {
      return TestContent.adaptation(Id.adapt("ranged"), factory);
    }
  }

  private FakeProjectile arrow(PlayerRef owner) {
    FakeProjectile arrow = new FakeProjectile(Id.minecraft("arrow"), new Loc(world, 0, 64, 0));
    arrow.setShooter(owner);
    arrow.setVelocity(new Vec3(3, 0, 0));
    return arrow;
  }

  /** A heavy draw at level 3 of 4 whose shot filter and enabled state the test sets per player. */
  private static final class HeavyDrawFixture {
    private final Map<PlayerRef, RangedPreferences.ShotScope> shots = new IdentityHashMap<>();
    private final RangedHeavyDraw draw;
    private boolean enabled = true;

    private HeavyDrawFixture(RangedHeavyDraw original) {
      ComponentHost.of(original).useFixedConfig(new RangedHeavyDraw.Config());
      draw = spy(original);
      AdaptationHost runtime = RuntimeSpy.install(original, draw);
      FxEmitter emitter = mock(FxEmitter.class, RETURNS_SELF);
      doReturn(emitter).when(runtime).fx(any(Loc.class), any(FxPriority.class));
      doReturn(emitter).when(runtime).fx(any(EntityRef.class), any(FxPriority.class));
      doReturn(4).when(draw).getMaxLevel();
      doAnswer(call -> enabled ? 3 : 0).when(draw).getActiveLevel(any());
      doReturn(false).when(draw).isProtectedFriendly(any(), any(EntityRef.class));
      doAnswer(call -> {
        PlayerPreference<?> preference = call.getArgument(1);
        PlayerRef player = call.getArgument(0);
        return preference.parse(shots.getOrDefault(player, RangedPreferences.ShotScope.ALL).name()).orElseThrow();
      }).when(draw).preference(any(), any());
    }
  }
}
