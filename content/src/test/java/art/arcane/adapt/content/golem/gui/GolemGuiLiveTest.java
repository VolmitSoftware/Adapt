package art.arcane.adapt.content.golem.gui;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.client.protocol.HintKind;
import art.arcane.adapt.client.protocol.PanelLayout;
import art.arcane.adapt.client.protocol.PanelTile;
import art.arcane.adapt.client.protocol.PanelView;
import art.arcane.adapt.client.protocol.TileRole;
import art.arcane.adapt.content.golem.GolemAnchor;
import art.arcane.adapt.content.golem.GolemBody;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemEquipmentSlot;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemMastery;
import art.arcane.adapt.content.golem.GolemMode;
import art.arcane.adapt.content.golem.GolemRegistry;
import art.arcane.adapt.content.golem.GolemRune;
import art.arcane.adapt.content.golem.GolemRuneEffects;
import art.arcane.adapt.content.golem.GolemRuneLayout;
import art.arcane.adapt.content.golem.GolemRuneType;
import art.arcane.adapt.content.golem.GolemRunes;
import art.arcane.adapt.content.golem.GolemState;
import art.arcane.adapt.content.golem.GolemTestItems;
import art.arcane.adapt.content.golem.GolemVitals;
import art.arcane.adapt.content.golem.GolemWorkStatus;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.item.GolemItemKind;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuIcon;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemGuiLiveTest extends AdaptTestBase {
  private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000e2");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000e3");
  private static final GolemRuneLayout LAYOUT = new GolemRuneLayout(2, List.of(1, 2), 3, List.of(1, 2, 3), Set.of());
  private static final GolemRuneLayout BONE_LAYOUT = new GolemRuneLayout(3, List.of(1, 1, 3, 5), 2, List.of(1, 3, 5), Set.of());
  private static final int RUNE_ROW = 1;
  private static final int CARRIED_ROW = 2;
  private static final int EQUIPMENT_ROW = 2;
  private static final int CARRIED_FIRST = -4;
  private static final int HEADER_ROW = 0;
  private static final int FOOTER_ROW = 4;

  private static final GolemMastery.Twins TWINS = new GolemMastery.Twins() {
    @Override
    public int maxTwins(UUID owner, GolemHandle handle) {
      return Integer.MAX_VALUE;
    }

    @Override
    public int freeTwins() {
      return 2;
    }

    @Override
    public double secondCoreSpeed() {
      return 0.75D;
    }
  };

  private final ManualScheduler scheduler = new ManualScheduler();
  private final AtomicReference<GolemBody> body = new AtomicReference<>(GolemBody.IRON);
  private final AtomicReference<Map<GolemEquipmentSlot, String>> equipment = new AtomicReference<>(Map.of());
  private final PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
  private final AtomicReference<List<String>> carried = new AtomicReference<>(List.of("cobblestone-12", "oak_log-4"));
  private final AtomicReference<List<GolemCoreType>> cores = new AtomicReference<>(List.of(GolemCoreType.BREAKER));
  private final AtomicReference<List<GolemRune>> runes = new AtomicReference<>(List.of());
  private final AtomicReference<GolemMode> mode = new AtomicReference<>(GolemMode.WORK);
  private final AtomicReference<Double> health = new AtomicReference<>(20D);
  private final AtomicInteger essence = new AtomicInteger();
  private final AtomicLong tasks = new AtomicLong(4L);
  private final AtomicBoolean combatOff = new AtomicBoolean();
  private final AtomicBoolean valid = new AtomicBoolean(true);
  private final AtomicBoolean retired = new AtomicBoolean();
  private final AtomicBoolean online = new AtomicBoolean(true);
  private final AtomicInteger stateReads = new AtomicInteger();
  private final Map<GolemWindow, AtomicBoolean> visibility = new IdentityHashMap<>();
  private final List<Consumer<GolemWindow>> closeCallbacks = new ArrayList<>();
  private MockedConstruction<GolemWindow> windows;
  private MockedStatic<GolemTheme> themes;
  private MockedStatic<AdaptApi> scheduling;
  private final AdaptScheduler adaptScheduler = mock(AdaptScheduler.class);
  private MockedStatic<GolemItems> items;
  private MockedStatic<GolemRunes> runeEffects;
  private MockedStatic<GolemVitals> vitals;
  private MockedStatic<HudStatus> hud;
  private MobRef golem;
  private PlayerRef player;
  private GolemHandle handle;
  private CrookGestures<GolemHandle> gestures;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    golem = mock(MobRef.class);
    lenient().when(golem.isValid()).thenAnswer(invocation -> valid.get());
    lenient().when(golem.health()).thenAnswer(invocation -> health.get());
    player = mock(PlayerRef.class);
    lenient().when(player.uuid()).thenReturn(VIEWER);
    lenient().when(player.isOnline()).thenAnswer(invocation -> online.get());
    GolemState.Stats stats = mock(GolemState.Stats.class);
    lenient().when(stats.tasks()).thenAnswer(invocation -> tasks.get());
    GolemState state = mock(GolemState.class);
    lenient().when(state.body()).thenAnswer(invocation -> body.get());
    lenient().when(state.cores()).thenAnswer(invocation -> cores.get());
    lenient().when(state.primaryCore()).thenAnswer(invocation -> cores.get().getFirst());
    lenient().when(state.runes()).thenAnswer(invocation -> runes.get());
    lenient().when(state.mode()).thenAnswer(invocation -> mode.get());
    lenient().when(state.combatOff()).thenAnswer(invocation -> combatOff.get());
    lenient().when(state.home()).thenReturn(GolemAnchor.of(WORLD, 0, 64, 0));
    lenient().when(state.carried()).thenAnswer(invocation -> carried.get());
    lenient().when(state.equipment()).thenAnswer(invocation -> equipment.get());
    lenient().when(player.inventory()).thenReturn(inventory);
    lenient().when(inventory.addItem(any(ItemRef.class))).thenReturn(List.of());
    ItemOps itemOps = mock(ItemOps.class);
    lenient().when(AdaptCore.platform().host().items()).thenReturn(itemOps);
    lenient().when(itemOps.empty()).thenAnswer(invocation -> FakeItem.empty());
    lenient().when(itemOps.decode(anyString())).thenAnswer(invocation -> stack(invocation.getArgument(0)));
    lenient().when(inventory.cursor()).thenAnswer(invocation -> FakeItem.empty());
    lenient().when(state.essencePoints()).thenAnswer(invocation -> essence.get());
    lenient().when(state.stats()).thenReturn(stats);
    handle = mock(GolemHandle.class);
    lenient().when(handle.workStatus()).thenReturn(GolemWorkStatus.WAITING);
    lenient().when(handle.entity()).thenReturn(golem);
    lenient().when(handle.owner()).thenReturn(OWNER);
    lenient().when(handle.id()).thenReturn(UUID.randomUUID());
    lenient().when(handle.isRetired()).thenAnswer(invocation -> retired.get());
    lenient().when(handle.state()).thenAnswer(invocation -> {
      scheduler.expect(golem, "golem state read");
      stateReads.incrementAndGet();
      return state;
    });
    doAnswer(invocation -> {
      mode.set(invocation.getArgument(0));
      return null;
    }).when(handle).setMode(any(GolemMode.class));
    lenient().doAnswer(invocation -> {
      scheduler.expect(golem, "combat change");
      combatOff.set(!invocation.<Boolean>getArgument(0));
      return true;
    }).when(handle).setCombat(anyBoolean());
    gestures = mock(CrookGestures.class);

    scheduling = mockStatic(AdaptApi.class, CALLS_REAL_METHODS);
    scheduling.when(AdaptApi::scheduler).thenReturn(adaptScheduler);
    scheduler.install(adaptScheduler);
    themes = mockStatic(GolemTheme.class, invocation -> "element".equals(invocation.getMethod().getName())
        ? new GolemElement(invocation.getArgument(0)).setMaterial(invocation.getArgument(1))
        : null);
    items = mockStatic(GolemItems.class);
    items.when(() -> GolemItems.core(any(GolemCoreType.class))).thenAnswer(invocation -> GolemTestItems.stack(Id.minecraft("paper"), 1));
    items.when(() -> GolemItems.rune(any(GolemRune.class))).thenAnswer(invocation -> {
      return GolemTestItems.stack(Id.minecraft("amethyst_shard"), 1);
    });
    runeEffects = mockStatic(GolemRunes.class);
    runeEffects.when(() -> GolemRunes.effects(any(GolemHandle.class))).thenReturn(GolemRuneEffects.NONE);
    runeEffects.when(() -> GolemRunes.layout(any(GolemBody.class), any(UUID.class), anyBoolean())).thenReturn(LAYOUT);
    runeEffects.when(() -> GolemRunes.layout(eq(GolemBody.BONE), any(UUID.class), anyBoolean())).thenReturn(BONE_LAYOUT);
    vitals = mockStatic(GolemVitals.class);
    vitals.when(() -> GolemVitals.maxHealth(any())).thenReturn(20D);
    hud = mockStatic(HudStatus.class);
    windows = mockConstruction(GolemWindow.class, (window, context) -> {
      AtomicBoolean visible = new AtomicBoolean(true);
      visibility.put(window, visible);
      lenient().when(window.isVisible()).thenAnswer(invocation -> visible.get());
      lenient().doAnswer(invocation -> {
        scheduler.expect(player, "window close");
        visible.set(false);
        return null;
      }).when(window).close();
      lenient().when(window.open()).thenAnswer(invocation -> {
        scheduler.expect(player, "window open");
        return window;
      });
      lenient().when(window.updateElement(anyInt(), anyInt(), any())).thenAnswer(invocation -> {
        scheduler.expect(player, "window update");
        return window;
      });
      lenient().when(window.onClosed(any())).thenAnswer(invocation -> {
        closeCallbacks.add(invocation.getArgument(0));
        return window;
      });
    });
  }

  @AfterEach
  void tearDown() {
    windows.close();
    hud.close();
    vitals.close();
    runeEffects.close();
    items.close();
    themes.close();
    scheduling.close();
  }

  @Test
  void theFirstDrawPlacesEveryElementOfTheMainPage() {
    openAndDraw();
    GolemWindow window = onlyWindow();

    verify(window).setElement(eq(-4), eq(HEADER_ROW), any(GolemElement.class));
    verify(window).setElement(eq(-3), eq(HEADER_ROW), any(GolemElement.class));
    verify(window).setElement(eq(0), eq(HEADER_ROW), any(GolemElement.class));
    verify(window).setElement(eq(CARRIED_FIRST), eq(CARRIED_ROW), any(GolemElement.class));
    verify(window).setElement(eq(CARRIED_FIRST + 1), eq(CARRIED_ROW), any(GolemElement.class));
    verify(window, never()).setElement(eq(CARRIED_FIRST + 2), eq(CARRIED_ROW), any(GolemElement.class));
    verify(window).setElement(eq(-4), eq(FOOTER_ROW), any(GolemElement.class));
    verify(window).setElement(eq(4), eq(FOOTER_ROW), any(GolemElement.class));
    verify(window, never()).updateElement(anyInt(), anyInt(), any());
  }

  @Test
  void aCarriedCountThatGrowsUpdatesOnlyThatSlotOfTheOpenWindow() {
    openAndDraw();
    GolemWindow window = onlyWindow();
    carried.set(List.of("cobblestone-13", "oak_log-4"));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(CARRIED_FIRST), eq(CARRIED_ROW), any(GolemElement.class));
    assertThat(scheduler.violations()).isEmpty();
  }

  @Test
  void aCarriedChangeNeverReopensTheWindowOrBuildsANewOne() {
    openAndDraw();
    GolemWindow window = onlyWindow();
    carried.set(List.of("cobblestone-13", "oak_log-4"));

    scheduler.advance(LiveRefresh.POLL_TICKS * 3);

    assertThat(windows.constructed()).hasSize(1);
    verify(window, times(1)).open();
    verify(window, never()).close();
  }

  @Test
  void aNewCarriedStackFillsTheNextSlot() {
    openAndDraw();
    GolemWindow window = onlyWindow();
    carried.set(List.of("cobblestone-12", "oak_log-4", "dirt-2"));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(CARRIED_FIRST + 2), eq(CARRIED_ROW), any(GolemElement.class));
  }

  @Test
  void aTakenCarriedStackClearsItsSlot() {
    openAndDraw();
    GolemWindow window = onlyWindow();
    carried.set(List.of("cobblestone-12"));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(CARRIED_FIRST + 1), eq(CARRIED_ROW), eq(null));
  }

  @Test
  void carriedPastTheShownSlotsAddsTheMoreCarriedNote() {
    openAndDraw();
    GolemWindow window = onlyWindow();
    List<String> many = new ArrayList<>();
    for (int index = 0; index < 20; index++) {
      many.add("dirt-" + (index + 1));
    }
    carried.set(many);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    ArgumentCaptor<GolemElement> note = ArgumentCaptor.forClass(GolemElement.class);
    verify(window).updateElement(eq(CARRIED_FIRST + 8), eq(CARRIED_ROW + 1), note.capture());
    assertThat(note.getValue().getId()).isEqualTo("golem-carried-more");
  }

  @Test
  void healthThatChangesTheShownValueUpdatesTheBodyElement() {
    openAndDraw();
    GolemWindow window = onlyWindow();
    health.set(18.2D);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(-4), eq(HEADER_ROW), any(GolemElement.class));
  }

  @Test
  void healthThatKeepsTheShownValueUpdatesNothing() {
    openAndDraw();
    GolemWindow window = onlyWindow();
    health.set(19.4D);

    scheduler.advance(LiveRefresh.POLL_TICKS * 3);

    verify(window, never()).updateElement(anyInt(), anyInt(), any());
  }

  @Test
  void aModeChangeUpdatesTheModeAndWorkStatus() {
    openAndDraw();
    GolemWindow window = onlyWindow();
    mode.set(GolemMode.FOLLOW);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(2)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(-4), eq(HEADER_ROW), any(GolemElement.class));
    verify(window).updateElement(eq(0), eq(HEADER_ROW), any(GolemElement.class));
  }

  @Test
  void theMainPagePlacesTheCombatToggleNextToMode() {
    openAndDraw();

    verify(onlyWindow()).setElement(eq(-1), eq(HEADER_ROW), any(GolemElement.class));
  }

  @Test
  void clickingTheCombatToggleTurnsCombatOffOnTheGolemSchedulerAndUpdatesInPlace() {
    openAndDraw();
    GolemWindow window = onlyWindow();
    GolemElement toggle = placed(window, -1, HEADER_ROW);

    click(toggle, ClickKind.LEFT);
    scheduler.advance(0);

    assertThat(combatOff.get()).isTrue();
    verify(handle).setCombat(false);
    verify(window).updateElement(eq(-1), eq(HEADER_ROW), any(GolemElement.class));
    assertThat(windows.constructed()).hasSize(1);
    assertThat(scheduler.violations()).isEmpty();
  }

  @Test
  void clickingTheCombatToggleAgainTurnsCombatBackOn() {
    combatOff.set(true);
    openAndDraw();
    GolemWindow window = onlyWindow();
    GolemElement toggle = placed(window, -1, HEADER_ROW);

    click(toggle, ClickKind.LEFT);
    scheduler.advance(0);

    assertThat(combatOff.get()).isFalse();
    verify(handle).setCombat(true);
    assertThat(scheduler.violations()).isEmpty();
  }

  @Test
  void aCombatChangeFromElsewhereUpdatesTheToggleInPlace() {
    openAndDraw();
    GolemWindow window = onlyWindow();
    combatOff.set(true);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window).updateElement(eq(-1), eq(HEADER_ROW), any(GolemElement.class));
    assertThat(scheduler.violations()).isEmpty();
  }

  @Test
  void aGuardiansCombatToggleIsLockedAndIgnoresClicks() {
    cores.set(List.of(GolemCoreType.GUARDIAN));
    openAndDraw();
    GolemElement toggle = placed(onlyWindow(), -1, HEADER_ROW);

    click(toggle, ClickKind.LEFT);
    scheduler.advance(0);

    assertThat(combatOff.get()).isFalse();
    verify(handle, never()).setCombat(anyBoolean());
  }

  @Test
  void essenceUpdatesTheEssenceElement() {
    openAndDraw();
    GolemWindow window = onlyWindow();
    essence.set(7);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(2), eq(FOOTER_ROW), any(GolemElement.class));
  }

  @Test
  void theStatisticsLoreUpdatesTheStatsElement() {
    openAndDraw();
    GolemWindow window = onlyWindow();
    tasks.set(5L);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(-4), eq(FOOTER_ROW), any(GolemElement.class));
  }

  @Test
  void aSlottedCoreUpdatesTheCoreSlotsAndTheEssenceElement() {
    openAndDraw();
    GolemWindow window = onlyWindow();
    cores.set(List.of(GolemCoreType.BREAKER, GolemCoreType.GATHERER));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(3)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(-3), eq(HEADER_ROW), any(GolemElement.class));
    verify(window).updateElement(eq(-2), eq(HEADER_ROW), any(GolemElement.class));
    verify(window).updateElement(eq(2), eq(FOOTER_ROW), any(GolemElement.class));
  }

  @Test
  void aSlottedRuneUpdatesTheRuneRow() {
    openAndDraw();
    GolemWindow window = onlyWindow();
    runes.set(List.of(new GolemRune(GolemRuneType.MIGHT, 1)));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window).updateElement(eq(GolemLayout.centeredPosition(0, 2)), eq(1), any(GolemElement.class));
    verify(window, never()).updateElement(anyInt(), eq(CARRIED_ROW), any());
    verify(window, never()).updateElement(anyInt(), eq(HEADER_ROW), any());
  }

  @Test
  void aBoneGolemsRuneRowShowsItsOpenAndLockedSlots() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    openAndDraw();
    GolemWindow window = onlyWindow();

    verify(window, times(BONE_LAYOUT.maxSlots())).setElement(anyInt(), eq(RUNE_ROW), any(GolemElement.class));
    for (int index = 0; index < BONE_LAYOUT.maxSlots(); index++) {
      verify(window).setElement(eq(GolemLayout.centeredPosition(index, BONE_LAYOUT.maxSlots())), eq(RUNE_ROW), any(GolemElement.class));
    }
  }

  @Test
  void aRuneSlottedIntoABoneGolemUpdatesItsRuneRow() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    openAndDraw();
    GolemWindow window = onlyWindow();
    runes.set(List.of(new GolemRune(GolemRuneType.SWIFTNESS, 1)));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(BONE_LAYOUT.maxSlots())).updateElement(anyInt(), eq(RUNE_ROW), any(GolemElement.class));
    verify(window, times(BONE_LAYOUT.maxSlots())).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(GolemLayout.centeredPosition(0, BONE_LAYOUT.maxSlots())), eq(RUNE_ROW), any(GolemElement.class));
  }

  @Test
  void aBoneGolemShowsASixSlotEquipmentRowAndOtherBodiesDoNot() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    openAndDraw();
    GolemWindow bone = onlyWindow();

    ArgumentCaptor<GolemElement> placed = ArgumentCaptor.forClass(GolemElement.class);
    verify(bone, times(GolemEquipmentSlot.values().length)).setElement(anyInt(), eq(EQUIPMENT_ROW), placed.capture());
    assertThat(placed.getAllValues()).extracting(GolemElement::getId).containsExactly("golem-equipment-helmet", "golem-equipment-chestplate",
        "golem-equipment-leggings", "golem-equipment-boots", "golem-equipment-main_hand", "golem-equipment-off_hand");
    for (int index = 0; index < GolemEquipmentSlot.values().length; index++) {
      verify(bone).setElement(eq(GolemLayout.centeredPosition(index, GolemEquipmentSlot.values().length)), eq(EQUIPMENT_ROW), any(GolemElement.class));
    }
  }

  @Test
  void aNonBoneGolemHasNoEquipmentRow() {
    carried.set(List.of());
    equipment.set(Map.of(GolemEquipmentSlot.HELMET, "iron_helmet-1"));
    openAndDraw();

    verify(onlyWindow(), never()).setElement(anyInt(), eq(EQUIPMENT_ROW), any(GolemElement.class));
  }

  @Test
  void aWornItemUpdatesOnlyItsOwnSlot() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    openAndDraw();
    GolemWindow window = onlyWindow();
    equipment.set(Map.of(GolemEquipmentSlot.BOOTS, "iron_boots-1"));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(GolemLayout.centeredPosition(GolemEquipmentSlot.BOOTS.ordinal(), GolemEquipmentSlot.values().length)),
        eq(EQUIPMENT_ROW), any(GolemElement.class));
  }

  @Test
  void clickingAWornItemTwiceHandsItBackOnlyOnce() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    equipment.set(Map.of(GolemEquipmentSlot.BOOTS, "iron_boots-1"));
    ItemRef boots = GolemTestItems.stack(Id.minecraft("iron_boots"), 1);
    when(handle.unequip(GolemEquipmentSlot.BOOTS, "iron_boots-1")).thenReturn(Optional.of(boots)).thenReturn(Optional.empty());
    openAndDraw();
    GolemElement element = equipmentElement("golem-equipment-boots");

    click(element, ClickKind.LEFT);
    click(element, ClickKind.LEFT);
    scheduler.advance(0);

    verify(handle, times(2)).unequip(GolemEquipmentSlot.BOOTS, "iron_boots-1");
    verify(inventory, times(1)).addItem(any(ItemRef.class));
    assertThat(scheduler.violations()).isEmpty();
  }

  @Test
  void aShiftClickOnAWornItemTakesItBackOnce() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    equipment.set(Map.of(GolemEquipmentSlot.MAIN_HAND, "bow-1"));
    ItemRef bow = GolemTestItems.stack(Id.minecraft("bow"), 1);
    when(handle.unequip(GolemEquipmentSlot.MAIN_HAND, "bow-1")).thenReturn(Optional.of(bow)).thenReturn(Optional.empty());
    openAndDraw();
    GolemElement element = equipmentElement("golem-equipment-main_hand");

    click(element, ClickKind.SHIFT_LEFT);
    click(element, ClickKind.LEFT);
    scheduler.advance(0);

    verify(inventory, times(1)).addItem(any(ItemRef.class));
  }

  @Test
  void anItemFromTheCursorIsEquippedOneAtATimeIntoAnEmptySlot() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    ItemRef cursor = GolemTestItems.stack(Id.minecraft("iron_helmet"), 2);
    when(inventory.cursor()).thenReturn(cursor);
    when(handle.equip(eq(GolemEquipmentSlot.HELMET), any(ItemRef.class))).thenReturn(true);
    openAndDraw();
    GolemElement element = equipmentElement("golem-equipment-helmet");

    click(element, ClickKind.LEFT);
    scheduler.advance(0);

    ArgumentCaptor<ItemRef> equipped = ArgumentCaptor.forClass(ItemRef.class);
    verify(handle).equip(eq(GolemEquipmentSlot.HELMET), equipped.capture());
    assertThat(equipped.getValue().amount()).isEqualTo(1);
    assertThat(cursor.amount()).isEqualTo(1);
    verify(inventory, never()).addItem(any(ItemRef.class));
  }

  @Test
  void anItemThatDoesNotFitTheSlotStaysOnTheCursor() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    ItemRef cursor = GolemTestItems.stack(Id.minecraft("iron_boots"), 1);
    when(inventory.cursor()).thenReturn(cursor);
    openAndDraw();
    GolemElement element = equipmentElement("golem-equipment-helmet");

    click(element, ClickKind.LEFT);
    scheduler.advance(0);

    verify(handle, never()).equip(any(GolemEquipmentSlot.class), any(ItemRef.class));
    verify(inventory, never()).setCursor(any());
    assertThat(cursor.amount()).isEqualTo(1);
    hud.verify(() -> HudStatus.show(eq(player), anyString()), times(1));
  }

  @Test
  void anEmptyCursorOnAnEmptySlotAsksForAnItem() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    when(inventory.cursor()).thenReturn(FakeItem.empty());
    openAndDraw();
    GolemElement element = equipmentElement("golem-equipment-main_hand");

    click(element, ClickKind.LEFT);
    scheduler.advance(0);

    verify(handle, never()).equip(any(GolemEquipmentSlot.class), any(ItemRef.class));
    hud.verify(() -> HudStatus.show(eq(player), anyString()), times(1));
  }

  @Test
  void aRefusedEquipHandsTheTakenItemBackToThePlayer() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    ItemRef cursor = GolemTestItems.stack(Id.minecraft("shield"), 1);
    when(inventory.cursor()).thenReturn(cursor);
    when(handle.equip(any(GolemEquipmentSlot.class), any(ItemRef.class))).thenReturn(false);
    openAndDraw();
    GolemElement element = equipmentElement("golem-equipment-off_hand");

    click(element, ClickKind.LEFT);
    scheduler.advance(0);

    verify(inventory, times(1)).addItem(any(ItemRef.class));
  }

  @Test
  void aTwinCorePastTheFreeOnesTakesOneMasterworkHeartFromTheInventory() {
    ItemRef heart = GolemTestItems.stack(Id.minecraft("heart_of_the_sea"), 2);
    GolemRegistry registry = twinRegistry(heart);
    try (MockedStatic<GolemancyRuntime> runtime = mockStatic(GolemancyRuntime.class, CALLS_REAL_METHODS)) {
      runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
      GolemMastery.registerTwins(TWINS);
      try {
        openAndDraw();
        click(placed(onlyWindow(), -2, HEADER_ROW), ClickKind.LEFT);
        scheduler.advance(0);
      } finally {
        GolemMastery.unregisterTwins(TWINS);
      }
    }

    verify(registry).addCore(handle, GolemCoreType.LUMBER, true);
    assertThat(heart.amount()).isEqualTo(1);
    verify(inventory, never()).addItem(any(ItemRef.class));
  }

  @Test
  void aHeartTheRegistryDidNotSpendGoesBackToThePlayer() {
    ItemRef heart = GolemTestItems.stack(Id.minecraft("heart_of_the_sea"), 2);
    GolemRegistry registry = twinRegistry(heart);
    when(registry.addCore(handle, GolemCoreType.LUMBER, true)).thenReturn(new GolemRegistry.CoreSlotting(Optional.empty(), false));
    try (MockedStatic<GolemancyRuntime> runtime = mockStatic(GolemancyRuntime.class, CALLS_REAL_METHODS)) {
      runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
      GolemMastery.registerTwins(TWINS);
      try {
        openAndDraw();
        click(placed(onlyWindow(), -2, HEADER_ROW), ClickKind.LEFT);
        scheduler.advance(0);
      } finally {
        GolemMastery.unregisterTwins(TWINS);
      }
    }

    verify(inventory, times(1)).addItem(any(ItemRef.class));
  }

  @Test
  void aTwinCorePastTheFreeOnesWithoutAHeartStaysOnTheCursor() {
    ItemRef heart = GolemTestItems.stack(Id.minecraft("heart_of_the_sea"), 1);
    GolemRegistry registry = twinRegistry(heart);
    items.when(() -> GolemItems.kindOf(heart)).thenReturn(Optional.empty());
    try (MockedStatic<GolemancyRuntime> runtime = mockStatic(GolemancyRuntime.class, CALLS_REAL_METHODS)) {
      runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
      GolemMastery.registerTwins(TWINS);
      try {
        openAndDraw();
        click(placed(onlyWindow(), -2, HEADER_ROW), ClickKind.LEFT);
        scheduler.advance(0);
      } finally {
        GolemMastery.unregisterTwins(TWINS);
      }
    }

    verify(registry, never()).addCore(any(GolemHandle.class), any(GolemCoreType.class), anyBoolean());
    assertThat(inventory.cursor().amount()).isEqualTo(1);
    assertThat(heart.amount()).isEqualTo(1);
  }

  private GolemRegistry twinRegistry(ItemRef heart) {
    ItemRef core = GolemTestItems.stack(Id.minecraft("paper"), 1);
    when(inventory.cursor()).thenReturn(core);
    items.when(() -> GolemItems.coreOf(core)).thenReturn(Optional.of(GolemCoreType.LUMBER));
    items.when(() -> GolemItems.kindOf(heart)).thenReturn(Optional.of(GolemItemKind.MASTERWORK_HEART));
    lenient().when(inventory.size()).thenReturn(9);
    lenient().when(inventory.item(anyInt())).thenAnswer(invocation -> invocation.<Integer>getArgument(0) == 4 ? heart : FakeItem.empty());
    GolemRegistry registry = mock(GolemRegistry.class);
    lenient().when(registry.twinOpen(handle)).thenReturn(true);
    lenient().when(registry.twins(OWNER)).thenReturn(2);
    lenient().when(registry.addCore(handle, GolemCoreType.LUMBER, true)).thenReturn(new GolemRegistry.CoreSlotting(Optional.empty(), true));
    return registry;
  }

  @Test
  void aStateThatNeverChangesUpdatesNothingForever() {
    openAndDraw();
    GolemWindow window = onlyWindow();

    scheduler.advance(LiveRefresh.POLL_TICKS * 50);

    verify(window, never()).updateElement(anyInt(), anyInt(), any());
    assertThat(windows.constructed()).hasSize(1);
    assertThat(scheduler.pending()).isEqualTo(1);
  }

  @Test
  void aMutationFromTheWindowUpdatesInPlaceToo() {
    openAndDraw();
    GolemWindow window = onlyWindow();
    mode.set(GolemMode.STAY);
    scheduler.advance(LiveRefresh.POLL_TICKS);
    ArgumentCaptor<GolemElement> element = ArgumentCaptor.forClass(GolemElement.class);
    verify(window).updateElement(eq(0), eq(HEADER_ROW), element.capture());

    click(element.getValue(), ClickKind.LEFT);
    scheduler.advance(0);

    assertThat(mode.get()).isNotEqualTo(GolemMode.STAY);
    verify(window, times(2)).updateElement(eq(0), eq(HEADER_ROW), any(GolemElement.class));
    assertThat(windows.constructed()).hasSize(1);
    assertThat(scheduler.violations()).isEmpty();
  }

  @Test
  void closingTheWindowStopsTheRefreshAndLeavesNothingScheduled() {
    openAndDraw();
    GolemWindow window = onlyWindow();
    scheduler.advance(LiveRefresh.POLL_TICKS * 2);
    closeCallbacks.getFirst().accept(window);
    int reads = stateReads.get();
    carried.set(List.of("cobblestone-99"));

    scheduler.advance(LiveRefresh.POLL_TICKS * 20);

    assertThat(stateReads.get()).isEqualTo(reads);
    verify(window, never()).updateElement(anyInt(), anyInt(), any());
    assertThat(scheduler.pending()).isZero();
  }

  @Test
  void aWindowReplacedByAnotherMenuStopsTheRefresh() {
    openAndDraw();
    GolemWindow window = onlyWindow();
    visibility.get(window).set(false);
    carried.set(List.of("cobblestone-99"));

    scheduler.advance(LiveRefresh.POLL_TICKS * 20);

    verify(window, never()).updateElement(anyInt(), anyInt(), any());
    verify(window, never()).close();
    assertThat(scheduler.pending()).isZero();
    hud.verify(() -> HudStatus.show(any(PlayerRef.class), anyString()), never());
  }

  @Test
  void aPlayerWhoQuitsStopsTheRefresh() {
    openAndDraw();
    GolemWindow window = onlyWindow();
    online.set(false);
    carried.set(List.of("cobblestone-99"));

    scheduler.advance(LiveRefresh.POLL_TICKS * 20);

    verify(window, never()).updateElement(anyInt(), anyInt(), any());
    assertThat(scheduler.pending()).isZero();
  }

  @Test
  void aGolemThatIsReleasedOrPackedClosesTheGui() {
    openAndDraw();
    GolemWindow window = onlyWindow();
    retired.set(true);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).close();
    hud.verify(() -> HudStatus.show(eq(player), anyString()), times(1));
    assertThat(scheduler.pending()).isZero();
    assertThat(scheduler.violations()).isEmpty();
  }

  @Test
  void aGolemThatDiesOrUnloadsClosesTheGui() {
    openAndDraw();
    GolemWindow window = onlyWindow();
    valid.set(false);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).close();
    assertThat(scheduler.pending()).isZero();
  }

  @Test
  void aGolemSchedulerThatRetiresTheReadClosesTheGui() {
    openAndDraw();
    GolemWindow window = onlyWindow();

    scheduler.retire(golem);
    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).close();
    assertThat(scheduler.pending()).isZero();
  }

  @Test
  void nothingIsScheduledWhileNoGolemGuiIsOpen() {
    scheduler.advance(LiveRefresh.POLL_TICKS * 100);

    assertThat(scheduler.pending()).isZero();
    assertThat(stateReads).hasValue(0);
    assertThat(windows.constructed()).isEmpty();
  }

  @Test
  void openingAGolemThatIsAlreadyGoneStartsNoRefresh() {
    retired.set(true);

    GolemGui.open(player, handle, gestures);
    scheduler.advance(LiveRefresh.POLL_TICKS * 100);

    assertThat(windows.constructed()).isEmpty();
    assertThat(scheduler.pending()).isZero();
  }

  @Test
  void theMainPageIsAGridThatNamesEachTileForNativeClients() {
    openAndDraw();

    PanelView view = MenuReplay.view(onlyWindow());

    assertThat(view.layout()).isEqualTo(PanelLayout.GRID);
    PanelTile body = MenuReplay.tile(view, -4, HEADER_ROW);
    assertThat(body.role()).isEqualTo(TileRole.INFO);
    assertThat(body.value()).isEqualTo("20/20");
    PanelTile mode = MenuReplay.tile(view, 0, HEADER_ROW);
    String work = GolemMode.WORK.displayName().resolve();
    assertThat(mode.role()).isEqualTo(TileRole.SETTING);
    assertThat(mode.hint().kind()).isEqualTo(HintKind.CHOICE);
    assertThat(mode.hint().typed()).isFalse();
    assertThat(mode.hint().options()).contains(work);
    assertThat(mode.value()).isEqualTo(work);
    assertThat(MenuReplay.actions(mode)).containsEntry(ClickKind.LEFT, Text.key("adapt.gui.common.next").resolve())
        .containsEntry(ClickKind.RIGHT, Text.key("adapt.gui.common.previous").resolve());
    assertThat(mode.lore()).hasSize(3);
    assertThat(mode.description()).containsExactly(mode.lore().getFirst());
    PanelTile carried = MenuReplay.tile(view, CARRIED_FIRST, CARRIED_ROW);
    String take = Text.key("adapt.golemancy.gui.take").resolve();
    assertThat(carried.role()).isEqualTo(TileRole.ACTION);
    assertThat(carried.lore()).last().asString().contains(take);
    assertThat(carried.description()).noneMatch(line -> line.contains(take));
    assertThat(MenuReplay.actions(carried)).containsOnlyKeys(ClickKind.LEFT, ClickKind.SHIFT_LEFT).containsValue(take);
    PanelTile release = MenuReplay.tile(view, 4, FOOTER_ROW);
    assertThat(release.role()).isEqualTo(TileRole.ACTION);
    assertThat(release.hint().kind()).isEqualTo(HintKind.DESTRUCTIVE);
    assertThat(MenuReplay.actions(release)).containsEntry(ClickKind.LEFT, Text.key("adapt.golemancy.gui.release").resolve());
    assertThat(MenuReplay.tile(view, -4, FOOTER_ROW).role()).isEqualTo(TileRole.INFO);
    assertThat(MenuReplay.tile(view, 1, HEADER_ROW).role()).isEqualTo(TileRole.NAVIGATION);
  }

  @Test
  void annotationsLeaveTheChestTilesAsTheyWere() {
    openAndDraw();
    GolemElement mode = placed(onlyWindow(), 0, HEADER_ROW);
    GolemElement carried = placed(onlyWindow(), CARRIED_FIRST, CARRIED_ROW);

    MenuIcon modeIcon = mode.button().icon();
    MenuIcon carriedIcon = carried.button().icon();

    assertThat(modeIcon.name()).contains(GolemMode.WORK.displayName().resolve());
    assertThat(modeIcon.lore()).containsExactly("§7Do your core's jobs around home", "§7Left-click for next, right-click for", "§7previous");
    assertThat(mode.button().clickable()).isTrue();
    assertThat(carriedIcon.lore()).last().asString().contains(Text.key("adapt.golemancy.gui.take").resolve());
    assertThat(carriedIcon.amount()).isEqualTo(12);
    assertThat(placed(onlyWindow(), -4, FOOTER_ROW).button().clickable()).isFalse();
  }

  @Test
  void theChannelPageIsAFormOfChannelToggles() {
    openAndDraw();
    click(placed(onlyWindow(), 1, HEADER_ROW), ClickKind.LEFT);
    scheduler.advance(0);
    GolemWindow channels = windows.constructed().getLast();

    PanelView view = MenuReplay.view(channels);

    assertThat(windows.constructed()).hasSize(2);
    assertThat(view.layout()).isEqualTo(PanelLayout.FORM);
    assertThat(view.breadcrumb()).hasSize(1);
    PanelTile first = view.tiles().stream().filter(tile -> tile.hint() != null && tile.hint().kind() == HintKind.TOGGLE).findFirst().orElseThrow();
    assertThat(first.role()).isEqualTo(TileRole.SETTING);
    assertThat(first.hint().value()).isEqualTo("OFF");
    assertThat(first.description()).isEmpty();
    assertThat(view.tiles().stream().filter(tile -> tile.hint() != null && tile.hint().kind() == HintKind.TOGGLE)).hasSize(16);
  }

  private void click(GolemElement element, ClickKind kind) {
    MenuButton button = element.button();
    if (button.clickable()) {
      button.action().click(new MenuClick(player, null, 0, kind));
    }
  }

  private void openAndDraw() {
    GolemGui.open(player, handle, gestures);
    scheduler.advance(0);
    assertThat(windows.constructed()).hasSize(1);
    assertThat(scheduler.pending()).as("one pending poll after the first draw").isEqualTo(1);
  }

  private static GolemElement placed(GolemWindow window, int position, int row) {
    ArgumentCaptor<GolemElement> element = ArgumentCaptor.forClass(GolemElement.class);
    verify(window).setElement(eq(position), eq(row), element.capture());
    return element.getValue();
  }

  private GolemElement equipmentElement(String id) {
    ArgumentCaptor<GolemElement> placed = ArgumentCaptor.forClass(GolemElement.class);
    verify(onlyWindow(), times(GolemEquipmentSlot.values().length)).setElement(anyInt(), eq(EQUIPMENT_ROW), placed.capture());
    return placed.getAllValues().stream().filter(element -> id.equals(element.getId())).findFirst().orElseThrow();
  }

  private GolemWindow onlyWindow() {
    return windows.constructed().getFirst();
  }

  private static ItemRef stack(String encoded) {
    int split = encoded.lastIndexOf('-');
    return GolemTestItems.stack(Id.minecraft(encoded.substring(0, split).toLowerCase(Locale.ROOT)), Integer.parseInt(encoded.substring(split + 1)));
  }
}
