package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
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

  private final GolemRoster roster = new GolemRoster();
  private final Runnable saveRoster = mock(Runnable.class);
  private final Player player = mock(Player.class);
  private final PlayerInventory inventory = mock(PlayerInventory.class);
  private final World world = mock(World.class);
  private final Map<ItemStack, GolemRune> runeItems = new HashMap<>();
  private MockedStatic<GolemItems> items;
  private MockedStatic<J> scheduling;

  @BeforeEach
  void setUp() {
    items = mockStatic(GolemItems.class);
    items.when(() -> GolemItems.runeOf(any())).thenAnswer(call -> Optional.ofNullable(runeItems.get(call.<ItemStack>getArgument(0))));
    items.when(() -> GolemItems.rune(any(GolemRune.class))).thenAnswer(call -> runeItem(call.getArgument(0), 1));
    scheduling = mockStatic(J.class);
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class))).thenAnswer(call -> {
      call.<Runnable>getArgument(1).run();
      return true;
    });
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class), any(Runnable.class))).thenAnswer(call -> {
      call.<Runnable>getArgument(1).run();
      return true;
    });
    lenient().when(player.getInventory()).thenReturn(inventory);
    lenient().when(player.getLocation()).thenReturn(new Location(world, 0D, 64D, 0D));
    lenient().when(inventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());
  }

  @AfterEach
  void tearDown() {
    scheduling.close();
    items.close();
  }

  @Test
  void insertingTakesOneRuneFromTheCursorSavesThePlayerThenStoresIt() {
    ItemStack cursor = runeItem(new GolemRune(GolemRuneType.MIGHT, 2), 3);
    when(player.getItemOnCursor()).thenReturn(cursor);

    BoneLatticeEditor.Outcome outcome = editor(2).insert(player);

    assertThat(outcome).isEqualTo(new BoneLatticeEditor.Outcome(BoneLatticeEditor.Result.SLOTTED, new GolemRune(GolemRuneType.MIGHT, 2)));
    assertThat(cursor.getAmount()).isEqualTo(2);
    assertThat(roster.lattice()).containsExactly(new GolemRune(GolemRuneType.MIGHT, 2));
    InOrder order = inOrder(player, saveRoster);
    order.verify(player).setItemOnCursor(cursor);
    order.verify(player).saveData();
    order.verify(saveRoster).run();
  }

  @Test
  void theLastRuneOnTheCursorLeavesTheCursorEmpty() {
    ItemStack cursor = runeItem(new GolemRune(GolemRuneType.EMBER, 1), 1);
    when(player.getItemOnCursor()).thenReturn(cursor);

    editor(2).insert(player);

    verify(player).setItemOnCursor(isNull());
  }

  @Test
  void refusedRunesStayOnTheCursorAndNothingIsSaved() {
    roster.addLattice(new GolemRune(GolemRuneType.MIGHT, 1));

    assertThat(insertFrom(new GolemRune(GolemRuneType.ORDER, 1), 3).result()).isEqualTo(BoneLatticeEditor.Result.ASPECT);
    assertThat(insertFrom(new GolemRune(GolemRuneType.MIGHT, 3), 3).result()).isEqualTo(BoneLatticeEditor.Result.DUPLICATE);
    assertThat(editor(1).insert(cursorOf(new GolemRune(GolemRuneType.EMBER, 2))).result()).isEqualTo(BoneLatticeEditor.Result.NO_SLOT);

    verify(player, never()).setItemOnCursor(any());
    verify(player, never()).saveData();
    verify(saveRoster, never()).run();
    assertThat(roster.lattice()).containsExactly(new GolemRune(GolemRuneType.MIGHT, 1));
  }

  @Test
  void anEmptyCursorNeedsARuneFirst() {
    when(player.getItemOnCursor()).thenReturn(mock(ItemStack.class));

    assertThat(editor(2).insert(player).result()).isEqualTo(BoneLatticeEditor.Result.NO_RUNE);
    verify(saveRoster, never()).run();
  }

  @Test
  void takingARuneSavesTheLatticeThenHandsTheRuneBackOnce() {
    GolemRune ember = new GolemRune(GolemRuneType.EMBER, 2);
    roster.addLattice(ember);

    BoneLatticeEditor.Outcome first = editor(2).take(player, 0, ember);
    BoneLatticeEditor.Outcome second = editor(2).take(player, 0, ember);

    assertThat(first.result()).isEqualTo(BoneLatticeEditor.Result.TAKEN);
    assertThat(second.result()).isEqualTo(BoneLatticeEditor.Result.STALE);
    assertThat(roster.lattice()).isEmpty();
    verify(inventory, times(1)).addItem(any(ItemStack[].class));
    verify(saveRoster, times(1)).run();
    InOrder order = inOrder(saveRoster, inventory, player);
    order.verify(saveRoster).run();
    order.verify(inventory).addItem(any(ItemStack[].class));
    order.verify(player).saveData();
  }

  private BoneLatticeEditor.Outcome insertFrom(GolemRune rune, int slots) {
    return editor(slots).insert(cursorOf(rune));
  }

  private Player cursorOf(GolemRune rune) {
    ItemStack cursor = runeItem(rune, 1);
    when(player.getItemOnCursor()).thenReturn(cursor);
    return player;
  }

  private BoneLatticeEditor editor(int slots) {
    return new BoneLatticeEditor(roster, saveRoster, slots, ALLOWED);
  }

  private ItemStack runeItem(GolemRune rune, int amount) {
    ItemStack item = mock(ItemStack.class);
    AtomicInteger count = new AtomicInteger(amount);
    lenient().when(item.getAmount()).thenAnswer(call -> count.get());
    lenient().doAnswer(call -> {
      count.set(call.getArgument(0));
      return null;
    }).when(item).setAmount(anyInt());
    lenient().when(item.clone()).thenReturn(item);
    runeItems.put(item, rune);
    return item;
  }
}
