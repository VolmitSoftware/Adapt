package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeProjectile;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.ManualScheduler;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HeartseekerSteeringCadenceTest {
  @TempDir
  Path dataDirectory;

  private AdaptCore previousCore;
  private PlatformLog previousLog;
  private TestPlatform platform;

  @BeforeEach
  void installPlatform() {
    previousCore = AdaptCore.current();
    previousLog = AdaptEnvironment.log();
    AdaptEnvironment.install(previousLog, () -> dataDirectory);
    platform = TestPlatform.builder(dataDirectory).build();
    TestCores.install(platform.platform());
  }

  @AfterEach
  void restoreCore() {
    AdaptEnvironment.install(previousLog, () -> null);
    TestCores.use(previousCore);
  }

  @Test
  void dispatchedArrowKeepsSteeringOnItsOwnSchedulerEveryTickOnRegionizedServers() throws ReflectiveOperationException {
    platform.scheduler().regionized(true);
    RangedHeartseeker adaptation = new RangedHeartseeker();
    FakeWorld world = platform.world();
    FakePlayer owner = platform.player("Archer", 0.5D, 64D, 0.5D);
    FakeProjectile arrow = new FakeProjectile(Id.minecraft("arrow"), new Loc(world, 1D, 65.5D, 0.5D));
    arrow.setShooter(owner);
    arrow.setVelocity(new Vec3(1.5D, 0D, 0D));
    arrow.setOnGround(false);
    FakeLiving cow = new FakeLiving(Id.minecraft("cow"), new Loc(world, 40D, 64D, 0.5D));
    adaptation.getConfig().ignorePassiveMobs = false;

    admit(adaptation, owner, arrow, cow, world);
    invoke(adaptation, "dispatchActiveArrows");
    ManualScheduler scheduler = platform.scheduler();
    scheduler.advance(3);

    List<Long> delays = scheduler.entityTaskDelays(arrow);
    assertThat(delays).hasSizeGreaterThanOrEqualTo(4);
    assertThat(delays.subList(1, 4)).containsOnly(1L);
  }

  @Test
  void singleThreadedServersFreeTheSlotAfterEachInlineStepAsBeforeThePort() throws ReflectiveOperationException {
    RangedHeartseeker adaptation = new RangedHeartseeker();
    FakeWorld world = platform.world();
    FakePlayer owner = platform.player("Archer", 0.5D, 64D, 0.5D);
    FakeProjectile arrow = new FakeProjectile(Id.minecraft("arrow"), new Loc(world, 1D, 65.5D, 0.5D));
    arrow.setShooter(owner);
    arrow.setVelocity(new Vec3(1.5D, 0D, 0D));
    arrow.setOnGround(false);
    FakeLiving cow = new FakeLiving(Id.minecraft("cow"), new Loc(world, 40D, 64D, 0.5D));
    adaptation.getConfig().ignorePassiveMobs = false;

    admit(adaptation, owner, arrow, cow, world);
    invoke(adaptation, "dispatchActiveArrows");

    assertThat(platform.scheduler().entityTaskDelays(arrow)).doesNotContain(1L);
  }

  @Test
  void regionizedServersSteerAsFarWithinOneWindowAsSingleThreadedOnes() throws ReflectiveOperationException {
    Vec3 singleThreaded = steeredVelocity(false);
    Vec3 regionized = steeredVelocity(true);

    assertThat(singleThreaded.x()).isCloseTo(regionized.x(), within(0.000001D));
    assertThat(singleThreaded.y()).isCloseTo(regionized.y(), within(0.000001D));
    assertThat(singleThreaded.z()).isCloseTo(regionized.z(), within(0.000001D));
    assertThat(regionized.normalize().x()).isLessThan(Math.cos(Math.toRadians(10D)));
  }

  private Vec3 steeredVelocity(boolean regionized) throws ReflectiveOperationException {
    platform.scheduler().regionized(regionized);
    RangedHeartseeker adaptation = new RangedHeartseeker();
    FakeWorld world = platform.world();
    FakePlayer owner = platform.player(regionized ? "RegionArcher" : "Archer", 0.5D, 64D, 0.5D);
    FakeProjectile arrow = new FakeProjectile(Id.minecraft("arrow"), new Loc(world, 1D, 65.5D, 0.5D));
    arrow.setShooter(owner);
    arrow.setVelocity(new Vec3(1.5D, 0D, 0D));
    arrow.setOnGround(false);
    FakeLiving cow = new FakeLiving(Id.minecraft("cow"), new Loc(world, 1D, 64D, 30D));
    adaptation.getConfig().ignorePassiveMobs = false;

    admit(adaptation, owner, arrow, cow, world);
    invoke(adaptation, "dispatchActiveArrows");
    return arrow.velocity();
  }

  @Test
  void onlyDispatchedSlotsCountAsInFlight() throws ReflectiveOperationException {
    HeartseekerCoordinator coordinator = new HeartseekerCoordinator(8, 3);
    UUID ownerId = UUID.randomUUID();
    UUID arrowId = UUID.randomUUID();
    long generation = coordinator.admit(ownerId, arrowId);

    assertThat(coordinator.isInFlight(arrowId, generation)).isFalse();
    List<HeartseekerCoordinator.Dispatch> dispatches = coordinator.takeDispatches(1);
    assertThat(dispatches).hasSize(1);
    assertThat(coordinator.isInFlight(arrowId, generation)).isTrue();
    assertThat(coordinator.takeDispatches(1)).isEmpty();

    assertThat(coordinator.suspend(arrowId, generation)).isTrue();
    assertThat(coordinator.isInFlight(arrowId, generation)).isFalse();
    assertThat(coordinator.resume(arrowId, generation)).isTrue();
    assertThat(coordinator.isInFlight(arrowId, generation)).isFalse();
    assertThat(coordinator.isInFlight(arrowId, generation + 1L)).isFalse();
  }

  private static void admit(RangedHeartseeker adaptation, PlayerRef owner, FakeProjectile arrow, LivingRef target,
                            FakeWorld world) throws ReflectiveOperationException {
    Class<?> snapshotType = nested("TargetSnapshot");
    Constructor<?> snapshotConstructor = snapshotType.getDeclaredConstructors()[0];
    snapshotConstructor.setAccessible(true);
    Loc aim = target.location().add(0D, 0.7D, 0D);
    Object snapshot = snapshotConstructor.newInstance(target, target.uuid(), world.uid(), target.location(), aim,
        false, false, false, false, System.nanoTime());

    Field lifecycleField = RangedHeartseeker.class.getDeclaredField("lifecycle");
    lifecycleField.setAccessible(true);
    long token = ((HeartseekerLifecycle) lifecycleField.get(adaptation)).current();

    Class<?> admissionType = nested("HomingAdmission");
    Constructor<?> admissionConstructor = admissionType.getDeclaredConstructors()[0];
    admissionConstructor.setAccessible(true);
    Object admission = admissionConstructor.newInstance(owner, owner.uuid(), arrow, snapshot,
        new HeartseekerPassBudget(0, 0), 1.5D, false, token);

    Method admitMethod = RangedHeartseeker.class.getDeclaredMethod("admitArrowOwned", admissionType);
    admitMethod.setAccessible(true);
    assertThat(admitMethod.invoke(adaptation, admission)).isNotNull();
  }

  private static Class<?> nested(String simpleName) {
    for (Class<?> type : RangedHeartseeker.class.getDeclaredClasses()) {
      if (type.getSimpleName().equals(simpleName)) {
        return type;
      }
    }
    throw new IllegalStateException("Missing nested type " + simpleName);
  }

  private static void invoke(Object target, String name) throws ReflectiveOperationException {
    Method method = target.getClass().getDeclaredMethod(name);
    method.setAccessible(true);
    method.invoke(target);
  }
}
