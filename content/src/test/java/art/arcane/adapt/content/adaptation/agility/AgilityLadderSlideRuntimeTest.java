package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.scheduling.Tasks;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgilityLadderSlideRuntimeTest {
  @TempDir
  Path data;

  private TestAdapt adapt;

  @BeforeEach
  void start() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build(), List.of());
  }

  @AfterEach
  void stop() {
    adapt.close();
  }

  private static final Id LADDER = Id.minecraft("ladder");
  private static final Id AIR = Id.minecraft("air");

  @Test
  void cameraPitchDirectlySelectsAscentAndDescent() {
    assertThat(AgilityLadderSlide.resolveMode(AgilityLadderSlide.Mode.NONE, -20F, 20D, 10D))
        .isEqualTo(AgilityLadderSlide.Mode.CLIMB);
    assertThat(AgilityLadderSlide.resolveMode(AgilityLadderSlide.Mode.NONE, 20F, 20D, 10D))
        .isEqualTo(AgilityLadderSlide.Mode.SLIDE);
    assertThat(AgilityLadderSlide.resolveMode(AgilityLadderSlide.Mode.NONE, 0F, 20D, 10D))
        .isEqualTo(AgilityLadderSlide.Mode.NONE);
  }

  @Test
  void lookHysteresisPreventsDirectionFlickerAndReversesImmediately() {
    assertThat(AgilityLadderSlide.resolveMode(AgilityLadderSlide.Mode.CLIMB, -11F, 20D, 10D))
        .isEqualTo(AgilityLadderSlide.Mode.CLIMB);
    assertThat(AgilityLadderSlide.resolveMode(AgilityLadderSlide.Mode.CLIMB, -10F, 20D, 10D))
        .isEqualTo(AgilityLadderSlide.Mode.NONE);
    assertThat(AgilityLadderSlide.resolveMode(AgilityLadderSlide.Mode.SLIDE, 11F, 20D, 10D))
        .isEqualTo(AgilityLadderSlide.Mode.SLIDE);
    assertThat(AgilityLadderSlide.resolveMode(AgilityLadderSlide.Mode.SLIDE, 10F, 20D, 10D))
        .isEqualTo(AgilityLadderSlide.Mode.NONE);
    assertThat(AgilityLadderSlide.resolveMode(AgilityLadderSlide.Mode.CLIMB, 20F, 20D, 10D))
        .isEqualTo(AgilityLadderSlide.Mode.SLIDE);
    assertThat(AgilityLadderSlide.resolveMode(AgilityLadderSlide.Mode.SLIDE, -20F, 20D, 10D))
        .isEqualTo(AgilityLadderSlide.Mode.CLIMB);
  }

  @Test
  void invalidPitchReturnsToNormalLadderControl() {
    assertThat(AgilityLadderSlide.resolveMode(AgilityLadderSlide.Mode.CLIMB, Float.NaN, 20D, 10D))
        .isEqualTo(AgilityLadderSlide.Mode.NONE);
    assertThat(AgilityLadderSlide.resolveMode(AgilityLadderSlide.Mode.SLIDE, Float.POSITIVE_INFINITY, 20D, 10D))
        .isEqualTo(AgilityLadderSlide.Mode.NONE);
  }

  @Test
  void sneakingOverridesEveryGazeDirectionAndHeldMovementMode() {
    assertThat(AgilityLadderSlide.resolveMode(AgilityLadderSlide.Mode.CLIMB, -70F, 20D, 10D, true))
        .isEqualTo(AgilityLadderSlide.Mode.NONE);
    assertThat(AgilityLadderSlide.resolveMode(AgilityLadderSlide.Mode.SLIDE, 70F, 20D, 10D, true))
        .isEqualTo(AgilityLadderSlide.Mode.NONE);
    assertThat(AgilityLadderSlide.resolveMode(AgilityLadderSlide.Mode.NONE, -70F, 20D, 10D, false))
        .isEqualTo(AgilityLadderSlide.Mode.CLIMB);
    assertThat(AgilityLadderSlide.resolveMode(AgilityLadderSlide.Mode.NONE, 70F, 20D, 10D, false))
        .isEqualTo(AgilityLadderSlide.Mode.SLIDE);
  }

  @Test
  void gazeModesProduceFastSignedMotionWithoutUnsafeValues() {
    assertThat(AgilityLadderSlide.targetVerticalVelocity(AgilityLadderSlide.Mode.CLIMB, 0.5D, 0.6D))
        .isEqualTo(0.5D);
    assertThat(AgilityLadderSlide.targetVerticalVelocity(AgilityLadderSlide.Mode.SLIDE, 0.5D, 0.6D))
        .isEqualTo(-0.6D);
    assertThat(AgilityLadderSlide.targetVerticalVelocity(AgilityLadderSlide.Mode.NONE, 0.5D, 0.6D))
        .isZero();
    assertThat(AgilityLadderSlide.normalizeSpeed(50D)).isEqualTo(1D);
    assertThat(AgilityLadderSlide.normalizeSpeed(Double.NaN)).isZero();
  }

  @Test
  void lookThresholdsNormalizeToAStableHysteresisWindow() {
    assertThat(AgilityLadderSlide.normalizeActivation(0D)).isEqualTo(1D);
    assertThat(AgilityLadderSlide.normalizeActivation(100D)).isEqualTo(89D);
    assertThat(AgilityLadderSlide.normalizeActivation(Double.NaN)).isEqualTo(30D);
    assertThat(AgilityLadderSlide.normalizeRelease(30D, 20D)).isEqualTo(19D);
    assertThat(AgilityLadderSlide.normalizeRelease(-1D, 20D)).isZero();
    assertThat(AgilityLadderSlide.normalizeRelease(Double.NaN, 20D)).isEqualTo(15D);
  }

  @Test
  void loadedConfigIsCanonicalizedToSafeGazeAndMotionValues() {
    AgilityLadderSlide adaptation = TestContent.adaptation(Id.adapt("agility"), AgilityLadderSlide::new);
    AgilityLadderSlide.Config config = new AgilityLadderSlide.Config();
    config.descentSpeedBase = 40D;
    config.descentSpeedPerLevel = -2D;
    config.climbAssistBase = Double.NaN;
    config.climbAssistPerLevel = 2D;
    config.lookActivationDegrees = Double.NaN;
    config.lookReleaseDegrees = 80D;
    config.maxLevel = 3;

    adaptation.normalizeLoadedConfig(config);

    assertThat(config.descentSpeedBase).isEqualTo(1D);
    assertThat(config.descentSpeedPerLevel).isZero();
    assertThat(config.climbAssistBase).isZero();
    assertThat(config.climbAssistPerLevel).isEqualTo(1D);
    assertThat(config.lookActivationDegrees).isEqualTo(30D);
    assertThat(config.lookReleaseDegrees).isEqualTo(29D);
    assertThat(config.maxLevel).isEqualTo(3);
  }

  @Test
  void legacyDefaultLookWindowWidensByFiftyPercent() {
    AgilityLadderSlide adaptation = TestContent.adaptation(Id.adapt("agility"), AgilityLadderSlide::new);
    AgilityLadderSlide.Config config = new AgilityLadderSlide.Config();
    config.lookActivationDegrees = 20D;
    config.lookReleaseDegrees = 10D;

    adaptation.normalizeLoadedConfig(config);

    assertThat(config.lookActivationDegrees).isEqualTo(30D);
    assertThat(config.lookReleaseDegrees).isEqualTo(15D);
  }

  @Test
  void defaultsExposeFastGazeControlAndSafeLanding() throws NoSuchMethodException {
    AgilityLadderSlide.Config config = new AgilityLadderSlide.Config();
    Method handler = AgilityLadderSlide.class.getDeclaredMethod("on", EntityDamageEvent.class);
    Handle eventHandler = handler.getAnnotation(Handle.class);
    Method sneakHandler = AgilityLadderSlide.class.getDeclaredMethod("on", PlayerToggleSneakEvent.class);
    Handle sneakEventHandler = sneakHandler.getAnnotation(Handle.class);

    assertThat(config.descentSpeedBase + config.descentSpeedPerLevel).isCloseTo(0.6D, offset(1.0E-9D));
    assertThat(config.climbAssistBase + config.climbAssistPerLevel).isCloseTo(0.5D, offset(1.0E-9D));
    assertThat(config.lookActivationDegrees).isEqualTo(30D);
    assertThat(config.lookReleaseDegrees).isEqualTo(15D);
    assertThat(config.safeLanding).isTrue();
    assertThat(eventHandler).isNotNull();
    assertThat(eventHandler.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(eventHandler.ignoreCancelled()).isTrue();
    assertThat(sneakEventHandler).isNotNull();
    assertThat(sneakEventHandler.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(sneakEventHandler.ignoreCancelled()).isTrue();
  }

  @Test
  void firstAndLastTwoBlocksOfAColumnAreNormalControlBuffers() {
    Map<Integer, BlockRef> column = ladderColumn(6);

    assertThat(isInEndBuffer(column.get(0))).isTrue();
    assertThat(isInEndBuffer(column.get(1))).isTrue();
    assertThat(isInEndBuffer(column.get(2))).isFalse();
    assertThat(isInEndBuffer(column.get(3))).isFalse();
    assertThat(isInEndBuffer(column.get(4))).isTrue();
    assertThat(isInEndBuffer(column.get(5))).isTrue();
  }

  @Test
  void clientClimbingTagChangesOnlyOnStateTransitionsAndAfterResourceReload() throws Exception {
    RecordingLadderSlide adaptation = TestContent.adaptation(Id.adapt("agility"), RecordingLadderSlide::new);
    PlayerRef player = playerAt(12D);
    Object session = addControlSession(adaptation, player);

    assertThat(ensureClientState(adaptation, session, LADDER, true)).isTrue();
    assertThat(ensureClientState(adaptation, session, LADDER, true)).isTrue();
    assertThat(adaptation.clientStates).containsExactly(LADDER);

    adaptation.invalidateClientTagPackets();
    assertThat(ensureClientState(adaptation, session, LADDER, true)).isTrue();
    assertThat(adaptation.clientStates).containsExactly(LADDER, LADDER);

    assertThat(ensureClientState(adaptation, session, LADDER, false)).isTrue();
    assertThat(ensureClientState(adaptation, session, LADDER, false)).isTrue();
    assertThat(adaptation.clientStates).containsExactly(LADDER, LADDER, null);
  }

  @Test
  void releasingTheLookGestureStopsResidualCustomVerticalMotionOnce() throws Exception {
    RecordingLadderSlide adaptation = TestContent.adaptation(Id.adapt("agility"), RecordingLadderSlide::new);
    PlayerRef player = playerAt(12D);
    Object session = addControlSession(adaptation, player);
    setSessionMode(session, AgilityLadderSlide.Mode.CLIMB);
    BlockRef ladder = mock(BlockRef.class);
    when(ladder.type()).thenReturn(LADDER);

    assertThat(applyMode(adaptation, session, ladder, AgilityLadderSlide.Mode.NONE)).isTrue();
    assertThat(applyMode(adaptation, session, ladder, AgilityLadderSlide.Mode.NONE)).isTrue();

    assertThat(adaptation.verticalMotions).containsExactly(0D);
  }

  @Test
  void haltingASlideRestoresClientClimbingAndStopsCustomVerticalMotion() throws Exception {
    RecordingLadderSlide adaptation = TestContent.adaptation(Id.adapt("agility"), RecordingLadderSlide::new);
    PlayerRef player = playerAt(12D);
    Object session = addControlSession(adaptation, player);
    BlockRef ladder = mock(BlockRef.class);
    when(ladder.type()).thenReturn(LADDER);
    ensureClientState(adaptation, session, LADDER, true);
    setSessionMode(session, AgilityLadderSlide.Mode.SLIDE);

    assertThat(applyMode(adaptation, session, ladder, AgilityLadderSlide.Mode.NONE)).isTrue();

    assertThat(adaptation.clientStates).containsExactly(LADDER, null);
    assertThat(adaptation.verticalMotions).containsExactly(0D);
  }

  @Test
  void schedulerRejectionRestoresClientTagsAndRetiresControlSession() throws Exception {
    RecordingLadderSlide adaptation = TestContent.adaptation(Id.adapt("agility"), RecordingLadderSlide::new);
    PlayerRef player = playerAt(12D);
    Object session = addControlSession(adaptation, player);
    ensureClientState(adaptation, session, LADDER, true);

    try (MockedStatic<Tasks> scheduling = SchedulerForwarding.mockTasks()) {
      scheduling.when(() -> Tasks.runEntity(same(player), any(Runnable.class), eq(1))).thenReturn(false);
      scheduleControl(adaptation, session);
    }

    assertThat(controlSessions(adaptation)).isEmpty();
    assertThat(adaptation.clientStates).containsExactly(LADDER, null);
  }

  @Test
  void externalTeleportOnlyCleansUpAndNeverActsAsMovement() throws Exception {
    RecordingLadderSlide adaptation = TestContent.adaptation(Id.adapt("agility"), RecordingLadderSlide::new);
    PlayerRef player = playerAt(12D);
    Object session = addControlSession(adaptation, player);
    ensureClientState(adaptation, session, LADDER, true);
    WorldRef world = player.world();

    adaptation.on(new PlayerTeleportEvent(
        player,
        new Loc(world, 0D, 12D, 0D),
        new Loc(world, 8D, 70D, 8D),
        TeleportCause.COMMAND
    ));

    assertThat(controlSessions(adaptation)).isEmpty();
    assertThat(adaptation.clientStates).containsExactly(LADDER, null);
  }

  @Test
  void unregisterRestoresAnyOutstandingClientTagLease() throws Exception {
    RecordingLadderSlide adaptation = TestContent.adaptation(Id.adapt("agility"), RecordingLadderSlide::new);
    PlayerRef player = playerAt(12D);
    Object session = addControlSession(adaptation, player);
    ensureClientState(adaptation, session, LADDER, true);

    adaptation.unregister();

    assertThat(controlSessions(adaptation)).isEmpty();
    assertThat(adaptation.clientStates).containsExactly(LADDER, null);
  }

  private static PlayerRef playerAt(double y) {
    PlayerRef player = mock(PlayerRef.class);
    WorldRef world = mock(WorldRef.class);
    UUID playerId = UUID.randomUUID();
    UUID worldId = UUID.randomUUID();
    when(player.uuid()).thenReturn(playerId);
    when(player.world()).thenReturn(world);
    when(world.uid()).thenReturn(worldId);
    when(player.location()).thenReturn(new Loc(world, 0D, y, 0D));
    return player;
  }

  private static Map<Integer, BlockRef> ladderColumn(int height) {
    Map<Integer, BlockRef> column = new HashMap<>();
    for (int index = -1; index <= height; index++) {
      BlockRef block = mock(BlockRef.class);
      when(block.type()).thenReturn(index >= 0 && index < height ? LADDER : AIR);
      column.put(index, block);
    }
    for (int index = 0; index < height; index++) {
      when(column.get(index).relative(BlockFace.DOWN)).thenReturn(column.get(index - 1));
      when(column.get(index).relative(BlockFace.UP)).thenReturn(column.get(index + 1));
    }
    return column;
  }

  private static boolean isInEndBuffer(BlockRef block) {
    return AgilityLadderSlide.isInColumnEndBuffer(block, material -> material.equals(LADDER));
  }

  private static Object addControlSession(AgilityLadderSlide adaptation, PlayerRef player) throws Exception {
    Class<?> sessionClass = controlSessionClass();
    Constructor<?> constructor = sessionClass.getDeclaredConstructor(PlayerRef.class, Loc.class);
    constructor.setAccessible(true);
    Object session = constructor.newInstance(player, player.location());
    controlSessions(adaptation).put(player.uuid(), session);
    return session;
  }

  private static boolean ensureClientState(
      AgilityLadderSlide adaptation,
      Object session,
      Id material,
      boolean suppressed
  ) throws Exception {
    Method method = AgilityLadderSlide.class.getDeclaredMethod(
        "ensureClientClimbingState",
        controlSessionClass(),
        Id.class,
        boolean.class
    );
    method.setAccessible(true);
    return (boolean) method.invoke(adaptation, session, material, suppressed);
  }

  private static void scheduleControl(AgilityLadderSlide adaptation, Object session) throws Exception {
    Method method = AgilityLadderSlide.class.getDeclaredMethod("scheduleControlTick", controlSessionClass());
    method.setAccessible(true);
    method.invoke(adaptation, session);
  }

  private static boolean applyMode(
      AgilityLadderSlide adaptation,
      Object session,
      BlockRef climbable,
      AgilityLadderSlide.Mode mode
  ) throws Exception {
    Method method = AgilityLadderSlide.class.getDeclaredMethod(
        "applyMode",
        controlSessionClass(),
        int.class,
        BlockRef.class,
        AgilityLadderSlide.Mode.class
    );
    method.setAccessible(true);
    return (boolean) method.invoke(adaptation, session, 1, climbable, mode);
  }

  private static void setSessionMode(Object session, AgilityLadderSlide.Mode mode) throws Exception {
    Field field = controlSessionClass().getDeclaredField("mode");
    field.setAccessible(true);
    field.set(session, mode);
  }

  private static Class<?> controlSessionClass() {
    for (Class<?> nested : AgilityLadderSlide.class.getDeclaredClasses()) {
      if (nested.getSimpleName().equals("ControlSession")) {
        return nested;
      }
    }
    throw new IllegalStateException("Missing ControlSession");
  }

  @SuppressWarnings("unchecked")
  private static Map<UUID, Object> controlSessions(AgilityLadderSlide adaptation) throws Exception {
    Field field = AgilityLadderSlide.class.getDeclaredField("controlSessions");
    field.setAccessible(true);
    return (Map<UUID, Object>) field.get(adaptation);
  }

  private static final class RecordingLadderSlide extends AgilityLadderSlide {
    private final List<Id> clientStates = new ArrayList<>();
    private final List<Double> verticalMotions = new ArrayList<>();

    @Override
    protected boolean sendVerticalMotion(PlayerRef player, double targetY) {
      verticalMotions.add(targetY);
      return true;
    }

    @Override
    protected boolean sendClientClimbingState(PlayerRef player, Id suppressedMaterial) {
      clientStates.add(suppressedMaterial);
      return true;
    }
  }
}
