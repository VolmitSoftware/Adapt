package art.arcane.adapt.api.golem.reveal;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.fx.ViewerGlowCoordinator;
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
import art.arcane.adapt.util.common.scheduling.J;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RevealServiceServantsTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
  private static final int SERVANT_RUNTIME_ID = 4_101;
  private static final int GOLEM_RUNTIME_ID = 4_202;

  private final List<TextDisplay> spawned = new ArrayList<>();
  private final List<Component> texts = new ArrayList<>();
  private final List<Skeleton> pack = new ArrayList<>();
  private final AtomicReference<Integer> frameLevel = new AtomicReference<>(1);
  private final AtomicReference<Integer> channels = new AtomicReference<>(ChannelMask.of(DyeColor.RED));
  private final ServantFrame.Frame frame = mock(ServantFrame.Frame.class);
  private final ViewerGlowCoordinator glow = mock(ViewerGlowCoordinator.class);
  private MockedStatic<J> scheduling;
  private MockedStatic<HiddenDisplays> displays;
  private MockedStatic<GolemItems> items;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<GolemVitals> vitals;
  private World world;
  private Player viewer;
  private GolemRegistry registry;

  @BeforeEach
  void setUp() {
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
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
    vitals = mockStatic(GolemVitals.class);
    vitals.when(() -> GolemVitals.maxHealth(any())).thenReturn(24D);
    world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(WORLD);
    viewer = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    lenient().when(inventory.getItemInMainHand()).thenReturn(mock(ItemStack.class));
    lenient().when(inventory.getItemInOffHand()).thenReturn(mock(ItemStack.class));
    lenient().when(viewer.getUniqueId()).thenReturn(OWNER);
    lenient().when(viewer.isOnline()).thenReturn(true);
    lenient().when(viewer.getInventory()).thenReturn(inventory);
    lenient().when(viewer.getLocation()).thenAnswer(invocation -> new Location(world, 0D, 64D, 0D));
    lenient().when(viewer.getWorld()).thenReturn(world);
    lenient().when(plugin.isEnabled()).thenReturn(true);
    lenient().when(plugin.getViewerGlowCoordinator()).thenReturn(glow);
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
  }

  @Test
  void aServantInsideTheLabelRangeGetsAnOwnerLabelAndTheChannelGlow() {
    Skeleton servant = servant(new AtomicReference<>(5D));
    RevealService service = new RevealService(RevealSettings::defaults);

    service.update(viewer);

    assertThat(spawned).hasSize(1);
    displays.verify(() -> HiddenDisplays.show(viewer, spawned.getFirst()));
    verify(servant).addPassenger(spawned.getFirst());
    verify(glow).set(ViewerGlowCoordinator.Layer.GOLEMANCY, servant, viewer, ChatColor.RED);
  }

  @Test
  void theServantLabelShowsItsNameHealthTimeLeftAndChannel() {
    Skeleton servant = servant(new AtomicReference<>(5D));
    when(frame.expiresAt(servant)).thenReturn(System.currentTimeMillis() + 41_500L);
    RevealService service = new RevealService(RevealSettings::defaults);

    service.update(viewer);

    String plain = PlainTextComponentSerializer.plainText().serialize(texts.getFirst());
    assertThat(plain.lines().toList()).containsExactly("Skeletal Servant", "|||||||||| 12/24", "42s left · " + GolemLabel.SWATCH);
  }

  @Test
  void aServantBeyondTheLabelRangeOnlyGlows() {
    Skeleton servant = servant(new AtomicReference<>(20D));
    RevealService service = new RevealService(RevealSettings::defaults);

    service.update(viewer);

    assertThat(spawned).isEmpty();
    verify(glow).set(ViewerGlowCoordinator.Layer.GOLEMANCY, servant, viewer, ChatColor.RED);
  }

  @Test
  void servantsDropTheirLabelPastTheLabelMarginAndTheirGlowPastTheRevealMargin() {
    AtomicReference<Double> distance = new AtomicReference<>(5D);
    Skeleton servant = servant(distance);
    RevealService service = new RevealService(RevealSettings::defaults);
    service.update(viewer);
    TextDisplay label = spawned.getFirst();

    distance.set(9.5D);
    service.update(viewer);
    verify(label, never()).remove();

    distance.set(10.5D);
    service.update(viewer);
    displays.verify(() -> HiddenDisplays.hide(viewer, label));
    verify(label).remove();
    verify(glow, never()).unset(eq(ViewerGlowCoordinator.Layer.GOLEMANCY), any(UUID.class), anyInt(), eq(viewer));

    distance.set(34.5D);
    service.update(viewer);
    verify(glow).unset(ViewerGlowCoordinator.Layer.GOLEMANCY, servant.getUniqueId(), SERVANT_RUNTIME_ID, viewer);
  }

  @Test
  void aChannelChangeRecolorsTheGlowOnTheNextPoll() {
    Skeleton servant = servant(new AtomicReference<>(5D));
    RevealService service = new RevealService(RevealSettings::defaults);
    service.update(viewer);

    channels.set(ChannelMask.NONE);
    service.update(viewer);

    verify(glow).set(ViewerGlowCoordinator.Layer.GOLEMANCY, servant, viewer, ChatColor.WHITE);
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
    Skeleton servant = servant(new AtomicReference<>(5D));
    RevealService service = new RevealService(RevealSettings::defaults);
    service.update(viewer);
    TextDisplay label = spawned.getFirst();

    items.when(() -> GolemItems.kindOf(any(ItemStack.class))).thenReturn(Optional.empty());
    service.update(viewer);

    displays.verify(() -> HiddenDisplays.hide(viewer, label));
    verify(label).remove();
    verify(glow).unset(ViewerGlowCoordinator.Layer.GOLEMANCY, servant.getUniqueId(), SERVANT_RUNTIME_ID, viewer);
  }

  @Test
  void revealedGolemsGlowInTheirFirstChannelUntilTheyLeave() {
    AtomicReference<Double> distance = new AtomicReference<>(5D);
    GolemHandle golem = golem(distance, DyeColor.LIME);
    when(registry.loadedOf(OWNER)).thenReturn(List.of(golem));
    RevealService service = new RevealService(RevealSettings::defaults);

    service.update(viewer);
    verify(glow).set(ViewerGlowCoordinator.Layer.GOLEMANCY, golem.entity(), viewer, ChatColor.GREEN);

    distance.set(34.5D);
    service.update(viewer);
    verify(glow).unset(ViewerGlowCoordinator.Layer.GOLEMANCY, golem.entityId(), GOLEM_RUNTIME_ID, viewer);
  }

  private Skeleton servant(AtomicReference<Double> distance) {
    Skeleton servant = mock(Skeleton.class);
    UUID id = UUID.randomUUID();
    lenient().when(servant.getUniqueId()).thenReturn(id);
    lenient().when(servant.getEntityId()).thenReturn(SERVANT_RUNTIME_ID);
    lenient().when(servant.isValid()).thenReturn(true);
    lenient().when(servant.getWorld()).thenReturn(world);
    lenient().when(servant.getLocation()).thenAnswer(invocation -> new Location(world, distance.get(), 64D, 0D));
    lenient().when(servant.addPassenger(any(Entity.class))).thenReturn(true);
    lenient().when(servant.getHealth()).thenReturn(12D);
    lenient().when(frame.expiresAt(servant)).thenReturn(System.currentTimeMillis() + 60_000L);
    pack.add(servant);
    return servant;
  }

  private GolemHandle golem(AtomicReference<Double> distance, DyeColor channel) {
    IronGolem golem = mock(IronGolem.class);
    UUID entityId = UUID.randomUUID();
    lenient().when(golem.getUniqueId()).thenReturn(entityId);
    lenient().when(golem.getEntityId()).thenReturn(GOLEM_RUNTIME_ID);
    lenient().when(golem.isValid()).thenReturn(true);
    lenient().when(golem.getWorld()).thenReturn(world);
    lenient().when(golem.getLocation()).thenAnswer(invocation -> new Location(world, distance.get(), 64D, 0D));
    lenient().when(golem.addPassenger(any(Entity.class))).thenReturn(true);
    lenient().when(golem.getHealth()).thenReturn(100D);
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
}
