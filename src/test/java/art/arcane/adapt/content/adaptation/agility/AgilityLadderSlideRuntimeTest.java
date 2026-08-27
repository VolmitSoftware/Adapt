package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.scheduling.J;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.resources.Identifier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
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
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgilityLadderSlideRuntimeTest {
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
    AgilityLadderSlide adaptation = new AgilityLadderSlide();
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
    assertThat(adaptation.shouldCanonicalizeConfigOnLoad()).isTrue();
  }

  @Test
  void legacyDefaultLookWindowWidensByFiftyPercent() {
    AgilityLadderSlide adaptation = new AgilityLadderSlide();
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
    EventHandler eventHandler = handler.getAnnotation(EventHandler.class);
    Method sneakHandler = AgilityLadderSlide.class.getDeclaredMethod("on", PlayerToggleSneakEvent.class);
    EventHandler sneakEventHandler = sneakHandler.getAnnotation(EventHandler.class);

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
    Map<Integer, Block> column = ladderColumn(6);

    assertThat(isInEndBuffer(column.get(0))).isTrue();
    assertThat(isInEndBuffer(column.get(1))).isTrue();
    assertThat(isInEndBuffer(column.get(2))).isFalse();
    assertThat(isInEndBuffer(column.get(3))).isFalse();
    assertThat(isInEndBuffer(column.get(4))).isTrue();
    assertThat(isInEndBuffer(column.get(5))).isTrue();
  }

  @Test
  void suppressedTagPayloadPreservesEveryOtherBlockTagAndStandardPayload() {
    Identifier climbable = Identifier.withDefaultNamespace("climbable");
    Identifier mineable = Identifier.withDefaultNamespace("mineable/pickaxe");
    IntList climbableIds = new IntArrayList(new int[]{3, 7, 9});
    IntList mineableIds = new IntArrayList(new int[]{11, 12});
    Map<Identifier, IntList> standard = new HashMap<>();
    standard.put(climbable, climbableIds);
    standard.put(mineable, mineableIds);

    Map<Identifier, IntList> suppressed = AgilityLadderSlide.suppressBlockInTag(standard, climbable, 7);

    assertThat(suppressed).containsOnlyKeys(climbable, mineable);
    assertThat(suppressed.get(climbable).toIntArray()).containsExactly(3, 9);
    assertThat(suppressed.get(mineable).toIntArray()).containsExactly(11, 12);
    assertThat(standard.get(climbable).toIntArray()).containsExactly(3, 7, 9);
    assertThat(standard.get(mineable).toIntArray()).containsExactly(11, 12);
  }

  @Test
  void suppressingAnUnrelatedBlockFailsInsteadOfSendingAFalseTagView() {
    Identifier climbable = Identifier.withDefaultNamespace("climbable");
    Map<Identifier, IntList> standard = Map.of(climbable, new IntArrayList(new int[]{3, 7, 9}));

    assertThatThrownBy(() -> AgilityLadderSlide.suppressBlockInTag(standard, climbable, 12))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("12");
  }

  @Test
  void clientClimbingTagChangesOnlyOnStateTransitionsAndAfterResourceReload() throws Exception {
    RecordingLadderSlide adaptation = new RecordingLadderSlide();
    Player player = playerAt(12D);
    Object session = addControlSession(adaptation, player);

    assertThat(ensureClientState(adaptation, session, Material.LADDER, true)).isTrue();
    assertThat(ensureClientState(adaptation, session, Material.LADDER, true)).isTrue();
    assertThat(adaptation.clientStates).containsExactly(Material.LADDER);

    adaptation.invalidateClientTagPackets();
    assertThat(ensureClientState(adaptation, session, Material.LADDER, true)).isTrue();
    assertThat(adaptation.clientStates).containsExactly(Material.LADDER, Material.LADDER);

    assertThat(ensureClientState(adaptation, session, Material.LADDER, false)).isTrue();
    assertThat(ensureClientState(adaptation, session, Material.LADDER, false)).isTrue();
    assertThat(adaptation.clientStates).containsExactly(Material.LADDER, Material.LADDER, null);
  }

  @Test
  void releasingTheLookGestureStopsResidualCustomVerticalMotionOnce() throws Exception {
    RecordingLadderSlide adaptation = new RecordingLadderSlide();
    Player player = playerAt(12D);
    Object session = addControlSession(adaptation, player);
    setSessionMode(session, AgilityLadderSlide.Mode.CLIMB);
    Block ladder = mock(Block.class);
    when(ladder.getType()).thenReturn(Material.LADDER);

    assertThat(applyMode(adaptation, session, ladder, AgilityLadderSlide.Mode.NONE)).isTrue();
    assertThat(applyMode(adaptation, session, ladder, AgilityLadderSlide.Mode.NONE)).isTrue();

    assertThat(adaptation.verticalMotions).containsExactly(0D);
  }

  @Test
  void haltingASlideRestoresClientClimbingAndStopsCustomVerticalMotion() throws Exception {
    RecordingLadderSlide adaptation = new RecordingLadderSlide();
    Player player = playerAt(12D);
    Object session = addControlSession(adaptation, player);
    Block ladder = mock(Block.class);
    when(ladder.getType()).thenReturn(Material.LADDER);
    ensureClientState(adaptation, session, Material.LADDER, true);
    setSessionMode(session, AgilityLadderSlide.Mode.SLIDE);

    assertThat(applyMode(adaptation, session, ladder, AgilityLadderSlide.Mode.NONE)).isTrue();

    assertThat(adaptation.clientStates).containsExactly(Material.LADDER, null);
    assertThat(adaptation.verticalMotions).containsExactly(0D);
  }

  @Test
  void schedulerRejectionRestoresClientTagsAndRetiresControlSession() throws Exception {
    RecordingLadderSlide adaptation = new RecordingLadderSlide();
    Player player = playerAt(12D);
    Object session = addControlSession(adaptation, player);
    ensureClientState(adaptation, session, Material.LADDER, true);

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(same(player), any(Runnable.class), eq(1))).thenReturn(false);
      scheduleControl(adaptation, session);
    }

    assertThat(controlSessions(adaptation)).isEmpty();
    assertThat(adaptation.clientStates).containsExactly(Material.LADDER, null);
  }

  @Test
  void externalTeleportOnlyCleansUpAndNeverActsAsMovement() throws Exception {
    RecordingLadderSlide adaptation = new RecordingLadderSlide();
    Player player = playerAt(12D);
    Object session = addControlSession(adaptation, player);
    ensureClientState(adaptation, session, Material.LADDER, true);
    World world = player.getWorld();

    adaptation.on(new PlayerTeleportEvent(
        player,
        new Location(world, 0D, 12D, 0D),
        new Location(world, 8D, 70D, 8D),
        PlayerTeleportEvent.TeleportCause.COMMAND
    ));

    assertThat(controlSessions(adaptation)).isEmpty();
    assertThat(adaptation.clientStates).containsExactly(Material.LADDER, null);
  }

  @Test
  void unregisterRestoresAnyOutstandingClientTagLease() throws Exception {
    RecordingLadderSlide adaptation = new RecordingLadderSlide();
    Player player = playerAt(12D);
    Object session = addControlSession(adaptation, player);
    ensureClientState(adaptation, session, Material.LADDER, true);

    adaptation.unregister();

    assertThat(controlSessions(adaptation)).isEmpty();
    assertThat(adaptation.clientStates).containsExactly(Material.LADDER, null);
  }

  @Test
  void implementationUsesMotionPacketsAndContainsNoTeleportMovementCall() throws Exception {
    Path sourcePath = Path.of("src/main/java/art/arcane/adapt/content/adaptation/agility/AgilityLadderSlide.java");
    String source = Files.readString(sourcePath);

    assertThat(source).contains("ClientboundSetEntityMotionPacket");
    assertThat(source).contains("ClientboundUpdateTagsPacket");
    assertThat(source).doesNotContain(
        ".teleport(",
        ".teleportAsync(",
        "J.teleport(",
        "TeleportCause.PLUGIN",
        "performSlideTeleport",
        "PlayerInputEvent"
    );
  }

  private static Player playerAt(double y) {
    Player player = mock(Player.class);
    World world = mock(World.class);
    UUID playerId = UUID.randomUUID();
    UUID worldId = UUID.randomUUID();
    when(player.getUniqueId()).thenReturn(playerId);
    when(player.getWorld()).thenReturn(world);
    when(world.getUID()).thenReturn(worldId);
    when(player.getLocation()).thenReturn(new Location(world, 0D, y, 0D));
    return player;
  }

  private static Map<Integer, Block> ladderColumn(int height) {
    Map<Integer, Block> column = new HashMap<>();
    for (int index = -1; index <= height; index++) {
      Block block = mock(Block.class);
      when(block.getType()).thenReturn(index >= 0 && index < height ? Material.LADDER : Material.AIR);
      column.put(index, block);
    }
    for (int index = 0; index < height; index++) {
      when(column.get(index).getRelative(BlockFace.DOWN)).thenReturn(column.get(index - 1));
      when(column.get(index).getRelative(BlockFace.UP)).thenReturn(column.get(index + 1));
    }
    return column;
  }

  private static boolean isInEndBuffer(Block block) {
    return AgilityLadderSlide.isInColumnEndBuffer(block, material -> material == Material.LADDER);
  }

  private static Object addControlSession(AgilityLadderSlide adaptation, Player player) throws Exception {
    Class<?> sessionClass = controlSessionClass();
    Constructor<?> constructor = sessionClass.getDeclaredConstructor(Player.class, Location.class);
    constructor.setAccessible(true);
    Object session = constructor.newInstance(player, player.getLocation());
    controlSessions(adaptation).put(player.getUniqueId(), session);
    return session;
  }

  private static boolean ensureClientState(
      AgilityLadderSlide adaptation,
      Object session,
      Material material,
      boolean suppressed
  ) throws Exception {
    Method method = AgilityLadderSlide.class.getDeclaredMethod(
        "ensureClientClimbingState",
        controlSessionClass(),
        Material.class,
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
      Block climbable,
      AgilityLadderSlide.Mode mode
  ) throws Exception {
    Method method = AgilityLadderSlide.class.getDeclaredMethod(
        "applyMode",
        controlSessionClass(),
        int.class,
        Block.class,
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
    private final FxEmitter emitter = mock(FxEmitter.class, RETURNS_SELF);
    private final List<Material> clientStates = new ArrayList<>();
    private final List<Double> verticalMotions = new ArrayList<>();

    @Override
    protected FxEmitter fx(Location location, FxPriority priority) {
      return emitter;
    }

    @Override
    protected void addStat(Player player, String stat, double amount) {
    }

    @Override
    protected boolean sendVerticalMotion(Player player, double targetY) {
      verticalMotions.add(targetY);
      return true;
    }

    @Override
    protected boolean sendClientClimbingState(Player player, Material suppressedMaterial) {
      clientStates.add(suppressedMaterial);
      return true;
    }
  }
}
