package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BoneLatticeEditorTest extends AdaptTestBase {
  private static final Set<GolemRuneType> ALLOWED = EnumSet.of(GolemRuneType.SWIFTNESS, GolemRuneType.MIGHT, GolemRuneType.EMBER, GolemRuneType.BULWARK);

  private static final ItemRef EMPTY = mock(ItemRef.class);

  private final PlayerData data = new PlayerData();
  private final GolemRoster roster = data.getGolemancy();
  private final AdaptPlayer owner = mock(AdaptPlayer.class);
  private final PlayerRef player = mock(PlayerRef.class);
  private final PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
  private final WorldRef world = mock(WorldRef.class);
  private final Map<ItemRef, GolemRune> runeItems = new HashMap<>();
  private MockedStatic<GolemItems> items;
  private MockedStatic<Tasks> scheduling;

  @BeforeEach
  void setUp() {
    items = mockStatic(GolemItems.class);
    items.when(() -> GolemItems.runeOf(any())).thenAnswer(call -> Optional.ofNullable(runeItems.get(call.<ItemRef>getArgument(0))));
    items.when(() -> GolemItems.rune(any(GolemRune.class))).thenAnswer(call -> runeItem(call.getArgument(0), 1));
    scheduling = mockStatic(Tasks.class);
    scheduling.when(() -> Tasks.runEntity(any(EntityRef.class), any(Runnable.class))).thenAnswer(call -> {
      call.<Runnable>getArgument(1).run();
      return true;
    });
    scheduling.when(() -> Tasks.runEntity(any(EntityRef.class), any(Runnable.class), any(Runnable.class))).thenAnswer(call -> {
      call.<Runnable>getArgument(1).run();
      return true;
    });
    lenient().when(owner.getData()).thenReturn(data);
    lenient().when(player.inventory()).thenReturn(inventory);
    lenient().when(player.location()).thenReturn(new Loc(world, 0D, 64D, 0D));
    lenient().when(inventory.addItem(any(ItemRef.class))).thenReturn(List.of());
    ItemOps itemOps = mock(ItemOps.class);
    lenient().when(itemOps.empty()).thenReturn(EMPTY);
    lenient().when(AdaptCore.platform().host().items()).thenReturn(itemOps);
  }

  @AfterEach
  void tearDown() {
    scheduling.close();
    items.close();
  }

  @Test
  void insertingTakesOneRuneFromTheCursorSavesThePlayerThenStoresIt() {
    ItemRef cursor = runeItem(new GolemRune(GolemRuneType.MIGHT, 2), 3);
    when(inventory.cursor()).thenReturn(cursor);

    BoneLatticeEditor.Outcome outcome = editor(2).insert(player);

    assertThat(outcome).isEqualTo(new BoneLatticeEditor.Outcome(BoneLatticeEditor.Result.SLOTTED, new GolemRune(GolemRuneType.MIGHT, 2)));
    assertThat(cursor.amount()).isEqualTo(2);
    assertThat(roster.lattice()).containsExactly(new GolemRune(GolemRuneType.MIGHT, 2));
    InOrder order = inOrder(inventory, player, owner);
    order.verify(inventory).setCursor(cursor);
    order.verify(player).saveData();
    order.verify(owner).saveNow();
    verify(owner, never()).requestSave();
  }

  @Test
  void theLastRuneOnTheCursorLeavesTheCursorEmpty() {
    ItemRef cursor = runeItem(new GolemRune(GolemRuneType.EMBER, 1), 1);
    when(inventory.cursor()).thenReturn(cursor);

    editor(2).insert(player);

    verify(inventory).setCursor(EMPTY);
  }

  @Test
  void refusedRunesStayOnTheCursorAndNothingIsSaved() {
    roster.addLattice(new GolemRune(GolemRuneType.MIGHT, 1));

    assertThat(insertFrom(new GolemRune(GolemRuneType.ORDER, 1), 3).result()).isEqualTo(BoneLatticeEditor.Result.ASPECT);
    assertThat(insertFrom(new GolemRune(GolemRuneType.MIGHT, 3), 3).result()).isEqualTo(BoneLatticeEditor.Result.DUPLICATE);
    assertThat(editor(1).insert(cursorOf(new GolemRune(GolemRuneType.EMBER, 2))).result()).isEqualTo(BoneLatticeEditor.Result.NO_SLOT);

    verify(inventory, never()).setCursor(any());
    verify(player, never()).saveData();
    verify(owner, never()).saveNow();
    assertThat(roster.lattice()).containsExactly(new GolemRune(GolemRuneType.MIGHT, 1));
  }

  @Test
  void anEmptyCursorNeedsARuneFirst() {
    when(inventory.cursor()).thenReturn(mock(ItemRef.class));

    assertThat(editor(2).insert(player).result()).isEqualTo(BoneLatticeEditor.Result.NO_RUNE);
    verify(owner, never()).saveNow();
  }

  @Test
  void takingARuneWritesTheRosterBeforeTheRuneIsHandedBackOnce() {
    GolemRune ember = new GolemRune(GolemRuneType.EMBER, 2);
    roster.addLattice(ember);

    BoneLatticeEditor.Outcome first = editor(2).take(player, 0, ember);
    BoneLatticeEditor.Outcome second = editor(2).take(player, 0, ember);

    assertThat(first.result()).isEqualTo(BoneLatticeEditor.Result.TAKEN);
    assertThat(second.result()).isEqualTo(BoneLatticeEditor.Result.STALE);
    assertThat(roster.lattice()).isEmpty();
    verify(inventory, times(1)).addItem(any(ItemRef.class));
    verify(owner, times(1)).saveNow();
    verify(owner, never()).requestSave();
    InOrder order = inOrder(owner, inventory, player);
    order.verify(owner).saveNow();
    order.verify(inventory).addItem(any(ItemRef.class));
    order.verify(player).saveData();
  }

  @Test
  void atLevelOneEverySlotIsLockedButSlottedRunesStillComeOut() {
    GolemRune might = new GolemRune(GolemRuneType.MIGHT, 2);
    roster.addLattice(might);

    assertThat(editor(0).insert(cursorOf(new GolemRune(GolemRuneType.EMBER, 1))).result()).isEqualTo(BoneLatticeEditor.Result.NO_SLOT);
    assertThat(editor(0).take(player, 0, might).result()).isEqualTo(BoneLatticeEditor.Result.TAKEN);

    assertThat(roster.lattice()).isEmpty();
    verify(inventory, times(1)).addItem(any(ItemRef.class));
  }

  private BoneLatticeEditor.Outcome insertFrom(GolemRune rune, int slots) {
    return editor(slots).insert(cursorOf(rune));
  }

  private PlayerRef cursorOf(GolemRune rune) {
    ItemRef cursor = runeItem(rune, 1);
    when(inventory.cursor()).thenReturn(cursor);
    return player;
  }

  private BoneLatticeEditor editor(int slots) {
    return new BoneLatticeEditor(owner, slots, ALLOWED);
  }

  private ItemRef runeItem(GolemRune rune, int amount) {
    ItemRef item = mock(ItemRef.class);
    AtomicInteger count = new AtomicInteger(amount);
    lenient().when(item.amount()).thenAnswer(call -> count.get());
    lenient().doAnswer(call -> {
      count.set(call.getArgument(0));
      return null;
    }).when(item).setAmount(anyInt());
    lenient().when(item.copy()).thenReturn(item);
    runeItems.put(item, rune);
    return item;
  }
}
