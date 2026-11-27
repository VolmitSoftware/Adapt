package art.arcane.adapt.content.golem.reveal;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.integration.GlossOverlay;
import art.arcane.adapt.content.golem.GolemAnchor;
import art.arcane.adapt.content.golem.GolemBody;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemNameMode;
import art.arcane.adapt.content.golem.GolemNames;
import art.arcane.adapt.content.golem.GolemRegistry;
import art.arcane.adapt.content.golem.GolemSnapshot;
import art.arcane.adapt.content.golem.GolemState;
import art.arcane.adapt.content.golem.GolemVitals;
import art.arcane.adapt.content.golem.GolemWorkStatus;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.HiddenDisplays;
import art.arcane.adapt.content.golem.ServantFrame;
import art.arcane.adapt.content.golem.item.GolemItemKind;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.content.adaptation.discovery.EntityInsight;
import art.arcane.adapt.localization.MarkupPort;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.display.TextDisplayRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Transform;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.testkit.TestMarkup;
import art.arcane.volmlib.util.math.M;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RevealServiceNamesTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final long CLOCK = 1_000_000L;

  private final List<TextDisplayRef> spawned = new ArrayList<>();
  private final List<String> texts = new ArrayList<>();
  private final AtomicReference<GolemNameMode> mode = new AtomicReference<>(GolemNameMode.VANILLA);
  private final EntityInsight.Display insight = mock(EntityInsight.Display.class);
  private MockedStatic<GlossOverlay> overlay;
  private final ServantFrame.Frame frame = mock(ServantFrame.Frame.class);
  private final List<MobRef> pack = new ArrayList<>();
  private MockedStatic<M> time;
  private MockedStatic<AdaptApi> scheduling;
  private final AdaptScheduler adaptScheduler = mock(AdaptScheduler.class);
  private MockedStatic<HiddenDisplays> displays;
  private MockedStatic<GolemItems> items;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<GolemVitals> vitals;
  private WorldRef world;
  private PlayerRef viewer;
  private GolemRegistry registry;
  private RevealService service;

  @BeforeEach
  void setUp() {
    overlay = mockStatic(GlossOverlay.class);
    time = mockStatic(M.class);
    time.when(M::ms).thenReturn(CLOCK);
    AdaptPorts.install(MarkupPort.class, new TestMarkup());
    scheduling = mockStatic(AdaptApi.class, CALLS_REAL_METHODS);
    scheduling.when(AdaptApi::scheduler).thenReturn(adaptScheduler);
    when(adaptScheduler.runEntity(any(EntityRef.class), any(Runnable.class))).thenAnswer(invocation -> run(invocation.getArgument(1)));
    when(adaptScheduler.runEntity(any(EntityRef.class), any(Runnable.class), anyLong())).thenAnswer(invocation -> run(invocation.getArgument(1)));
    when(adaptScheduler.isRegionized()).thenReturn(false);
    when(adaptScheduler.owns(any(EntityRef.class))).thenReturn(true);
    displays = mockStatic(HiddenDisplays.class);
    displays.when(() -> HiddenDisplays.spawnLabel(any(Loc.class), anyString(), anyInt(), anyFloat()))
        .thenAnswer(invocation -> spawnDisplay(invocation.getArgument(1)));
    items = mockStatic(GolemItems.class);
    items.when(() -> GolemItems.kindOf(any(ItemRef.class))).thenReturn(Optional.of(GolemItemKind.CROOK));
    registry = mock(GolemRegistry.class);
    lenient().when(registry.loadedOf(any(UUID.class))).thenReturn(List.of());
    GolemNames names = mock(GolemNames.class);
    lenient().when(names.mode()).thenAnswer(invocation -> mode.get());
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
    runtime.when(GolemancyRuntime::names).thenReturn(Optional.of(names));
    runtime.when(() -> GolemancyRuntime.handle(any())).thenReturn(Optional.empty());
    vitals = mockStatic(GolemVitals.class);
    vitals.when(() -> GolemVitals.maxHealth(any())).thenReturn(100D);
    world = mock(WorldRef.class);
    lenient().when(world.uid()).thenReturn(WORLD);
    viewer = viewer(OWNER);
    overlay.when(() -> GlossOverlay.show(any(PlayerRef.class), any(LivingRef.class), anyList(), anyLong())).thenReturn(true);
    EntityInsight.attach(insight);
    service = new RevealService(RevealSettings::defaults);
    EntityInsight.register(service);
  }

  @AfterEach
  void tearDown() {
    overlay.close();
    EntityInsight.unregister(service);
    EntityInsight.detach(insight);
    ServantFrame.unregister(frame);
    vitals.close();
    runtime.close();
    items.close();
    displays.close();
    scheduling.close();
    time.close();
    AdaptPorts.uninstall(MarkupPort.class);
  }

  @Test
  void vanillaKeepsTheFloatingLabelButDropsTheNameTheNametagAlreadyShows() {
    golem(0D, 3D);

    service.update(viewer);

    assertThat(texts).hasSize(1);
    assertThat(plain(texts.getFirst())).doesNotContain("Clank").contains("100/100", "Guardian");
    overlay.verify(() -> GlossOverlay.show(any(PlayerRef.class), any(LivingRef.class), anyList(), anyLong()), never());
  }

  @Test
  void glossShowsNoLabelAndSendsTheLookedAtGolemsNameThenItsStatus() {
    mode.set(GolemNameMode.GLOSS);
    GolemHandle golem = golem(0D, 3D);
    LivingRef body = golem.entity();

    service.update(viewer);

    assertThat(spawned).isEmpty();
    ArgumentCaptor<List<String>> lines = linesCaptor();
    ArgumentCaptor<Long> duration = ArgumentCaptor.forClass(Long.class);
    overlay.verify(() -> GlossOverlay.show(eq(viewer), eq(body), lines.capture(), duration.capture()));
    assertThat(lines.getValue()).hasSize(3);
    assertThat(lines.getValue().getFirst()).contains("Clank");
    assertThat(String.join("\n", lines.getValue().subList(1, 3))).contains("100/100", "Guardian").doesNotContain("Clank");
    assertThat(duration.getValue()).isGreaterThan(RevealSettings.defaults().pollMillis());

    when(golem.entity().boundingBox()).thenReturn(box(6D, 65D, 3D, 0.7D, 1.35D, 0.7D));
    service.update(viewer);
    overlay.verify(() -> GlossOverlay.clear(OWNER));
  }

  @Test
  void glossInsightPutsTheNameFirstForAnyViewerAndTheStatusOnlyForTheOwner() {
    mode.set(GolemNameMode.GLOSS);
    GolemHandle golem = golem(0D, 3D);
    service.update(viewer);

    List<String> owned = EntityInsight.details(viewer, golem.entity());
    assertThat(owned).hasSize(3);
    assertThat(owned.getFirst()).contains("Clank");
    assertThat(String.join("\n", owned.subList(1, 3))).contains("100/100", "Guardian").doesNotContain("Clank");

    List<String> seen = EntityInsight.details(viewer(STRANGER), golem.entity());
    assertThat(seen).hasSize(1);
    assertThat(seen.getFirst()).contains("Clank");
  }

  @Test
  void glossInsightKeepsTheNameWhenTheOwnerHidesStatusLabels() {
    mode.set(GolemNameMode.GLOSS);
    GolemLabelPolicy labels = mock(GolemLabelPolicy.class);
    when(labels.hidesLabels(viewer)).thenReturn(true);
    runtime.when(GolemancyRuntime::labelPolicy).thenReturn(Optional.of(labels));
    GolemHandle golem = golem(0D, 3D);
    service.update(viewer);

    List<String> owned = EntityInsight.details(viewer, golem.entity());

    assertThat(owned).hasSize(1);
    assertThat(owned.getFirst()).contains("Clank");
  }

  @Test
  void vanillaInsightCarriesOnlyTheOwnersStatusBecauseTheNametagHoldsTheName() {
    GolemHandle golem = golem(0D, 3D);
    service.update(viewer);

    List<String> owned = EntityInsight.details(viewer, golem.entity());

    assertThat(owned).hasSize(2);
    assertThat(String.join("\n", owned)).contains("100/100", "Guardian").doesNotContain("Clank");
    assertThat(EntityInsight.details(viewer(STRANGER), golem.entity())).isEmpty();
  }

  @Test
  void glossLeavesTheStatusToAnInsightReadoutThatIsAlreadyUp() {
    mode.set(GolemNameMode.GLOSS);
    when(insight.showing(OWNER)).thenReturn(true);
    golem(0D, 3D);

    service.update(viewer);

    overlay.verify(() -> GlossOverlay.show(any(PlayerRef.class), any(LivingRef.class), anyList(), anyLong()), never());
    overlay.verify(() -> GlossOverlay.clear(any(UUID.class)), never());
  }

  @Test
  void glossSendsAServantsStatusWithItsName() {
    mode.set(GolemNameMode.GLOSS);
    MobRef servant = servant();

    service.update(viewer);

    assertThat(spawned).isEmpty();
    ArgumentCaptor<List<String>> lines = linesCaptor();
    overlay.verify(() -> GlossOverlay.show(eq(viewer), eq(servant), lines.capture(), anyLong()));
    assertThat(lines.getValue()).hasSize(3);
    assertThat(lines.getValue().getFirst()).contains("Skeletal Servant");
    assertThat(EntityInsight.details(viewer, servant)).isEqualTo(lines.getValue());
  }

  @Test
  void hiddenStatusLabelsHideTheStatusInEveryMode() {
    GolemLabelPolicy labels = mock(GolemLabelPolicy.class);
    when(labels.hidesLabels(viewer)).thenReturn(true);
    runtime.when(GolemancyRuntime::labelPolicy).thenReturn(Optional.of(labels));
    GolemHandle golem = golem(0D, 3D);

    for (GolemNameMode next : GolemNameMode.values()) {
      mode.set(next);
      service.update(viewer);
    }

    assertThat(spawned).isEmpty();
    overlay.verify(() -> GlossOverlay.show(any(PlayerRef.class), any(LivingRef.class), anyList(), anyLong()), never());
    assertThat(EntityInsight.details(viewer, golem.entity())).isEmpty();
    mode.set(GolemNameMode.GLOSS);
    assertThat(EntityInsight.details(viewer, golem.entity())).hasSize(1);
  }

  @Test
  void aModeChangeToGlossRemovesTheLabelWhenViewersAreRefreshed() {
    golem(0D, 3D);
    service.update(viewer);
    TextDisplayRef label = spawned.getFirst();

    mode.set(GolemNameMode.GLOSS);
    when(core.getPlatform().host().player(OWNER)).thenReturn(viewer);
    service.refreshViewers();

    displays.verify(() -> HiddenDisplays.hide(viewer, label));
    verify(label).remove();
  }

  private GolemHandle golem(double x, double z) {
    MobRef golem = mock(MobRef.class);
    UUID entityId = UUID.randomUUID();
    lenient().when(golem.uuid()).thenReturn(entityId);
    lenient().when(golem.isValid()).thenReturn(true);
    lenient().when(golem.world()).thenReturn(world);
    lenient().when(golem.location()).thenAnswer(invocation -> new Loc(world, x, 64D, z));
    lenient().when(golem.boundingBox()).thenReturn(box(x, 65.35D, z, 0.7D, 1.35D, 0.7D));
    lenient().when(golem.addPassenger(any(EntityRef.class))).thenReturn(true);
    lenient().when(golem.health()).thenReturn(100D);
    GolemState state = namedState("Clank");
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.workStatus()).thenReturn(GolemWorkStatus.WAITING);
    lenient().when(handle.entityId()).thenReturn(entityId);
    lenient().when(handle.entity()).thenReturn(golem);
    lenient().when(handle.owner()).thenReturn(OWNER);
    lenient().when(handle.state()).thenReturn(state);
    when(registry.loadedOf(OWNER)).thenReturn(List.of(handle));
    runtime.when(() -> GolemancyRuntime.handle(golem)).thenReturn(Optional.of(handle));
    return handle;
  }

  private static GolemState namedState(String name) {
    GolemState created = GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, GolemBody.IRON, GolemCoreType.GUARDIAN,
        GolemAnchor.of(WORLD, 0, 64, 0)));
    JsonObject json = new Gson().fromJson(created.toJson(), JsonObject.class);
    json.addProperty("customName", GolemSnapshot.textName(name));
    EntityRef carrier = mock(EntityRef.class);
    DataHolder data = mock(DataHolder.class);
    when(carrier.data()).thenReturn(data);
    when(data.get(GolemState.KEY)).thenReturn(json.toString());
    return GolemState.read(carrier).orElseThrow();
  }

  private MobRef servant() {
    MobRef servant = mock(MobRef.class);
    lenient().when(servant.uuid()).thenReturn(UUID.randomUUID());
    lenient().when(servant.isValid()).thenReturn(true);
    lenient().when(servant.world()).thenReturn(world);
    lenient().when(servant.location()).thenAnswer(invocation -> new Loc(world, 0D, 64D, 4D));
    lenient().when(servant.boundingBox()).thenReturn(box(0D, 65D, 4D, 0.3D, 1D, 0.3D));
    lenient().when(servant.addPassenger(any(EntityRef.class))).thenReturn(true);
    lenient().when(servant.health()).thenReturn(12D);
    pack.add(servant);
    lenient().when(frame.level(OWNER)).thenReturn(1);
    lenient().when(frame.labelRange()).thenReturn(8D);
    lenient().when(frame.servants(OWNER)).thenAnswer(invocation -> List.copyOf(pack));
    lenient().when(frame.expiresAt(servant)).thenReturn(CLOCK + 60_000L);
    ServantFrame.register(frame);
    return servant;
  }

  private PlayerRef viewer(UUID id) {
    PlayerRef player = mock(PlayerRef.class);
    PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    lenient().when(inventory.mainHand()).thenReturn(mock(ItemRef.class));
    lenient().when(inventory.offHand()).thenReturn(mock(ItemRef.class));
    lenient().when(player.uuid()).thenReturn(id);
    lenient().when(player.isOnline()).thenReturn(true);
    lenient().when(player.inventory()).thenReturn(inventory);
    lenient().when(player.location()).thenAnswer(invocation -> new Loc(world, 0D, 64D, 0D));
    lenient().when(player.eyeLocation()).thenAnswer(invocation -> new Loc(world, 0D, 65.6D, 0D));
    lenient().when(player.world()).thenReturn(world);
    return player;
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

  private static String plain(String markup) {
    return MarkupPort.current().plain(markup);
  }

  private static Box box(double x, double y, double z, double halfX, double halfY, double halfZ) {
    return new Box(x - halfX, y - halfY, z - halfZ, x + halfX, y + halfY, z + halfZ);
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<List<String>> linesCaptor() {
    return ArgumentCaptor.forClass((Class<List<String>>) (Class<?>) List.class);
  }
}
