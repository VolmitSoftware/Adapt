package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemGuiItemsTest {
  private final World world = mock(World.class);
  private final Location playerSpot = new Location(world, 1.5D, 64D, 1.5D);
  private final Location golemSpot = new Location(world, 8.5D, 64D, 8.5D);
  private final IronGolem golem = mock(IronGolem.class);
  private final Player player = mock(Player.class);
  private final PlayerInventory inventory = mock(PlayerInventory.class);
  private final ItemStack rune = mock(ItemStack.class);
  private MockedStatic<J> scheduling;

  @BeforeEach
  void setUp() {
    scheduling = mockStatic(J.class);
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class), any(Runnable.class))).thenReturn(true);
    scheduling.when(() -> J.runAt(any(Location.class), any(Runnable.class))).thenReturn(true);
    lenient().when(player.getLocation()).thenAnswer(invocation -> playerSpot.clone());
    lenient().when(player.getWorld()).thenReturn(world);
    lenient().when(player.getInventory()).thenReturn(inventory);
    lenient().when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
  }

  @AfterEach
  void tearDown() {
    scheduling.close();
  }

  @Test
  void aSlotTaskRunsOnTheGolemsScheduler() {
    Runnable slot = mock(Runnable.class);

    assertThat(GolemGuiItems.toGolem(golem, player, rune, slot)).isTrue();

    scheduling.verify(() -> J.runEntity(eq(golem), eq(slot), any(Runnable.class)));
  }

  @Test
  void aRetiredGolemReturnsTheRuneToThePlayer() {
    GolemGuiItems.toGolem(golem, player, rune, mock(Runnable.class));
    ArgumentCaptor<Runnable> retired = ArgumentCaptor.forClass(Runnable.class);
    scheduling.verify(() -> J.runEntity(eq(golem), any(Runnable.class), retired.capture()));

    retired.getValue().run();
    ArgumentCaptor<Runnable> give = ArgumentCaptor.forClass(Runnable.class);
    scheduling.verify(() -> J.runEntity(eq(player), give.capture(), any(Runnable.class)));
    give.getValue().run();

    verify(inventory).addItem(rune);
    verify(world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
  }

  @Test
  void aRetiredPlayerGetsTheRuneDroppedAtTheGolemThroughARegionTask() {
    GolemGuiItems.toPlayer(player, rune, golemSpot);
    ArgumentCaptor<Runnable> retired = ArgumentCaptor.forClass(Runnable.class);
    scheduling.verify(() -> J.runEntity(eq(player), any(Runnable.class), retired.capture()));

    retired.getValue().run();
    ArgumentCaptor<Runnable> drop = ArgumentCaptor.forClass(Runnable.class);
    scheduling.verify(() -> J.runAt(eq(golemSpot), drop.capture()));
    verify(world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
    drop.getValue().run();

    verify(world).dropItemNaturally(golemSpot, rune);
    verify(inventory, never()).addItem(any(ItemStack.class));
  }

  @Test
  void aPlayerThatCannotBeScheduledGetsTheRuneDroppedAtTheFallback() {
    scheduling.when(() -> J.runEntity(eq(player), any(Runnable.class), any(Runnable.class))).thenReturn(false);

    GolemGuiItems.toPlayer(player, rune, golemSpot);
    ArgumentCaptor<Runnable> drop = ArgumentCaptor.forClass(Runnable.class);
    scheduling.verify(() -> J.runAt(eq(golemSpot), drop.capture()));
    drop.getValue().run();

    verify(world).dropItemNaturally(golemSpot, rune);
  }

  @Test
  void itemsThatDoNotFitDropAtThePlayer() {
    ItemStack leftover = mock(ItemStack.class);
    HashMap<Integer, ItemStack> leftovers = new HashMap<>();
    leftovers.put(0, leftover);
    when(inventory.addItem(rune)).thenReturn(leftovers);

    GolemGuiItems.give(player, rune);

    verify(world).dropItemNaturally(playerSpot, leftover);
  }
}
