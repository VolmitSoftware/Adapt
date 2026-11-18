package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.api.projectile.ProjectileCooperativeKeys;
import art.arcane.adapt.content.adaptation.rift.RiftPearlRebound;
import art.arcane.adapt.platform.data.DataCodec;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.data.DataType;
import art.arcane.adapt.platform.data.DataValue;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeProjectile;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;

class ProjectileModifierCompatibilityTest {
  private static final Id ENDER_PEARL = Id.minecraft("ender_pearl");
  private static final Id REBOUND_LEVEL = Id.adapt("rift_pearl_rebound_level");
  private static final Id REBOUNDED = Id.adapt("rift_pearl_rebounded");
  private static final Id TAGLOCK_TARGET = Id.adapt("rift_taglock_target_uuid");

  @TempDir
  Path dataDirectory;

  private TestAdapt adapt;
  private FakeWorld world;
  private FakePlayer player;
  private FakeProjectile pearl;
  private DataHolder stored;
  private RiftPearlRebound rebound;
  private List<Consumer<ProjectileLaunchEvent>> modifiers;

  @BeforeEach
  void prepareProjectile() {
    adapt = TestAdapt.start(TestPlatform.builder(dataDirectory).build(), List.of());
    world = adapt.platform().world();
    player = adapt.platform().player("Thrower", 0D, 64D, 0D);
    pearl = world.add(new FakeProjectile(ENDER_PEARL, new Loc(world, 0D, 64D, 0D)));
    pearl.setShooter(player);
    pearl.setVelocity(new Vec3(1D, 0D, 0D));
    stored = pearl.data();

    RangedFetchShot fetch = spy(new RangedFetchShot());
    doReturn(2).when(fetch).getActiveLevel(player);
    RangedFloaters floaters = spy(new RangedFloaters());
    doReturn(2).when(floaters).getActiveLevel(player);
    doReturn(false).when(floaters).preferenceEnabled(player, RangedPreferences.SNEAK);
    doReturn(RangedPreferences.Targets.ALL).when(floaters).preference(player, RangedPreferences.TARGETS);
    RangedPinningShot pinning = spy(new RangedPinningShot());
    doReturn(2).when(pinning).getActiveLevel(player);
    doReturn(false).when(pinning).preferenceEnabled(player, RangedPreferences.SNEAK);
    doReturn(true).when(pinning).preferenceEnabled(player, RangedPreferences.DAMPEN);
    modifiers = List.of(fetch::on, floaters::on, pinning::on);

    rebound = spy(new RiftPearlRebound());
    doReturn(2).when(rebound).getActiveLevel(player);
    doReturn(true).when(rebound).preferenceEnabled(player, RiftPearlRebound.REBOUND);
    doReturn(false).when(rebound).preferenceEnabled(player, RiftPearlRebound.SNEAK);
  }

  @AfterEach
  void tearDown() {
    adapt.close();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void cooperativeLaunchModifiersAllowReboundInEitherListenerOrder(boolean reboundFirst) {
    try (MockedStatic<Tasks> scheduler = mockStatic(Tasks.class, CALLS_REAL_METHODS)) {
      scheduler.when(() -> Tasks.runEntity(eq(player), any(Runnable.class))).thenReturn(true);
      ProjectileLaunchEvent launch = new ProjectileLaunchEvent(pearl);
      if (reboundFirst) {
        rebound.on(launch);
      }
      for (Consumer<ProjectileLaunchEvent> modifier : modifiers) {
        modifier.accept(launch);
      }
      if (!reboundFirst) {
        rebound.on(launch);
      }

      assertThat(stored.keys()).hasSize(8);
      assertThat(stored.get(DataKey.of(REBOUND_LEVEL, DataCodecs.INT))).isEqualTo(2);
      assertThat(ProjectileCooperativeKeys.isUnclaimed(pearl.data(), REBOUND_LEVEL, REBOUNDED)).isTrue();
      assertThat(ProjectileCooperativeKeys.isUnclaimed(pearl.data())).isFalse();
      ProjectileHitEvent hit = hitBlock(pearl, BlockFace.WEST);
      rebound.on(hit);

      assertThat(hit.isCancelled()).isTrue();
      assertThat(pearl.isValid()).isFalse();
      scheduler.verify(() -> Tasks.runEntity(eq(player), any(Runnable.class)));
    }
  }

  @Test
  void replacementPearlRetainsDetachedModifierDataAndCannotReboundAgain() {
    launchModifiers();
    Map<Id, DataValue> expectedModifiers = raw(stored);
    rebound.on(new ProjectileLaunchEvent(pearl));
    doReturn(true).when(rebound).isRuntimeRegistered();
    List<Runnable> playerTasks = new ArrayList<>();
    try (MockedStatic<Tasks> scheduler = mockStatic(Tasks.class, CALLS_REAL_METHODS)) {
      scheduler.when(() -> Tasks.runEntity(eq(player), any(Runnable.class))).thenAnswer(call -> {
        playerTasks.add(call.getArgument(1));
        return true;
      });
      ProjectileHitEvent firstHit = hitBlock(pearl, BlockFace.WEST);

      rebound.on(firstHit);
      for (Id key : stored.keys()) {
        stored.remove(key);
      }
      assertThat(playerTasks).hasSize(1);
      playerTasks.removeFirst().run();

      assertThat(firstHit.isCancelled()).isTrue();
      ProjectileRef replacement = replacementPearl();
      Map<Id, DataValue> replacementData = raw(replacement.data());
      assertThat(replacementData).hasSize(8).containsAllEntriesOf(expectedModifiers)
          .containsEntry(REBOUNDED, new DataValue.ByteValue((byte) 1)).doesNotContainKey(REBOUND_LEVEL);
      assertThat(replacement.shooter()).isSameAs(player);
      assertThat(replacement.velocity().lengthSquared()).isPositive();
      assertThat(playerTasks).hasSize(1);
      ProjectileHitEvent secondHit = hitBlock(replacement, BlockFace.EAST);
      rebound.on(secondHit);
      assertThat(secondHit.isCancelled()).isFalse();
      assertThat(replacement.isValid()).isTrue();
    }
  }

  @Test
  void detachedModifierSnapshotExcludesOwnershipAndUnknownPayloads() {
    launchModifiers();
    Map<Id, DataValue> expectedModifiers = raw(stored);
    stored.set(DataKey.of(REBOUND_LEVEL, DataCodecs.INT), 2);
    stored.set(DataKey.of(TAGLOCK_TARGET, DataCodecs.STRING), UUID.randomUUID().toString());

    Map<Id, DataValue> snapshot = ProjectileCooperativeKeys.snapshotCooperative(pearl.data());

    assertThat(snapshot).isEqualTo(expectedModifiers);
    assertThat(stored.keys()).hasSize(9).contains(TAGLOCK_TARGET).contains(REBOUND_LEVEL);
    for (Id key : stored.keys()) {
      stored.remove(key);
    }
    assertThat(snapshot).isEqualTo(expectedModifiers);
  }

  @Test
  void eachModifierKeyIsCooperativeAndDoesNotHideAnExclusivePayload() {
    launchModifiers();
    assertThat(stored.keys()).hasSize(7);
    Set<Id> modifierKeys = new HashSet<>(stored.keys());
    for (Id modifier : modifierKeys) {
      for (Id key : stored.keys()) {
        stored.remove(key);
      }
      stored.set(DataKey.of(modifier, DataCodecs.STRING), "metadata");
      assertThat(ProjectileCooperativeKeys.isUnclaimed(pearl.data())).as(modifier.toString()).isTrue();
      stored.set(DataKey.of(TAGLOCK_TARGET, DataCodecs.STRING), UUID.randomUUID().toString());
      assertThat(ProjectileCooperativeKeys.isUnclaimed(pearl.data())).as(modifier.toString()).isFalse();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "adapt:rift_taglock_target_uuid",
      "adapt:rift_conduit_taglock_loc",
      "adapt:rift_conduit_taglock_id",
      "adapt:unregistered_payload",
      "foreign:ranged_floaters_level",
      "foreign:fetch-launch-sneak"
  })
  void cooperativeModifiersDoNotAllowForeignPayloadsAtLaunch(String payloadKey) {
    launchModifiers();
    stored.set(DataKey.of(Id.parse(payloadKey), DataCodecs.STRING), "payload");

    rebound.on(new ProjectileLaunchEvent(pearl));

    assertThat(stored.keys()).doesNotContain(REBOUND_LEVEL);
    assertThat(ProjectileCooperativeKeys.isUnclaimed(pearl.data(), REBOUND_LEVEL, REBOUNDED)).isFalse();
  }

  @Test
  void payloadAddedAfterLaunchStillPreventsRebound() {
    rebound.on(new ProjectileLaunchEvent(pearl));
    launchModifiers();
    stored.set(DataKey.of(TAGLOCK_TARGET, DataCodecs.STRING), UUID.randomUUID().toString());
    ProjectileHitEvent hit = hitBlock(pearl, BlockFace.WEST);

    rebound.on(hit);

    assertThat(hit.isCancelled()).isFalse();
    assertThat(pearl.isValid()).isTrue();
  }

  @Test
  void cooperativeMetadataDoesNotAllowASecondRebound() {
    rebound.on(new ProjectileLaunchEvent(pearl));
    launchModifiers();
    stored.set(DataKey.of(REBOUNDED, DataCodecs.BYTE), (byte) 1);
    ProjectileHitEvent hit = hitBlock(pearl, BlockFace.WEST);

    rebound.on(hit);

    assertThat(hit.isCancelled()).isFalse();
    assertThat(pearl.isValid()).isTrue();
  }

  @Test
  void recreatingModifiersKeepsExistingProjectileMetadataCooperative() {
    launchModifiers();

    new RangedFetchShot();
    new RangedFloaters();
    new RangedPinningShot();

    assertThat(stored.keys()).hasSize(7);
    assertThat(ProjectileCooperativeKeys.isUnclaimed(pearl.data())).isTrue();
  }

  private void launchModifiers() {
    ProjectileLaunchEvent launch = new ProjectileLaunchEvent(pearl);
    for (Consumer<ProjectileLaunchEvent> modifier : modifiers) {
      modifier.accept(launch);
    }
  }

  private ProjectileRef replacementPearl() {
    for (art.arcane.adapt.platform.entity.EntityRef entity : world.entities()) {
      if (entity instanceof ProjectileRef projectile && projectile != pearl && projectile.type().equals(ENDER_PEARL)) {
        return projectile;
      }
    }
    throw new AssertionError("No replacement pearl was spawned");
  }

  private static ProjectileHitEvent hitBlock(ProjectileRef projectile, BlockFace face) {
    return new ProjectileHitEvent(projectile, null, mock(BlockRef.class), face);
  }

  private static Map<Id, DataValue> raw(DataHolder data) {
    Map<Id, DataValue> values = new HashMap<>();
    for (Id key : data.keys()) {
      values.put(key, data.get(DataKey.of(key, new RawCodec(DataType.STRING))));
    }
    return values;
  }

  private record RawCodec(DataType type) implements DataCodec<DataValue> {
    @Override
    public DataValue encode(DataValue value) {
      return value;
    }

    @Override
    public DataValue decode(DataValue value) {
      return value;
    }
  }
}
