package art.arcane.adapt.api.golem.reveal;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.fx.display.HiddenDisplays;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemWorkStatus;
import art.arcane.adapt.api.golem.GolemNameMode;
import art.arcane.adapt.api.golem.GolemNames;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemSnapshot;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemVitals;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.ServantFrame;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.insight.EntityInsight;
import art.arcane.adapt.api.insight.GlossInsight;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.math.M;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
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
import static org.mockito.ArgumentMatchers.eq;
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

  private final List<TextDisplay> spawned = new ArrayList<>();
  private final List<Component> texts = new ArrayList<>();
  private final AtomicReference<GolemNameMode> mode = new AtomicReference<>(GolemNameMode.VANILLA);
  private final EntityInsight.Display insight = mock(EntityInsight.Display.class);
  private final GlossInsight gloss = mock(GlossInsight.class);
  private final ServantFrame.Frame frame = mock(ServantFrame.Frame.class);
  private final List<Skeleton> pack = new ArrayList<>();
  private MockedStatic<M> time;
  private MockedStatic<J> scheduling;
  private MockedStatic<HiddenDisplays> displays;
  private MockedStatic<GolemItems> items;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<GolemVitals> vitals;
  private World world;
  private Player viewer;
  private GolemRegistry registry;
  private RevealService service;

  @BeforeEach
  void setUp() {
    time = mockStatic(M.class);
    time.when(M::ms).thenReturn(CLOCK);
    scheduling = mockStatic(J.class);
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class))).thenAnswer(invocation -> run(invocation.getArgument(1)));
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class), anyInt())).thenAnswer(invocation -> run(invocation.getArgument(1)));
    scheduling.when(J::isFoliaThreading).thenReturn(false);
    scheduling.when(() -> J.isOwnedByCurrentRegion(any(Entity.class))).thenReturn(true);
    displays = mockStatic(HiddenDisplays.class);
    displays.when(() -> HiddenDisplays.spawnLabel(any(Location.class), any(Component.class), any(Color.class), anyFloat()))
        .thenAnswer(invocation -> spawnDisplay(invocation.getArgument(1)));
    items = mockStatic(GolemItems.class);
    items.when(() -> GolemItems.kindOf(any(ItemStack.class))).thenReturn(Optional.of(GolemItemKind.CROOK));
    registry = mock(GolemRegistry.class);
    lenient().when(registry.loadedOf(any(UUID.class))).thenReturn(List.of());
    GolemNames names = mock(GolemNames.class);
    lenient().when(names.mode()).thenAnswer(invocation -> mode.get());
    lenient().when(names.gloss()).thenReturn(gloss);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
    runtime.when(GolemancyRuntime::names).thenReturn(Optional.of(names));
    runtime.when(() -> GolemancyRuntime.handle(any())).thenReturn(Optional.empty());
    vitals = mockStatic(GolemVitals.class);
    vitals.when(() -> GolemVitals.maxHealth(any())).thenReturn(100D);
    world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(WORLD);
    viewer = viewer(OWNER);
    lenient().when(plugin.isEnabled()).thenReturn(true);
    lenient().when(gloss.update(any(Player.class), any(LivingEntity.class), anyList(), anyLong())).thenReturn(true);
    EntityInsight.attach(insight);
    service = new RevealService(RevealSettings::defaults);
    EntityInsight.register(service);
  }

  @AfterEach
  void tearDown() {
    EntityInsight.unregister(service);
    EntityInsight.detach(insight);
    ServantFrame.unregister(frame);
    vitals.close();
    runtime.close();
    items.close();
    displays.close();
    scheduling.close();
    time.close();
  }

  @Test
  void vanillaKeepsTheFloatingLabelButDropsTheNameTheNametagAlreadyShows() {
    golem(0D, 3D);

    service.update(viewer);

    assertThat(texts).hasSize(1);
    assertThat(plain(texts.getFirst())).doesNotContain("Clank").contains("100/100", "Guardian");
    verify(gloss, never()).update(any(Player.class), any(LivingEntity.class), anyList(), anyLong());
  }

  @Test
  void glossShowsNoLabelAndSendsTheLookedAtGolemsNameThenItsStatus() {
    mode.set(GolemNameMode.GLOSS);
    GolemHandle golem = golem(0D, 3D);
    LivingEntity body = golem.entity();

    service.update(viewer);

    assertThat(spawned).isEmpty();
    ArgumentCaptor<List<String>> lines = linesCaptor();
    ArgumentCaptor<Long> duration = ArgumentCaptor.forClass(Long.class);
    verify(gloss).update(eq(viewer), eq(body), lines.capture(), duration.capture());
    assertThat(lines.getValue()).hasSize(3);
    assertThat(lines.getValue().getFirst()).contains("Clank");
    assertThat(String.join("\n", lines.getValue().subList(1, 3))).contains("100/100", "Guardian").doesNotContain("Clank");
    assertThat(duration.getValue()).isGreaterThan(RevealSettings.defaults().pollMillis());

    when(golem.entity().getBoundingBox()).thenReturn(BoundingBox.of(new Location(world, 6D, 65D, 3D), 0.7D, 1.35D, 0.7D));
    service.update(viewer);
    verify(gloss).clear(OWNER);
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

    verify(gloss, never()).update(any(Player.class), any(LivingEntity.class), anyList(), anyLong());
    verify(gloss, never()).clear(any(UUID.class));
  }

  @Test
  void glossSendsAServantsStatusWithItsName() {
    mode.set(GolemNameMode.GLOSS);
    Skeleton servant = servant();

    service.update(viewer);

    assertThat(spawned).isEmpty();
    ArgumentCaptor<List<String>> lines = linesCaptor();
    verify(gloss).update(eq(viewer), eq(servant), lines.capture(), anyLong());
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
    verify(gloss, never()).update(any(Player.class), any(LivingEntity.class), anyList(), anyLong());
    assertThat(EntityInsight.details(viewer, golem.entity())).isEmpty();
    mode.set(GolemNameMode.GLOSS);
    assertThat(EntityInsight.details(viewer, golem.entity())).hasSize(1);
  }

  @Test
  void aModeChangeToGlossRemovesTheLabelWhenViewersAreRefreshed() {
    golem(0D, 3D);
    service.update(viewer);
    TextDisplay label = spawned.getFirst();

    mode.set(GolemNameMode.GLOSS);
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.getPlayer(OWNER)).thenReturn(viewer);
      service.refreshViewers();
    }

    displays.verify(() -> HiddenDisplays.hide(viewer, label));
    verify(label).remove();
  }

  private GolemHandle golem(double x, double z) {
    IronGolem golem = mock(IronGolem.class);
    UUID entityId = UUID.randomUUID();
    lenient().when(golem.getUniqueId()).thenReturn(entityId);
    lenient().when(golem.isValid()).thenReturn(true);
    lenient().when(golem.getWorld()).thenReturn(world);
    lenient().when(golem.getLocation()).thenAnswer(invocation -> new Location(world, x, 64D, z));
    lenient().when(golem.getBoundingBox()).thenReturn(BoundingBox.of(new Location(world, x, 65.35D, z), 0.7D, 1.35D, 0.7D));
    lenient().when(golem.addPassenger(any(Entity.class))).thenReturn(true);
    lenient().when(golem.getHealth()).thenReturn(100D);
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
        GolemState.Anchor.of(WORLD, 0, 64, 0)));
    JsonObject json = new Gson().fromJson(created.toJson(), JsonObject.class);
    json.addProperty("customName", GolemSnapshot.serializeName(Component.text(name)));
    Entity carrier = mock(Entity.class);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    when(carrier.getPersistentDataContainer()).thenReturn(data);
    when(data.get(GolemState.KEY, PersistentDataType.STRING)).thenReturn(json.toString());
    return GolemState.read(carrier).orElseThrow();
  }

  private Skeleton servant() {
    Skeleton servant = mock(Skeleton.class);
    lenient().when(servant.getUniqueId()).thenReturn(UUID.randomUUID());
    lenient().when(servant.isValid()).thenReturn(true);
    lenient().when(servant.getWorld()).thenReturn(world);
    lenient().when(servant.getLocation()).thenAnswer(invocation -> new Location(world, 0D, 64D, 4D));
    lenient().when(servant.getBoundingBox()).thenReturn(BoundingBox.of(new Location(world, 0D, 65D, 4D), 0.3D, 1D, 0.3D));
    lenient().when(servant.addPassenger(any(Entity.class))).thenReturn(true);
    lenient().when(servant.getHealth()).thenReturn(12D);
    pack.add(servant);
    lenient().when(frame.level(OWNER)).thenReturn(1);
    lenient().when(frame.labelRange()).thenReturn(8D);
    lenient().when(frame.servants(OWNER)).thenAnswer(invocation -> List.copyOf(pack));
    lenient().when(frame.expiresAt(servant)).thenReturn(CLOCK + 60_000L);
    ServantFrame.register(frame);
    return servant;
  }

  private Player viewer(UUID id) {
    Player player = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    lenient().when(inventory.getItemInMainHand()).thenReturn(mock(ItemStack.class));
    lenient().when(inventory.getItemInOffHand()).thenReturn(mock(ItemStack.class));
    lenient().when(player.getUniqueId()).thenReturn(id);
    lenient().when(player.isOnline()).thenReturn(true);
    lenient().when(player.getInventory()).thenReturn(inventory);
    lenient().when(player.getLocation()).thenAnswer(invocation -> new Location(world, 0D, 64D, 0D));
    lenient().when(player.getEyeLocation()).thenAnswer(invocation -> new Location(world, 0D, 65.6D, 0D));
    lenient().when(player.getWorld()).thenReturn(world);
    return player;
  }

  private boolean run(Runnable task) {
    task.run();
    return true;
  }

  private TextDisplay spawnDisplay(Component text) {
    TextDisplay display = mock(TextDisplay.class);
    AtomicBoolean valid = new AtomicBoolean(true);
    lenient().when(display.isValid()).thenAnswer(invocation -> valid.get());
    lenient().doAnswer(invocation -> {
      valid.set(false);
      return null;
    }).when(display).remove();
    lenient().when(display.getUniqueId()).thenReturn(UUID.randomUUID());
    lenient().when(display.getTransformation())
        .thenReturn(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1F, 1F, 1F), new Quaternionf()));
    spawned.add(display);
    texts.add(text);
    return display;
  }

  private static String plain(Component component) {
    return PlainTextComponentSerializer.plainText().serialize(component);
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<List<String>> linesCaptor() {
    return ArgumentCaptor.forClass((Class<List<String>>) (Class<?>) List.class);
  }
}
