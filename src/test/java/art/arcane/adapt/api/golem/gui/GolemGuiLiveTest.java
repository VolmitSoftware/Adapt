package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.AdaptTestBase;
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
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.nms.NMS;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.inventorygui.ElementEvent;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.inventorygui.Window;
import art.arcane.volmlib.util.scheduling.Callback;
import org.bukkit.Material;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;
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
import java.util.concurrent.ConcurrentHashMap;
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
  private final PlayerInventory inventory = mock(PlayerInventory.class);
  private final AtomicReference<List<String>> carried = new AtomicReference<>(List.of("cobblestone-12", "oak_log-4"));
  private final AtomicReference<List<GolemCoreType>> cores = new AtomicReference<>(List.of(GolemCoreType.BREAKER));
  private final AtomicReference<List<GolemRune>> runes = new AtomicReference<>(List.of());
  private final AtomicReference<GolemMode> mode = new AtomicReference<>(GolemMode.WORK);
  private final AtomicReference<Double> health = new AtomicReference<>(20D);
  private final AtomicInteger essence = new AtomicInteger();
  private final AtomicLong tasks = new AtomicLong(4L);
  private final AtomicBoolean valid = new AtomicBoolean(true);
  private final AtomicBoolean retired = new AtomicBoolean();
  private final AtomicBoolean online = new AtomicBoolean(true);
  private final AtomicInteger stateReads = new AtomicInteger();
  private final Map<UIWindow, AtomicBoolean> visibility = new IdentityHashMap<>();
  private final List<Callback<Window>> closeCallbacks = new ArrayList<>();
  private final Inventory top = mock(Inventory.class);
  private MockedConstruction<UIWindow> windows;
  private MockedStatic<GuiTheme> themes;
  private MockedStatic<J> scheduling;
  private MockedStatic<NMS> nms;
  private MockedStatic<GolemItems> items;
  private MockedStatic<GolemRunes> runeEffects;
  private MockedStatic<GolemVitals> vitals;
  private MockedStatic<AdaptHud> hud;
  private IronGolem golem;
  private Player player;
  private GolemHandle handle;
  private CrookGestures<GolemHandle> gestures;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    when(plugin.getGuiLeftovers()).thenReturn(new ConcurrentHashMap<>());
    golem = mock(IronGolem.class);
    lenient().when(golem.isValid()).thenAnswer(invocation -> valid.get());
    lenient().when(golem.getHealth()).thenAnswer(invocation -> health.get());
    player = mock(Player.class);
    lenient().when(player.getUniqueId()).thenReturn(VIEWER);
    lenient().when(player.isOnline()).thenAnswer(invocation -> online.get());
    InventoryView view = mock(InventoryView.class);
    lenient().when(view.getTopInventory()).thenReturn(top);
    lenient().when(player.getOpenInventory()).thenReturn(view);
    GolemState.Stats stats = mock(GolemState.Stats.class);
    lenient().when(stats.tasks()).thenAnswer(invocation -> tasks.get());
    GolemState state = mock(GolemState.class);
    lenient().when(state.body()).thenAnswer(invocation -> body.get());
    lenient().when(state.cores()).thenAnswer(invocation -> cores.get());
    lenient().when(state.primaryCore()).thenAnswer(invocation -> cores.get().getFirst());
    lenient().when(state.runes()).thenAnswer(invocation -> runes.get());
    lenient().when(state.mode()).thenAnswer(invocation -> mode.get());
    lenient().when(state.home()).thenReturn(GolemState.Anchor.of(WORLD, 0, 64, 0));
    lenient().when(state.carried()).thenAnswer(invocation -> carried.get());
    lenient().when(state.equipment()).thenAnswer(invocation -> equipment.get());
    lenient().when(player.getInventory()).thenReturn(inventory);
    lenient().when(inventory.addItem(any(ItemStack[].class))).thenReturn(new java.util.HashMap<>());
    lenient().when(state.essencePoints()).thenAnswer(invocation -> essence.get());
    lenient().when(state.stats()).thenReturn(stats);
    handle = mock(GolemHandle.class);
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
    gestures = mock(CrookGestures.class);

    scheduling = mockStatic(J.class);
    scheduler.install(scheduling);
    themes = mockStatic(GuiTheme.class, invocation -> "element".equals(invocation.getMethod().getName())
        ? new UIElement(invocation.getArgument(0))
        : null);
    nms = mockStatic(NMS.class);
    nms.when(() -> NMS.deserializeStack(anyString())).thenAnswer(invocation -> stack(invocation.getArgument(0)));
    items = mockStatic(GolemItems.class);
    items.when(() -> GolemItems.core(any(GolemCoreType.class))).thenAnswer(invocation -> GolemTestItems.stack(Material.PAPER, 1));
    items.when(() -> GolemItems.rune(any(GolemRune.class))).thenAnswer(invocation -> GolemTestItems.stack(Material.AMETHYST_SHARD, 1));
    runeEffects = mockStatic(GolemRunes.class);
    runeEffects.when(() -> GolemRunes.effects(any(GolemHandle.class))).thenReturn(GolemRuneEffects.NONE);
    runeEffects.when(() -> GolemRunes.layout(any(GolemBody.class), any(UUID.class), anyBoolean())).thenReturn(LAYOUT);
    runeEffects.when(() -> GolemRunes.layout(eq(GolemBody.BONE), any(UUID.class), anyBoolean())).thenReturn(BONE_LAYOUT);
    vitals = mockStatic(GolemVitals.class);
    vitals.when(() -> GolemVitals.maxHealth(any())).thenReturn(20D);
    hud = mockStatic(AdaptHud.class);
    windows = mockConstruction(UIWindow.class, (window, context) -> {
      AtomicBoolean visible = new AtomicBoolean(true);
      visibility.put(window, visible);
      lenient().when(window.isVisible()).thenAnswer(invocation -> visible.get());
      lenient().when(window.close()).thenAnswer(invocation -> {
        scheduler.expect(player, "window close");
        visible.set(false);
        return window;
      });
      lenient().when(window.open()).thenAnswer(invocation -> {
        scheduler.expect(player, "window open");
        return window;
      });
      lenient().when(window.updateElement(anyInt(), anyInt(), any())).thenAnswer(invocation -> {
        scheduler.expect(player, "window update");
        return window;
      });
      lenient().when(window.batch(any())).thenAnswer(invocation -> {
        invocation.<Runnable>getArgument(0).run();
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
    GuiCursor.untrack(VIEWER, top);
    windows.close();
    hud.close();
    vitals.close();
    runeEffects.close();
    items.close();
    nms.close();
    themes.close();
    scheduling.close();
  }

  @Test
  void theFirstDrawPlacesEveryElementOfTheMainPage() {
    openAndDraw();
    UIWindow window = onlyWindow();

    verify(window).setElement(eq(-4), eq(HEADER_ROW), any(Element.class));
    verify(window).setElement(eq(-3), eq(HEADER_ROW), any(Element.class));
    verify(window).setElement(eq(0), eq(HEADER_ROW), any(Element.class));
    verify(window).setElement(eq(CARRIED_FIRST), eq(CARRIED_ROW), any(Element.class));
    verify(window).setElement(eq(CARRIED_FIRST + 1), eq(CARRIED_ROW), any(Element.class));
    verify(window, never()).setElement(eq(CARRIED_FIRST + 2), eq(CARRIED_ROW), any(Element.class));
    verify(window).setElement(eq(-4), eq(FOOTER_ROW), any(Element.class));
    verify(window).setElement(eq(4), eq(FOOTER_ROW), any(Element.class));
    verify(window, never()).updateElement(anyInt(), anyInt(), any());
  }

  @Test
  void aCarriedCountThatGrowsUpdatesOnlyThatSlotOfTheOpenWindow() {
    openAndDraw();
    UIWindow window = onlyWindow();
    carried.set(List.of("cobblestone-13", "oak_log-4"));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(CARRIED_FIRST), eq(CARRIED_ROW), any(Element.class));
    assertThat(scheduler.violations()).isEmpty();
  }

  @Test
  void aCarriedChangeNeverReopensTheWindowOrBuildsANewOne() {
    openAndDraw();
    UIWindow window = onlyWindow();
    carried.set(List.of("cobblestone-13", "oak_log-4"));

    scheduler.advance(LiveRefresh.POLL_TICKS * 3);

    assertThat(windows.constructed()).hasSize(1);
    verify(window, times(1)).open();
    verify(window, never()).close();
  }

  @Test
  void aNewCarriedStackFillsTheNextSlot() {
    openAndDraw();
    UIWindow window = onlyWindow();
    carried.set(List.of("cobblestone-12", "oak_log-4", "dirt-2"));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(CARRIED_FIRST + 2), eq(CARRIED_ROW), any(Element.class));
  }

  @Test
  void aTakenCarriedStackClearsItsSlot() {
    openAndDraw();
    UIWindow window = onlyWindow();
    carried.set(List.of("cobblestone-12"));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(CARRIED_FIRST + 1), eq(CARRIED_ROW), eq(null));
  }

  @Test
  void carriedPastTheShownSlotsAddsTheMoreCarriedNote() {
    openAndDraw();
    UIWindow window = onlyWindow();
    List<String> many = new ArrayList<>();
    for (int index = 0; index < 20; index++) {
      many.add("dirt-" + (index + 1));
    }
    carried.set(many);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    ArgumentCaptor<Element> note = ArgumentCaptor.forClass(Element.class);
    verify(window).updateElement(eq(CARRIED_FIRST + 8), eq(CARRIED_ROW + 1), note.capture());
    assertThat(note.getValue().getId()).isEqualTo("golem-carried-more");
  }

  @Test
  void healthThatChangesTheShownValueUpdatesTheBodyElement() {
    openAndDraw();
    UIWindow window = onlyWindow();
    health.set(18.2D);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(-4), eq(HEADER_ROW), any(Element.class));
  }

  @Test
  void healthThatKeepsTheShownValueUpdatesNothing() {
    openAndDraw();
    UIWindow window = onlyWindow();
    health.set(19.4D);

    scheduler.advance(LiveRefresh.POLL_TICKS * 3);

    verify(window, never()).updateElement(anyInt(), anyInt(), any());
  }

  @Test
  void aModeChangeUpdatesTheModeElement() {
    openAndDraw();
    UIWindow window = onlyWindow();
    mode.set(GolemMode.FOLLOW);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(0), eq(HEADER_ROW), any(Element.class));
  }

  @Test
  void essenceUpdatesTheEssenceElement() {
    openAndDraw();
    UIWindow window = onlyWindow();
    essence.set(7);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(2), eq(FOOTER_ROW), any(Element.class));
  }

  @Test
  void theStatisticsLoreUpdatesTheStatsElement() {
    openAndDraw();
    UIWindow window = onlyWindow();
    tasks.set(5L);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(-4), eq(FOOTER_ROW), any(Element.class));
  }

  @Test
  void aSlottedCoreUpdatesTheCoreSlotsAndTheEssenceElement() {
    openAndDraw();
    UIWindow window = onlyWindow();
    cores.set(List.of(GolemCoreType.BREAKER, GolemCoreType.GATHERER));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(3)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(-3), eq(HEADER_ROW), any(Element.class));
    verify(window).updateElement(eq(-2), eq(HEADER_ROW), any(Element.class));
    verify(window).updateElement(eq(2), eq(FOOTER_ROW), any(Element.class));
  }

  @Test
  void aSlottedRuneUpdatesTheRuneRow() {
    openAndDraw();
    UIWindow window = onlyWindow();
    runes.set(List.of(new GolemRune(GolemRuneType.MIGHT, 1)));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window).updateElement(eq(GuiLayout.centeredPosition(0, 2)), eq(1), any(Element.class));
    verify(window, never()).updateElement(anyInt(), eq(CARRIED_ROW), any());
    verify(window, never()).updateElement(anyInt(), eq(HEADER_ROW), any());
  }

  @Test
  void aBoneGolemsRuneRowShowsItsOpenAndLockedSlots() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    openAndDraw();
    UIWindow window = onlyWindow();

    verify(window, times(BONE_LAYOUT.maxSlots())).setElement(anyInt(), eq(RUNE_ROW), any(Element.class));
    for (int index = 0; index < BONE_LAYOUT.maxSlots(); index++) {
      verify(window).setElement(eq(GuiLayout.centeredPosition(index, BONE_LAYOUT.maxSlots())), eq(RUNE_ROW), any(Element.class));
    }
  }

  @Test
  void aRuneSlottedIntoABoneGolemUpdatesItsRuneRow() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    openAndDraw();
    UIWindow window = onlyWindow();
    runes.set(List.of(new GolemRune(GolemRuneType.SWIFTNESS, 1)));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(BONE_LAYOUT.maxSlots())).updateElement(anyInt(), eq(RUNE_ROW), any(Element.class));
    verify(window, times(BONE_LAYOUT.maxSlots())).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(GuiLayout.centeredPosition(0, BONE_LAYOUT.maxSlots())), eq(RUNE_ROW), any(Element.class));
  }

  @Test
  void aBoneGolemShowsASixSlotEquipmentRowAndOtherBodiesDoNot() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    openAndDraw();
    UIWindow bone = onlyWindow();

    ArgumentCaptor<Element> placed = ArgumentCaptor.forClass(Element.class);
    verify(bone, times(GolemEquipmentSlot.values().length)).setElement(anyInt(), eq(EQUIPMENT_ROW), placed.capture());
    assertThat(placed.getAllValues()).extracting(Element::getId).containsExactly("golem-equipment-helmet", "golem-equipment-chestplate",
        "golem-equipment-leggings", "golem-equipment-boots", "golem-equipment-main_hand", "golem-equipment-off_hand");
    for (int index = 0; index < GolemEquipmentSlot.values().length; index++) {
      verify(bone).setElement(eq(GuiLayout.centeredPosition(index, GolemEquipmentSlot.values().length)), eq(EQUIPMENT_ROW), any(Element.class));
    }
  }

  @Test
  void aNonBoneGolemHasNoEquipmentRow() {
    carried.set(List.of());
    equipment.set(Map.of(GolemEquipmentSlot.HELMET, "iron_helmet-1"));
    openAndDraw();

    verify(onlyWindow(), never()).setElement(anyInt(), eq(EQUIPMENT_ROW), any(Element.class));
  }

  @Test
  void aWornItemUpdatesOnlyItsOwnSlot() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    openAndDraw();
    UIWindow window = onlyWindow();
    equipment.set(Map.of(GolemEquipmentSlot.BOOTS, "iron_boots-1"));

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).updateElement(anyInt(), anyInt(), any());
    verify(window).updateElement(eq(GuiLayout.centeredPosition(GolemEquipmentSlot.BOOTS.ordinal(), GolemEquipmentSlot.values().length)),
        eq(EQUIPMENT_ROW), any(Element.class));
  }

  @Test
  void clickingAWornItemTwiceHandsItBackOnlyOnce() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    equipment.set(Map.of(GolemEquipmentSlot.BOOTS, "iron_boots-1"));
    ItemStack boots = GolemTestItems.stack(Material.IRON_BOOTS, 1);
    when(handle.unequip(GolemEquipmentSlot.BOOTS, "iron_boots-1")).thenReturn(Optional.of(boots)).thenReturn(Optional.empty());
    openAndDraw();
    Element element = equipmentElement("golem-equipment-boots");

    element.call(ElementEvent.LEFT, element);
    element.call(ElementEvent.LEFT, element);
    scheduler.advance(0);

    verify(handle, times(2)).unequip(GolemEquipmentSlot.BOOTS, "iron_boots-1");
    verify(inventory, times(1)).addItem(any(ItemStack[].class));
    assertThat(scheduler.violations()).isEmpty();
  }

  @Test
  void aShiftClickOnAWornItemTakesItBackOnce() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    equipment.set(Map.of(GolemEquipmentSlot.MAIN_HAND, "bow-1"));
    ItemStack bow = GolemTestItems.stack(Material.BOW, 1);
    when(handle.unequip(GolemEquipmentSlot.MAIN_HAND, "bow-1")).thenReturn(Optional.of(bow)).thenReturn(Optional.empty());
    openAndDraw();
    Element element = equipmentElement("golem-equipment-main_hand");

    element.call(ElementEvent.SHIFT_LEFT, element);
    element.call(ElementEvent.LEFT, element);
    scheduler.advance(0);

    verify(inventory, times(1)).addItem(any(ItemStack[].class));
  }

  @Test
  void anItemFromTheCursorIsEquippedOneAtATimeIntoAnEmptySlot() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    ItemStack cursor = GolemTestItems.stack(Material.IRON_HELMET, 2);
    when(player.getItemOnCursor()).thenReturn(cursor);
    when(handle.equip(eq(GolemEquipmentSlot.HELMET), any(ItemStack.class))).thenReturn(true);
    openAndDraw();
    Element element = equipmentElement("golem-equipment-helmet");

    element.call(ElementEvent.LEFT, element);
    scheduler.advance(0);

    ArgumentCaptor<ItemStack> equipped = ArgumentCaptor.forClass(ItemStack.class);
    verify(handle).equip(eq(GolemEquipmentSlot.HELMET), equipped.capture());
    assertThat(equipped.getValue().getAmount()).isEqualTo(1);
    assertThat(cursor.getAmount()).isEqualTo(1);
    verify(inventory, never()).addItem(any(ItemStack[].class));
  }

  @Test
  void anItemThatDoesNotFitTheSlotStaysOnTheCursor() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    ItemStack cursor = GolemTestItems.stack(Material.IRON_BOOTS, 1);
    when(player.getItemOnCursor()).thenReturn(cursor);
    openAndDraw();
    Element element = equipmentElement("golem-equipment-helmet");

    element.call(ElementEvent.LEFT, element);
    scheduler.advance(0);

    verify(handle, never()).equip(any(GolemEquipmentSlot.class), any(ItemStack.class));
    verify(player, never()).setItemOnCursor(any());
    assertThat(cursor.getAmount()).isEqualTo(1);
    hud.verify(() -> AdaptHud.actionBar(eq(player), anyString()), times(1));
  }

  @Test
  void anEmptyCursorOnAnEmptySlotAsksForAnItem() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    when(player.getItemOnCursor()).thenReturn(null);
    openAndDraw();
    Element element = equipmentElement("golem-equipment-main_hand");

    element.call(ElementEvent.LEFT, element);
    scheduler.advance(0);

    verify(handle, never()).equip(any(GolemEquipmentSlot.class), any(ItemStack.class));
    hud.verify(() -> AdaptHud.actionBar(eq(player), anyString()), times(1));
  }

  @Test
  void aRefusedEquipHandsTheTakenItemBackToThePlayer() {
    body.set(GolemBody.BONE);
    carried.set(List.of());
    ItemStack cursor = GolemTestItems.stack(Material.SHIELD, 1);
    when(player.getItemOnCursor()).thenReturn(cursor);
    when(handle.equip(any(GolemEquipmentSlot.class), any(ItemStack.class))).thenReturn(false);
    openAndDraw();
    Element element = equipmentElement("golem-equipment-off_hand");

    element.call(ElementEvent.LEFT, element);
    scheduler.advance(0);

    verify(inventory, times(1)).addItem(any(ItemStack[].class));
  }

  @Test
  void aStateThatNeverChangesUpdatesNothingForever() {
    openAndDraw();
    UIWindow window = onlyWindow();

    scheduler.advance(LiveRefresh.POLL_TICKS * 50);

    verify(window, never()).updateElement(anyInt(), anyInt(), any());
    assertThat(windows.constructed()).hasSize(1);
    assertThat(scheduler.pending()).isEqualTo(1);
  }

  @Test
  void aMutationFromTheWindowUpdatesInPlaceToo() {
    openAndDraw();
    UIWindow window = onlyWindow();
    mode.set(GolemMode.STAY);
    scheduler.advance(LiveRefresh.POLL_TICKS);
    ArgumentCaptor<Element> element = ArgumentCaptor.forClass(Element.class);
    verify(window).updateElement(eq(0), eq(HEADER_ROW), element.capture());

    element.getValue().call(ElementEvent.LEFT, element.getValue());
    scheduler.advance(0);

    assertThat(mode.get()).isNotEqualTo(GolemMode.STAY);
    verify(window, times(2)).updateElement(eq(0), eq(HEADER_ROW), any(Element.class));
    assertThat(windows.constructed()).hasSize(1);
    assertThat(scheduler.violations()).isEmpty();
  }

  @Test
  void closingTheWindowStopsTheRefreshAndLeavesNothingScheduled() {
    openAndDraw();
    UIWindow window = onlyWindow();
    scheduler.advance(LiveRefresh.POLL_TICKS * 2);
    closeCallbacks.getFirst().run(window);
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
    UIWindow window = onlyWindow();
    visibility.get(window).set(false);
    carried.set(List.of("cobblestone-99"));

    scheduler.advance(LiveRefresh.POLL_TICKS * 20);

    verify(window, never()).updateElement(anyInt(), anyInt(), any());
    verify(window, never()).close();
    assertThat(scheduler.pending()).isZero();
    hud.verify(() -> AdaptHud.actionBar(any(Player.class), anyString()), never());
  }

  @Test
  void aPlayerWhoQuitsStopsTheRefresh() {
    openAndDraw();
    UIWindow window = onlyWindow();
    online.set(false);
    carried.set(List.of("cobblestone-99"));

    scheduler.advance(LiveRefresh.POLL_TICKS * 20);

    verify(window, never()).updateElement(anyInt(), anyInt(), any());
    assertThat(scheduler.pending()).isZero();
  }

  @Test
  void aGolemThatIsReleasedOrPackedClosesTheGui() {
    openAndDraw();
    UIWindow window = onlyWindow();
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
    UIWindow window = onlyWindow();
    valid.set(false);

    scheduler.advance(LiveRefresh.POLL_TICKS);

    verify(window, times(1)).close();
    assertThat(scheduler.pending()).isZero();
  }

  @Test
  void aGolemSchedulerThatRetiresTheReadClosesTheGui() {
    openAndDraw();
    UIWindow window = onlyWindow();

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

  private void openAndDraw() {
    GolemGui.open(player, handle, gestures);
    scheduler.advance(0);
    assertThat(windows.constructed()).hasSize(1);
    assertThat(scheduler.pending()).as("one pending poll after the first draw").isEqualTo(1);
  }

  private Element equipmentElement(String id) {
    ArgumentCaptor<Element> placed = ArgumentCaptor.forClass(Element.class);
    verify(onlyWindow(), times(GolemEquipmentSlot.values().length)).setElement(anyInt(), eq(EQUIPMENT_ROW), placed.capture());
    return placed.getAllValues().stream().filter(element -> id.equals(element.getId())).findFirst().orElseThrow();
  }

  private UIWindow onlyWindow() {
    return windows.constructed().getFirst();
  }

  private static ItemStack stack(String encoded) {
    int split = encoded.lastIndexOf('-');
    return GolemTestItems.stack(Material.valueOf(encoded.substring(0, split).toUpperCase(Locale.ROOT)), Integer.parseInt(encoded.substring(split + 1)));
  }
}
