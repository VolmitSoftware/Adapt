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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
  void aSlotTaskRunsOnceOnTheGolemsScheduler() {
    Runnable slot = mock(Runnable.class);

    GolemGuiItems.toGolem(golem, player, rune, slot);
    ArgumentCaptor<Runnable> primary = ArgumentCaptor.forClass(Runnable.class);
    ArgumentCaptor<Runnable> retired = ArgumentCaptor.forClass(Runnable.class);
    scheduling.verify(() -> J.runEntity(eq(golem), primary.capture(), retired.capture()));
    primary.getValue().run();
    retired.getValue().run();

    verify(slot, times(1)).run();
    verify(inventory, never()).addItem(any(ItemStack.class));
    scheduling.verify(() -> J.runEntity(eq(player), any(Runnable.class), any(Runnable.class)), never());
  }

  @Test
  void aGolemThatRetiresLaterReturnsTheRuneToThePlayerOnce() {
    Runnable slot = mock(Runnable.class);

    GolemGuiItems.toGolem(golem, player, rune, slot);
    ArgumentCaptor<Runnable> primary = ArgumentCaptor.forClass(Runnable.class);
    ArgumentCaptor<Runnable> retired = ArgumentCaptor.forClass(Runnable.class);
    scheduling.verify(() -> J.runEntity(eq(golem), primary.capture(), retired.capture()));
    retired.getValue().run();
    primary.getValue().run();
    retired.getValue().run();
    runPlayerGives();

    verify(slot, never()).run();
    verify(inventory, times(1)).addItem(rune);
    verify(world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
  }

  @Test
  void anAlreadyRetiredGolemReturnsTheRuneOnceWhenSchedulingAlsoFails() {
    scheduling.when(() -> J.runEntity(eq(golem), any(Runnable.class), any(Runnable.class))).thenAnswer(invocation -> {
      invocation.<Runnable>getArgument(2).run();
      return false;
    });

    GolemGuiItems.toGolem(golem, player, rune, mock(Runnable.class));
    runPlayerGives();

    verify(inventory, times(1)).addItem(rune);
  }

  @Test
  void aGolemRefusedWithoutRetiringReturnsTheRuneInlineOnce() {
    scheduling.when(() -> J.runEntity(eq(golem), any(Runnable.class), any(Runnable.class))).thenReturn(false);
    Runnable slot = mock(Runnable.class);

    GolemGuiItems.toGolem(golem, player, rune, slot);

    verify(slot, never()).run();
    verify(inventory, times(1)).addItem(rune);
    scheduling.verify(() -> J.runEntity(eq(player), any(Runnable.class), any(Runnable.class)), never());
  }

  @Test
  void aPlayerThatRetiresLaterGetsOneDropAtTheGolemThroughARegionTask() {
    GolemGuiItems.toPlayer(player, rune, golemSpot);
    ArgumentCaptor<Runnable> primary = ArgumentCaptor.forClass(Runnable.class);
    ArgumentCaptor<Runnable> retired = ArgumentCaptor.forClass(Runnable.class);
    scheduling.verify(() -> J.runEntity(eq(player), primary.capture(), retired.capture()));

    retired.getValue().run();
    primary.getValue().run();
    retired.getValue().run();
    verify(world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
    runRegionDrops();

    verify(world, times(1)).dropItemNaturally(golemSpot, rune);
    verify(inventory, never()).addItem(any(ItemStack.class));
  }

  @Test
  void anAlreadyRetiredPlayerGetsExactlyOneDropWhenSchedulingAlsoFails() {
    scheduling.when(() -> J.runEntity(eq(player), any(Runnable.class), any(Runnable.class))).thenAnswer(invocation -> {
      invocation.<Runnable>getArgument(2).run();
      return false;
    });

    GolemGuiItems.toPlayer(player, rune, golemSpot);
    runRegionDrops();

    scheduling.verify(() -> J.runAt(eq(golemSpot), any(Runnable.class)), times(1));
    verify(world, times(1)).dropItemNaturally(golemSpot, rune);
    verify(inventory, never()).addItem(any(ItemStack.class));
  }

  @Test
  void aPlayerRefusedWithoutRetiringGetsOneDropAtTheFallback() {
    scheduling.when(() -> J.runEntity(eq(player), any(Runnable.class), any(Runnable.class))).thenReturn(false);

    GolemGuiItems.toPlayer(player, rune, golemSpot);
    runRegionDrops();

    scheduling.verify(() -> J.runAt(eq(golemSpot), any(Runnable.class)), times(1));
    verify(world, times(1)).dropItemNaturally(golemSpot, rune);
  }

  @Test
  void aTakeRunsOnceOnTheGolemsSchedulerWithoutWarning() {
    Runnable take = mock(Runnable.class);

    GolemGuiItems.fromGolem(golem, player, take);
    ArgumentCaptor<Runnable> primary = ArgumentCaptor.forClass(Runnable.class);
    ArgumentCaptor<Runnable> retired = ArgumentCaptor.forClass(Runnable.class);
    scheduling.verify(() -> J.runEntity(eq(golem), primary.capture(), retired.capture()));
    primary.getValue().run();
    retired.getValue().run();

    verify(take, times(1)).run();
    scheduling.verify(() -> J.runEntity(eq(player), any(Runnable.class)), never());
  }

  @Test
  void anAlreadyRetiredGolemTakesNothingAndWarnsOnce() {
    scheduling.when(() -> J.runEntity(eq(golem), any(Runnable.class), any(Runnable.class))).thenAnswer(invocation -> {
      invocation.<Runnable>getArgument(2).run();
      return false;
    });
    Runnable take = mock(Runnable.class);

    GolemGuiItems.fromGolem(golem, player, take);

    verify(take, never()).run();
    scheduling.verify(() -> J.runEntity(eq(player), any(Runnable.class)), times(1));
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

  private void runPlayerGives() {
    ArgumentCaptor<Runnable> gives = ArgumentCaptor.forClass(Runnable.class);
    scheduling.verify(() -> J.runEntity(eq(player), gives.capture(), any(Runnable.class)), atLeast(0));
    for (Runnable give : gives.getAllValues()) {
      give.run();
    }
  }

  private void runRegionDrops() {
    ArgumentCaptor<Runnable> drops = ArgumentCaptor.forClass(Runnable.class);
    scheduling.verify(() -> J.runAt(any(Location.class), drops.capture()), atLeast(0));
    for (Runnable drop : drops.getAllValues()) {
      drop.run();
    }
  }
}
