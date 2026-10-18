package art.arcane.adapt.api.golem.reveal;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.fx.display.HiddenDisplays;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemVitals;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.util.common.scheduling.J;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RevealServiceTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

  private final List<TextDisplay> spawned = new ArrayList<>();
  private final AtomicBoolean schedulerAccepts = new AtomicBoolean(true);
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
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class))).thenAnswer(invocation -> runIfAccepted(invocation.getArgument(1)));
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class), anyInt())).thenAnswer(invocation -> runIfAccepted(invocation.getArgument(1)));
    scheduling.when(J::isFoliaThreading).thenReturn(false);
    scheduling.when(() -> J.isOwnedByCurrentRegion(any(Entity.class))).thenReturn(true);
    displays = mockStatic(HiddenDisplays.class);
    displays.when(() -> HiddenDisplays.spawnLabel(any(Location.class), any(Component.class), any(Color.class), anyFloat()))
        .thenAnswer(invocation -> spawnDisplay());
    items = mockStatic(GolemItems.class);
    items.when(() -> GolemItems.kindOf(any(ItemStack.class))).thenReturn(Optional.of(GolemItemKind.CROOK));
    registry = mock(GolemRegistry.class);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
    vitals = mockStatic(GolemVitals.class);
    vitals.when(() -> GolemVitals.maxHealth(any())).thenReturn(100D);
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
    lenient().when(plugin.isEnabled()).thenReturn(true);
  }

  @AfterEach
  void tearDown() {
    vitals.close();
    runtime.close();
    items.close();
    displays.close();
    scheduling.close();
  }

  @Test
  void shutdownRemovesLabelsEvenAfterThePluginIsDisabled() {
    RevealService service = new RevealService(RevealSettings::defaults);
    AtomicReference<Double> distance = new AtomicReference<>(5D);
    GolemHandle golem = golem(distance);
    when(registry.loadedOf(OWNER)).thenReturn(List.of(golem));
    service.update(viewer);
    assertThat(spawned).hasSize(1);
    TextDisplay label = spawned.getFirst();
    displays.verify(() -> HiddenDisplays.show(viewer, label));

    when(plugin.isEnabled()).thenReturn(false);
    schedulerAccepts.set(false);
    service.shutdown();

    verify(label).remove();
  }

  @Test
  void aShownGolemWhoseLabelVanishedGetsANewOneOnTheNextPoll() {
    RevealService service = new RevealService(RevealSettings::defaults);
    AtomicReference<Double> distance = new AtomicReference<>(5D);
    GolemHandle golem = golem(distance);
    when(registry.loadedOf(OWNER)).thenReturn(List.of(golem));
    service.update(viewer);
    TextDisplay first = spawned.getFirst();
    when(first.isValid()).thenReturn(false);

    service.update(viewer);

    assertThat(spawned).hasSize(2);
    displays.verify(() -> HiddenDisplays.show(viewer, spawned.get(1)));
  }

  @Test
  void golemsLeaveTwoBlocksPastTheRevealRangeAndReenterOnlyInsideIt() {
    RevealService service = new RevealService(RevealSettings::defaults);
    AtomicReference<Double> distance = new AtomicReference<>(31D);
    GolemHandle golem = golem(distance);
    when(registry.loadedOf(OWNER)).thenReturn(List.of(golem));
    service.update(viewer);
    TextDisplay label = spawned.getFirst();

    distance.set(33.5D);
    service.update(viewer);
    displays.verify(() -> HiddenDisplays.hide(viewer, label), never());
    verify(label, never()).remove();

    distance.set(34.5D);
    service.update(viewer);
    displays.verify(() -> HiddenDisplays.hide(viewer, label));
    verify(label).remove();

    distance.set(33D);
    service.update(viewer);
    assertThat(spawned).hasSize(1);

    distance.set(32D);
    service.update(viewer);
    assertThat(spawned).hasSize(2);
  }

  private boolean runIfAccepted(Runnable task) {
    if (!schedulerAccepts.get()) {
      return false;
    }
    task.run();
    return true;
  }

  private TextDisplay spawnDisplay() {
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
    return display;
  }

  private GolemHandle golem(AtomicReference<Double> distance) {
    IronGolem golem = mock(IronGolem.class);
    UUID entityId = UUID.randomUUID();
    lenient().when(golem.getUniqueId()).thenReturn(entityId);
    lenient().when(golem.isValid()).thenReturn(true);
    lenient().when(golem.getWorld()).thenReturn(world);
    lenient().when(golem.getLocation()).thenAnswer(invocation -> new Location(world, distance.get(), 64D, 0D));
    lenient().when(golem.addPassenger(any(Entity.class))).thenReturn(true);
    lenient().when(golem.getHealth()).thenReturn(100D);
    GolemState state = GolemState.create(new GolemState.Seed(UUID.randomUUID(), OWNER, GolemBody.IRON, GolemCoreType.GUARDIAN,
        GolemState.Anchor.of(WORLD, 0, 64, 0)));
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.entityId()).thenReturn(entityId);
    lenient().when(handle.entity()).thenReturn(golem);
    lenient().when(handle.owner()).thenReturn(OWNER);
    lenient().when(handle.state()).thenReturn(state);
    lenient().when(handle.isRetired()).thenReturn(false);
    return handle;
  }
}
