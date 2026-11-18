package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemAnchor;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemEquipmentSlot;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemRuneEffects;
import art.arcane.adapt.api.golem.GolemRuneLayout;
import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.api.golem.GolemRunes;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemTestItems;
import art.arcane.adapt.api.golem.GolemVitals;
import art.arcane.adapt.api.golem.GolemWorkStatus;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.adapt.util.common.scheduling.Tasks;
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
import java.util.function.Consumer;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
  private final Map<GuiWindow, AtomicBoolean> visibility = new IdentityHashMap<>();
  private final List<Consumer<GuiWindow>> closeCallbacks = new ArrayList<>();
  private MockedConstruction<GuiWindow> windows;
  private MockedStatic<GuiTheme> themes;
  private MockedStatic<Tasks> scheduling;
  private MockedStatic<GolemItems> items;
  private MockedStatic<GolemRunes> runeEffects;
  private MockedStatic<GolemVitals> vitals;
  private MockedStatic<AdaptHud> hud;
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

    scheduling = mockStatic(Tasks.class);
    scheduler.install(scheduling);
    themes = mockStatic(GuiTheme.class, invocation -> "element".equals(invocation.getMethod().getName())
        ? new GuiElement(invocation.getArgument(0)).setMaterial(invocation.getArgument(1))
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
    hud = mockStatic(AdaptHud.class);
    windows = mockConstruction(GuiWindow.class, (window, context) -> {
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
    GuiWindow window = onlyWindow();

    verify(window).setElement(eq(-4), eq(HEADER_ROW), any(GuiElement.class));
    verify(window).setElement(eq(-3), eq(HEADER_ROW), any(GuiElement.class));
    verify(window).setElement(eq(0), eq(HEADER_ROW), any(GuiElement.class));
    verify(window).setElement(eq(CARRIED_FIRST), eq(CARRIED_ROW), any(GuiElement.class));
    verify(window).setElement(eq(CARRIED_FIRST + 1), eq(CARRIED_ROW), any(GuiElement.class));
    verify(window, never()).setElement(eq(CARRIED_FIRST + 2), eq(CARRIED_ROW), any(GuiElement.class));
    verify(window).setElement(eq(-4), eq(FOOTER_ROW), any(GuiElement.class));
    verify(window).setElement(eq(4), eq(FOOTER_ROW), any(GuiElement.class));
    verify(window, never()).updateElement(anyInt(), anyInt(), any());
  }

  @Test
  void aCarriedCountThatGrowsUpdatesOnlyThatSlotOfTheOpenWindow() {
    openAndDraw();
    GuiWindow window = onlyWindow();
    carried.set(List.of("cobblestone-13", "oak_log-4"));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(CARRIED_FIRST), eq(CARRIED_ROW), any(GuiElement.class));
    assertThat(scheduler.violations()).isEmpty();
  }

  @Test
  void aCarriedChangeNeverReopensTheWindowOrBuildsANewOne() {
    openAndDraw();
    GuiWindow window = onlyWindow();
    carried.set(List.of("cobblestone-13", "oak_log-4"));

    scheduler.advance(LiveRefresh.POLL_TICKS * 3);

    assertThat(windows.constructed()).hasSize(1);
    verify(window, times(1)).open();
    verify(window, never()).close();
  }

  @Test
  void aNewCarriedStackFillsTheNextSlot() {
    openAndDraw();
    GuiWindow window = onlyWindow();
    carried.set(List.of("cobblestone-12", "oak_log-4", "dirt-2"));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(CARRIED_FIRST + 2), eq(CARRIED_ROW), any(GuiElement.class));
  }

  @Test
  void aTakenCarriedStackClearsItsSlot() {
    openAndDraw();
    GuiWindow window = onlyWindow();
    carried.set(List.of("cobblestone-12"));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(CARRIED_FIRST + 1), eq(CARRIED_ROW), eq(null));
  }

  @Test
  void carriedPastTheShownSlotsAddsTheMoreCarriedNote() {
    openAndDraw();
    GuiWindow window = onlyWindow();
    List<String> many = new ArrayList<>();
    for (int index = 0; index < 20; index++) {
      many.add("dirt-" + (index + 1));
    }
    carried.set(many);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    ArgumentCaptor<GuiElement> note = ArgumentCaptor.forClass(GuiElement.class);
    verify(window).updateElement(eq(CARRIED_FIRST + 8), eq(CARRIED_ROW + 1), note.capture());
    assertThat(note.getValue().getId()).isEqualTo("golem-carried-more");
  }

  @Test
  void healthThatChangesTheShownValueUpdatesTheBodyElement() {
    openAndDraw();
    GuiWindow window = onlyWindow();
    health.set(18.2D);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(-4), eq(HEADER_ROW), any(GuiElement.class));
  }

  @Test
  void healthThatKeepsTheShownValueUpdatesNothing() {
    openAndDraw();
    GuiWindow window = onlyWindow();
    health.set(19.4D);

    scheduler.advance(LiveRefresh.POLL_TICKS * 3);

    verify(window, never()).updateElement(anyInt(), anyInt(), any());
  }

  @Test
  void aModeChangeUpdatesTheModeAndWorkStatus() {
    openAndDraw();
    GuiWindow window = onlyWindow();
    mode.set(GolemMode.FOLLOW);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(2)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(-4), eq(HEADER_ROW), any(GuiElement.class));
    verify(window).updateElement(eq(0), eq(HEADER_ROW), any(GuiElement.class));
  }

  @Test
  void theMainPagePlacesTheCombatToggleNextToMode() {
    openAndDraw();

    verify(onlyWindow()).setElement(eq(-1), eq(HEADER_ROW), any(GuiElement.class));
  }

  @Test
  void clickingTheCombatToggleTurnsCombatOffOnTheGolemSchedulerAndUpdatesInPlace() {
    openAndDraw();
    GuiWindow window = onlyWindow();
    GuiElement toggle = placed(window, -1, HEADER_ROW);

    click(toggle, ClickKind.LEFT);
    scheduler.advance(0);

    assertThat(combatOff.get()).isTrue();
    verify(handle).setCombat(false);
    verify(window).updateElement(eq(-1), eq(HEADER_ROW), any(GuiElement.class));
    assertThat(windows.constructed()).hasSize(1);
    assertThat(scheduler.violations()).isEmpty();
  }

  @Test
  void clickingTheCombatToggleAgainTurnsCombatBackOn() {
    combatOff.set(true);
    openAndDraw();
    GuiWindow window = onlyWindow();
    GuiElement toggle = placed(window, -1, HEADER_ROW);

    click(toggle, ClickKind.LEFT);
    scheduler.advance(0);

    assertThat(combatOff.get()).isFalse();
    verify(handle).setCombat(true);
    assertThat(scheduler.violations()).isEmpty();
  }

  @Test
  void aCombatChangeFromElsewhereUpdatesTheToggleInPlace() {
    openAndDraw();
    GuiWindow window = onlyWindow();
    combatOff.set(true);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window).updateElement(eq(-1), eq(HEADER_ROW), any(GuiElement.class));
    assertThat(scheduler.violations()).isEmpty();
  }

  @Test
  void aGuardiansCombatToggleIsLockedAndIgnoresClicks() {
    cores.set(List.of(GolemCoreType.GUARDIAN));
    openAndDraw();
    GuiElement toggle = placed(onlyWindow(), -1, HEADER_ROW);

    click(toggle, ClickKind.LEFT);
    scheduler.advance(0);

    assertThat(combatOff.get()).isFalse();
    verify(handle, never()).setCombat(anyBoolean());
  }

  @Test
  void essenceUpdatesTheEssenceElement() {
    openAndDraw();
    GuiWindow window = onlyWindow();
    essence.set(7);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(2), eq(FOOTER_ROW), any(GuiElement.class));
  }

  @Test
  void theStatisticsLoreUpdatesTheStatsElement() {
    openAndDraw();
    GuiWindow window = onlyWindow();
    tasks.set(5L);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(-4), eq(FOOTER_ROW), any(GuiElement.class));
  }

  @Test
  void aSlottedCoreUpdatesTheCoreSlotsAndTheEssenceElement() {
    openAndDraw();
    GuiWindow window = onlyWindow();
    cores.set(List.of(GolemCoreType.BREAKER, GolemCoreType.GATHERER));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(3)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(-3), eq(HEADER_ROW), any(GuiElement.class));
    verify(window).updateElement(eq(-2), eq(HEADER_ROW), any(GuiElement.class));
    verify(window).updateElement(eq(2), eq(FOOTER_ROW), any(GuiElement.class));
  }

  @Test
  void aSlottedRuneUpdatesTheRuneRow() {
    openAndDraw();
    GuiWindow window = onlyWindow();
    runes.set(List.of(new GolemRune(GolemRuneType.MIGHT, 1)));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window).updateElement(eq(GuiLayout.centeredPosition(0, 2)), eq(1), any(GuiElement.class));
    verify(window, never()).updateElement(anyInt(), eq(CARRIED_ROW), any());
    verify(window, never()).updateElement(anyInt(), eq(HEADER_ROW), any());
  }

  @Test
  void aBoneGolemsRuneRowShowsItsOpenAndLockedSlots() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    openAndDraw();
    GuiWindow window = onlyWindow();

    verify(window, times(BONE_LAYOUT.maxSlots())).setElement(anyInt(), eq(RUNE_ROW), any(GuiElement.class));
    for (int index = 0; index < BONE_LAYOUT.maxSlots(); index++) {
      verify(window).setElement(eq(GuiLayout.centeredPosition(index, BONE_LAYOUT.maxSlots())), eq(RUNE_ROW), any(GuiElement.class));
    }
  }

  @Test
  void aRuneSlottedIntoABoneGolemUpdatesItsRuneRow() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    openAndDraw();
    GuiWindow window = onlyWindow();
    runes.set(List.of(new GolemRune(GolemRuneType.SWIFTNESS, 1)));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(BONE_LAYOUT.maxSlots())).updateElement(anyInt(), eq(RUNE_ROW), any(GuiElement.class));
    verify(window, times(BONE_LAYOUT.maxSlots())).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(GuiLayout.centeredPosition(0, BONE_LAYOUT.maxSlots())), eq(RUNE_ROW), any(GuiElement.class));
  }

  @Test
  void aBoneGolemShowsASixSlotEquipmentRowAndOtherBodiesDoNot() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    openAndDraw();
    GuiWindow bone = onlyWindow();

    ArgumentCaptor<GuiElement> placed = ArgumentCaptor.forClass(GuiElement.class);
    verify(bone, times(GolemEquipmentSlot.values().length)).setElement(anyInt(), eq(EQUIPMENT_ROW), placed.capture());
    assertThat(placed.getAllValues()).extracting(GuiElement::getId).containsExactly("golem-equipment-helmet", "golem-equipment-chestplate",
        "golem-equipment-leggings", "golem-equipment-boots", "golem-equipment-main_hand", "golem-equipment-off_hand");
    for (int index = 0; index < GolemEquipmentSlot.values().length; index++) {
      verify(bone).setElement(eq(GuiLayout.centeredPosition(index, GolemEquipmentSlot.values().length)), eq(EQUIPMENT_ROW), any(GuiElement.class));
    }
  }

  @Test
  void aNonBoneGolemHasNoEquipmentRow() {
    carried.set(List.of());
    equipment.set(Map.of(GolemEquipmentSlot.HELMET, "iron_helmet-1"));
    openAndDraw();

    verify(onlyWindow(), never()).setElement(anyInt(), eq(EQUIPMENT_ROW), any(GuiElement.class));
  }

  @Test
  void aWornItemUpdatesOnlyItsOwnSlot() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    openAndDraw();
    GuiWindow window = onlyWindow();
    equipment.set(Map.of(GolemEquipmentSlot.BOOTS, "iron_boots-1"));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(GuiLayout.centeredPosition(GolemEquipmentSlot.BOOTS.ordinal(), GolemEquipmentSlot.values().length)),
        eq(EQUIPMENT_ROW), any(GuiElement.class));
  }

  @Test
  void clickingAWornItemTwiceHandsItBackOnlyOnce() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    equipment.set(Map.of(GolemEquipmentSlot.BOOTS, "iron_boots-1"));
    ItemRef boots = GolemTestItems.stack(Id.minecraft("iron_boots"), 1);
    when(handle.unequip(GolemEquipmentSlot.BOOTS, "iron_boots-1")).thenReturn(Optional.of(boots)).thenReturn(Optional.empty());
    openAndDraw();
    GuiElement element = equipmentElement("golem-equipment-boots");

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
    GuiElement element = equipmentElement("golem-equipment-main_hand");

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
    GuiElement element = equipmentElement("golem-equipment-helmet");

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
    GuiElement element = equipmentElement("golem-equipment-helmet");

    click(element, ClickKind.LEFT);
    scheduler.advance(0);

    verify(handle, never()).equip(any(GolemEquipmentSlot.class), any(ItemRef.class));
    verify(inventory, never()).setCursor(any());
    assertThat(cursor.amount()).isEqualTo(1);
    hud.verify(() -> AdaptHud.actionBar(eq(player), anyString()), times(1));
  }

  @Test
  void anEmptyCursorOnAnEmptySlotAsksForAnItem() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    when(inventory.cursor()).thenReturn(FakeItem.empty());
    openAndDraw();
    GuiElement element = equipmentElement("golem-equipment-main_hand");

    click(element, ClickKind.LEFT);
    scheduler.advance(0);

    verify(handle, never()).equip(any(GolemEquipmentSlot.class), any(ItemRef.class));
    hud.verify(() -> AdaptHud.actionBar(eq(player), anyString()), times(1));
  }

  @Test
  void aRefusedEquipHandsTheTakenItemBackToThePlayer() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    ItemRef cursor = GolemTestItems.stack(Id.minecraft("shield"), 1);
    when(inventory.cursor()).thenReturn(cursor);
    when(handle.equip(any(GolemEquipmentSlot.class), any(ItemRef.class))).thenReturn(false);
    openAndDraw();
    GuiElement element = equipmentElement("golem-equipment-off_hand");

    click(element, ClickKind.LEFT);
    scheduler.advance(0);

    verify(inventory, times(1)).addItem(any(ItemRef.class));
  }

  @Test
  void aStateThatNeverChangesUpdatesNothingForever() {
    openAndDraw();
    GuiWindow window = onlyWindow();

    scheduler.advance(LiveRefresh.POLL_TICKS * 50);

    verify(window, never()).updateElement(anyInt(), anyInt(), any());
    assertThat(windows.constructed()).hasSize(1);
    assertThat(scheduler.pending()).isEqualTo(1);
  }

  @Test
  void aMutationFromTheWindowUpdatesInPlaceToo() {
    openAndDraw();
    GuiWindow window = onlyWindow();
    mode.set(GolemMode.STAY);
    scheduler.advance(LiveRefresh.POLL_TICKS);
    ArgumentCaptor<GuiElement> element = ArgumentCaptor.forClass(GuiElement.class);
    verify(window).updateElement(eq(0), eq(HEADER_ROW), element.capture());

    click(element.getValue(), ClickKind.LEFT);
    scheduler.advance(0);

    assertThat(mode.get()).isNotEqualTo(GolemMode.STAY);
    verify(window, times(2)).updateElement(eq(0), eq(HEADER_ROW), any(GuiElement.class));
    assertThat(windows.constructed()).hasSize(1);
    assertThat(scheduler.violations()).isEmpty();
  }

  @Test
  void closingTheWindowStopsTheRefreshAndLeavesNothingScheduled() {
    openAndDraw();
    GuiWindow window = onlyWindow();
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
    GuiWindow window = onlyWindow();
    visibility.get(window).set(false);
    carried.set(List.of("cobblestone-99"));

    scheduler.advance(LiveRefresh.POLL_TICKS * 20);

    verify(window, never()).updateElement(anyInt(), anyInt(), any());
    verify(window, never()).close();
    assertThat(scheduler.pending()).isZero();
    hud.verify(() -> AdaptHud.actionBar(any(PlayerRef.class), anyString()), never());
  }

  @Test
  void aPlayerWhoQuitsStopsTheRefresh() {
    openAndDraw();
    GuiWindow window = onlyWindow();
    online.set(false);
    carried.set(List.of("cobblestone-99"));

    scheduler.advance(LiveRefresh.POLL_TICKS * 20);

    verify(window, never()).updateElement(anyInt(), anyInt(), any());
    assertThat(scheduler.pending()).isZero();
  }

  @Test
  void aGolemThatIsReleasedOrPackedClosesTheGui() {
    openAndDraw();
    GuiWindow window = onlyWindow();
    retired.set(true);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).close();
    hud.verify(() -> AdaptHud.actionBar(eq(player), anyString()), times(1));
    assertThat(scheduler.pending()).isZero();
    assertThat(scheduler.violations()).isEmpty();
  }

  @Test
  void aGolemThatDiesOrUnloadsClosesTheGui() {
    openAndDraw();
    GuiWindow window = onlyWindow();
    valid.set(false);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).close();
    assertThat(scheduler.pending()).isZero();
  }

  @Test
  void aGolemSchedulerThatRetiresTheReadClosesTheGui() {
    openAndDraw();
    GuiWindow window = onlyWindow();

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

  private void click(GuiElement element, ClickKind kind) {
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

  private static GuiElement placed(GuiWindow window, int position, int row) {
    ArgumentCaptor<GuiElement> element = ArgumentCaptor.forClass(GuiElement.class);
    verify(window).setElement(eq(position), eq(row), element.capture());
    return element.getValue();
  }

  private GuiElement equipmentElement(String id) {
    ArgumentCaptor<GuiElement> placed = ArgumentCaptor.forClass(GuiElement.class);
    verify(onlyWindow(), times(GolemEquipmentSlot.values().length)).setElement(anyInt(), eq(EQUIPMENT_ROW), placed.capture());
    return placed.getAllValues().stream().filter(element -> id.equals(element.getId())).findFirst().orElseThrow();
  }

  private GuiWindow onlyWindow() {
    return windows.constructed().getFirst();
  }

  private static ItemRef stack(String encoded) {
    int split = encoded.lastIndexOf('-');
    return GolemTestItems.stack(Id.minecraft(encoded.substring(0, split).toLowerCase(Locale.ROOT)), Integer.parseInt(encoded.substring(split + 1)));
  }
}
