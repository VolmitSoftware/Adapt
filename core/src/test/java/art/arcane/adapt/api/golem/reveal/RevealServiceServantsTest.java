package art.arcane.adapt.api.golem.reveal;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.fx.ViewerGlowPort;
import art.arcane.adapt.localization.MarkupPort;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.display.TextDisplayRef;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Transform;
import art.arcane.adapt.platform.viewer.GlowColor;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.api.fx.display.HiddenDisplays;
import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemVitals;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.ServantFrame;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.volmlib.util.math.M;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RevealServiceServantsTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
  private static final int SERVANT_RUNTIME_ID = 4_101;
  private static final int GOLEM_RUNTIME_ID = 4_202;
  private static final long CLOCK = 1_700_000_000_000L;

  private final List<TextDisplayRef> spawned = new ArrayList<>();
  private final List<String> texts = new ArrayList<>();
  private final List<MobRef> pack = new ArrayList<>();
  private final AtomicReference<Integer> frameLevel = new AtomicReference<>(1);
  private final AtomicReference<Integer> channels = new AtomicReference<>(ChannelMask.of(DyeColor.RED));
  private final ServantFrame.Frame frame = mock(ServantFrame.Frame.class);
  private final ViewerGlowPort glow = mock(ViewerGlowPort.class);
  private MockedStatic<M> time;
  private MockedStatic<Tasks> scheduling;
  private MockedStatic<HiddenDisplays> displays;
  private MockedStatic<GolemItems> items;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<GolemVitals> vitals;
  private WorldRef world;
  private PlayerRef viewer;
  private GolemRegistry registry;

  @BeforeEach
  void setUp() {
    time = mockStatic(M.class);
    time.when(M::ms).thenReturn(CLOCK);
    scheduling = mockStatic(Tasks.class);
    scheduling.when(() -> Tasks.runEntity(any(EntityRef.class), any(Runnable.class))).thenAnswer(invocation -> run(invocation.getArgument(1)));
    scheduling.when(() -> Tasks.runEntity(any(EntityRef.class), any(Runnable.class), anyInt())).thenAnswer(invocation -> run(invocation.getArgument(1)));
    scheduling.when(Tasks::isFoliaThreading).thenReturn(false);
    scheduling.when(() -> Tasks.isOwnedByCurrentRegion(any(EntityRef.class))).thenReturn(true);
    displays = mockStatic(HiddenDisplays.class);
    displays.when(() -> HiddenDisplays.spawnLabel(any(Loc.class), anyString(), anyInt(), anyFloat()))
        .thenAnswer(invocation -> spawnDisplay(invocation.getArgument(1)));
    items = mockStatic(GolemItems.class);
    items.when(() -> GolemItems.kindOf(any(ItemRef.class))).thenReturn(Optional.of(GolemItemKind.CROOK));
    registry = mock(GolemRegistry.class);
    lenient().when(registry.loadedOf(any(UUID.class))).thenReturn(List.of());
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
    vitals = mockStatic(GolemVitals.class);
    vitals.when(() -> GolemVitals.maxHealth(any())).thenReturn(24D);
    world = mock(WorldRef.class);
    lenient().when(world.uid()).thenReturn(WORLD);
    viewer = mock(PlayerRef.class);
    PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    lenient().when(inventory.mainHand()).thenReturn(mock(ItemRef.class));
    lenient().when(inventory.offHand()).thenReturn(mock(ItemRef.class));
    lenient().when(viewer.uuid()).thenReturn(OWNER);
    lenient().when(viewer.isOnline()).thenReturn(true);
    lenient().when(viewer.inventory()).thenReturn(inventory);
    lenient().when(viewer.location()).thenAnswer(invocation -> new Loc(world, 0D, 64D, 0D));
    lenient().when(viewer.world()).thenReturn(world);
    AdaptPorts.install(ViewerGlowPort.class, glow);
    lenient().when(glow.isAvailable()).thenReturn(true);
    lenient().when(glow.set(any(), any(), any(), any())).thenReturn(true);
    lenient().when(frame.level(OWNER)).thenAnswer(invocation -> frameLevel.get());
    lenient().when(frame.channels(OWNER)).thenAnswer(invocation -> channels.get());
    lenient().when(frame.labelRange()).thenReturn(8D);
    lenient().when(frame.servants(OWNER)).thenAnswer(invocation -> List.copyOf(pack));
    ServantFrame.register(frame);
  }

  @AfterEach
  void tearDown() {
    ServantFrame.unregister(frame);
    vitals.close();
    runtime.close();
    items.close();
    displays.close();
    scheduling.close();
    time.close();
    AdaptPorts.uninstall(ViewerGlowPort.class);
  }

  @Test
  void aServantInsideTheLabelRangeGetsAnOwnerLabelAndTheChannelGlow() {
    MobRef servant = servant(new AtomicReference<>(5D));
    RevealService service = new RevealService(RevealSettings::defaults);

    service.update(viewer);

    assertThat(spawned).hasSize(1);
    displays.verify(() -> HiddenDisplays.show(viewer, spawned.getFirst()));
    verify(servant).addPassenger(spawned.getFirst());
    verify(glow).set(ViewerGlowPort.Layer.GOLEMANCY, servant, viewer, GlowColor.RED);
  }

  @Test
  void theServantLabelShowsItsNameHealthTimeLeftAndChannel() {
    MobRef servant = servant(new AtomicReference<>(5D));
    when(frame.expiresAt(servant)).thenReturn(CLOCK + 41_500L);
    RevealService service = new RevealService(RevealSettings::defaults);

    service.update(viewer);

    String plain = MarkupPort.current().plain(texts.getFirst());
    assertThat(plain.lines().toList()).containsExactly("Skeletal Servant", "|||||||||| 12/24", "42s left · " + GolemLabel.SWATCH);
  }

  @Test
  void anOwnerWhoHidesStatusLabelsLosesTheServantLabelButKeepsTheGlow() {
    MobRef servant = servant(new AtomicReference<>(5D));
    GolemLabelPolicy labels = mock(GolemLabelPolicy.class);
    runtime.when(GolemancyRuntime::labelPolicy).thenReturn(Optional.of(labels));
    RevealService service = new RevealService(RevealSettings::defaults);
    service.update(viewer);
    TextDisplayRef label = spawned.getFirst();

    when(labels.hidesLabels(viewer)).thenReturn(true);
    service.update(viewer);
    service.update(viewer);

    displays.verify(() -> HiddenDisplays.hide(viewer, label));
    verify(label).remove();
    assertThat(spawned).hasSize(1);
    verify(glow, times(3)).set(ViewerGlowPort.Layer.GOLEMANCY, servant, viewer, GlowColor.RED);
    verify(glow, never()).unset(eq(ViewerGlowPort.Layer.GOLEMANCY), any(UUID.class), anyInt(), eq(viewer));
  }

  @Test
  void aServantBeyondTheLabelRangeOnlyGlows() {
    MobRef servant = servant(new AtomicReference<>(20D));
    RevealService service = new RevealService(RevealSettings::defaults);

    service.update(viewer);

    assertThat(spawned).isEmpty();
    verify(glow).set(ViewerGlowPort.Layer.GOLEMANCY, servant, viewer, GlowColor.RED);
  }

  @Test
  void servantsDropTheirLabelPastTheLabelMarginAndTheirGlowPastTheRevealMargin() {
    AtomicReference<Double> distance = new AtomicReference<>(5D);
    MobRef servant = servant(distance);
    RevealService service = new RevealService(RevealSettings::defaults);
    service.update(viewer);
    TextDisplayRef label = spawned.getFirst();

    distance.set(9.5D);
    service.update(viewer);
    verify(label, never()).remove();

    distance.set(10.5D);
    service.update(viewer);
    displays.verify(() -> HiddenDisplays.hide(viewer, label));
    verify(label).remove();
    verify(glow, never()).unset(eq(ViewerGlowPort.Layer.GOLEMANCY), any(UUID.class), anyInt(), eq(viewer));

    distance.set(34.5D);
    service.update(viewer);
    verify(glow).unset(ViewerGlowPort.Layer.GOLEMANCY, servant.uuid(), SERVANT_RUNTIME_ID, viewer);
  }

  @Test
  void aChannelChangeRecolorsTheGlowOnTheNextPoll() {
    MobRef servant = servant(new AtomicReference<>(5D));
    RevealService service = new RevealService(RevealSettings::defaults);
    service.update(viewer);

    channels.set(ChannelMask.NONE);
    service.update(viewer);

    verify(glow).set(ViewerGlowPort.Layer.GOLEMANCY, servant, viewer, GlowColor.WHITE);
  }

  @Test
  void ownersWithoutBoneFrameSeeNoServantReveal() {
    servant(new AtomicReference<>(5D));
    frameLevel.set(0);
    RevealService service = new RevealService(RevealSettings::defaults);

    service.update(viewer);

    assertThat(spawned).isEmpty();
    verify(glow, never()).set(any(), any(), any(), any());
  }

  @Test
  void puttingTheRevealItemAwayHidesTheServantLabelAndGlow() {
    MobRef servant = servant(new AtomicReference<>(5D));
    RevealService service = new RevealService(RevealSettings::defaults);
    service.update(viewer);
    TextDisplayRef label = spawned.getFirst();

    items.when(() -> GolemItems.kindOf(any(ItemRef.class))).thenReturn(Optional.empty());
    service.update(viewer);

    displays.verify(() -> HiddenDisplays.hide(viewer, label));
    verify(label).remove();
    verify(glow).unset(ViewerGlowPort.Layer.GOLEMANCY, servant.uuid(), SERVANT_RUNTIME_ID, viewer);
  }

  @Test
  void aServantLabelLeftInAnotherRegionIsRemovedWithoutTouchingItFromTheViewersRegion() {
    servant(new AtomicReference<>(5D));
    RevealService service = new RevealService(RevealSettings::defaults);
    service.update(viewer);
    TextDisplayRef label = spawned.getFirst();

    scheduling.when(Tasks::isFoliaThreading).thenReturn(true);
    scheduling.when(() -> Tasks.isOwnedByCurrentRegion(label)).thenReturn(false);
    items.when(() -> GolemItems.kindOf(any(ItemRef.class))).thenReturn(Optional.empty());
    service.update(viewer);

    displays.verify(() -> HiddenDisplays.hide(viewer, label), never());
    verify(label).remove();
  }

  @Test
  void aDyingServantLosesItsLabelAndGlow() {
    MobRef servant = servant(new AtomicReference<>(5D));
    RevealService service = new RevealService(RevealSettings::defaults);
    service.update(viewer);
    TextDisplayRef label = spawned.getFirst();
    EntityDeathEvent death = mock(EntityDeathEvent.class);
    when(death.entity()).thenReturn(servant);

    when(core.getPlatform().host().player(OWNER)).thenReturn(viewer);
    service.on(death);

    displays.verify(() -> HiddenDisplays.hide(viewer, label));
    verify(label).remove();
    verify(glow).unset(ViewerGlowPort.Layer.GOLEMANCY, servant.uuid(), SERVANT_RUNTIME_ID, viewer);
  }

  @Test
  void aServantThatLeavesThePackOnExpiryLosesItsLabelAndGlowOnTheNextPoll() {
    MobRef servant = servant(new AtomicReference<>(5D));
    RevealService service = new RevealService(RevealSettings::defaults);
    service.update(viewer);
    TextDisplayRef label = spawned.getFirst();

    pack.remove(servant);
    service.update(viewer);

    displays.verify(() -> HiddenDisplays.hide(viewer, label));
    verify(label).remove();
    verify(glow).unset(ViewerGlowPort.Layer.GOLEMANCY, servant.uuid(), SERVANT_RUNTIME_ID, viewer);
    service.update(viewer);
    assertThat(spawned).hasSize(1);
  }

  @Test
  void revealedGolemsGlowInTheirFirstChannelUntilTheyLeave() {
    AtomicReference<Double> distance = new AtomicReference<>(5D);
    GolemHandle golem = golem(distance, DyeColor.LIME);
    when(registry.loadedOf(OWNER)).thenReturn(List.of(golem));
    RevealService service = new RevealService(RevealSettings::defaults);

    service.update(viewer);
    verify(glow).set(ViewerGlowPort.Layer.GOLEMANCY, golem.entity(), viewer, GlowColor.GREEN);

    distance.set(34.5D);
    service.update(viewer);
    verify(glow).unset(ViewerGlowPort.Layer.GOLEMANCY, golem.entityId(), GOLEM_RUNTIME_ID, viewer);
  }

  private MobRef servant(AtomicReference<Double> distance) {
    MobRef servant = mock(MobRef.class);
    UUID id = UUID.randomUUID();
    lenient().when(servant.uuid()).thenReturn(id);
    lenient().when(servant.entityId()).thenReturn(SERVANT_RUNTIME_ID);
    lenient().when(servant.isValid()).thenReturn(true);
    lenient().when(servant.world()).thenReturn(world);
    lenient().when(servant.location()).thenAnswer(invocation -> new Loc(world, distance.get(), 64D, 0D));
    lenient().when(servant.addPassenger(any(EntityRef.class))).thenReturn(true);
    lenient().when(servant.health()).thenReturn(12D);
    lenient().when(frame.expiresAt(servant)).thenReturn(CLOCK + 60_000L);
    pack.add(servant);
    return servant;
  }

  private GolemHandle golem(AtomicReference<Double> distance, DyeColor channel) {
    MobRef golem = mock(MobRef.class);
    UUID entityId = UUID.randomUUID();
    lenient().when(golem.uuid()).thenReturn(entityId);
    lenient().when(golem.entityId()).thenReturn(GOLEM_RUNTIME_ID);
    lenient().when(golem.isValid()).thenReturn(true);
    lenient().when(golem.world()).thenReturn(world);
    lenient().when(golem.location()).thenAnswer(invocation -> new Loc(world, distance.get(), 64D, 0D));
    lenient().when(golem.addPassenger(any(EntityRef.class))).thenReturn(true);
    lenient().when(golem.health()).thenReturn(100D);
    GolemState state = mock(GolemState.class);
    lenient().when(state.body()).thenReturn(GolemBody.IRON);
    lenient().when(state.primaryCore()).thenReturn(GolemCoreType.GUARDIAN);
    lenient().when(state.mode()).thenReturn(GolemMode.FOLLOW);
    lenient().when(state.channels()).thenReturn(ChannelMask.of(channel));
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.entityId()).thenReturn(entityId);
    lenient().when(handle.entity()).thenReturn(golem);
    lenient().when(handle.owner()).thenReturn(OWNER);
    lenient().when(handle.state()).thenReturn(state);
    return handle;
  }

  private boolean run(Runnable task) {
    task.run();
    return true;
  }

  private TextDisplayRef spawnDisplay(String text) {
    TextDisplayRef display = mock(TextDisplayRef.class);
    AtomicBoolean valid = new AtomicBoolean(true);
    lenient().when(display.isValid()).thenAnswer(invocation -> valid.get());
    lenient().doAnswer(invocation -> {
      valid.set(false);
      return null;
    }).when(display).remove();
    lenient().when(display.uuid()).thenReturn(UUID.randomUUID());
    lenient().when(display.transform()).thenReturn(Transform.IDENTITY);
    spawned.add(display);
    texts.add(text);
    return display;
  }
}
