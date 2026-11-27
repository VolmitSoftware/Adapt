package art.arcane.adapt.content.golem.gui;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemGuiItemsTest {
  private final WorldRef world = mock(WorldRef.class);
  private final Loc playerSpot = new Loc(world, 1.5D, 64D, 1.5D);
  private final Loc golemSpot = new Loc(world, 8.5D, 64D, 8.5D);
  private final MobRef golem = mock(MobRef.class);
  private final PlayerRef player = mock(PlayerRef.class);
  private final PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
  private final ItemRef rune = mock(ItemRef.class);
  private MockedStatic<AdaptApi> scheduling;
  private final AdaptScheduler adaptScheduler = mock(AdaptScheduler.class);

  @BeforeEach
  void setUp() {
    scheduling = mockStatic(AdaptApi.class, CALLS_REAL_METHODS);
    scheduling.when(AdaptApi::scheduler).thenReturn(adaptScheduler);
    when(adaptScheduler.runEntity(any(EntityRef.class), any(Runnable.class), any(Runnable.class))).thenReturn(true);
    when(adaptScheduler.runAt(any(Loc.class), any(Runnable.class))).thenReturn(true);
    lenient().when(player.location()).thenReturn(playerSpot);
    lenient().when(player.world()).thenReturn(world);
    lenient().when(player.inventory()).thenReturn(inventory);
    lenient().when(inventory.addItem(any(ItemRef.class))).thenReturn(List.of());
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
    verify(adaptScheduler).runEntity(eq(golem), primary.capture(), retired.capture());
    primary.getValue().run();
    retired.getValue().run();

    verify(slot, times(1)).run();
    verify(inventory, never()).addItem(any(ItemRef.class));
    verify(adaptScheduler, never()).runEntity(eq(player), any(Runnable.class), any(Runnable.class));
  }

  @Test
  void aGolemThatRetiresLaterReturnsTheRuneToThePlayerOnce() {
    Runnable slot = mock(Runnable.class);

    GolemGuiItems.toGolem(golem, player, rune, slot);
    ArgumentCaptor<Runnable> primary = ArgumentCaptor.forClass(Runnable.class);
    ArgumentCaptor<Runnable> retired = ArgumentCaptor.forClass(Runnable.class);
    verify(adaptScheduler).runEntity(eq(golem), primary.capture(), retired.capture());
    retired.getValue().run();
    primary.getValue().run();
    retired.getValue().run();
    runPlayerGives();

    verify(slot, never()).run();
    verify(inventory, times(1)).addItem(rune);
    verify(world, never()).dropItemNaturally(any(Vec3.class), any(ItemRef.class));
  }

  @Test
  void anAlreadyRetiredGolemReturnsTheRuneOnceWhenSchedulingAlsoFails() {
    when(adaptScheduler.runEntity(eq(golem), any(Runnable.class), any(Runnable.class))).thenAnswer(invocation -> {
      invocation.<Runnable>getArgument(2).run();
      return false;
    });

    GolemGuiItems.toGolem(golem, player, rune, mock(Runnable.class));
    runPlayerGives();

    verify(inventory, times(1)).addItem(rune);
  }

  @Test
  void aGolemRefusedWithoutRetiringReturnsTheRuneInlineOnce() {
    when(adaptScheduler.runEntity(eq(golem), any(Runnable.class), any(Runnable.class))).thenReturn(false);
    Runnable slot = mock(Runnable.class);

    GolemGuiItems.toGolem(golem, player, rune, slot);

    verify(slot, never()).run();
    verify(inventory, times(1)).addItem(rune);
    verify(adaptScheduler, never()).runEntity(eq(player), any(Runnable.class), any(Runnable.class));
  }

  @Test
  void aPlayerThatRetiresLaterGetsOneDropAtTheGolemThroughARegionTask() {
    GolemGuiItems.toPlayer(player, rune, golemSpot);
    ArgumentCaptor<Runnable> primary = ArgumentCaptor.forClass(Runnable.class);
    ArgumentCaptor<Runnable> retired = ArgumentCaptor.forClass(Runnable.class);
    verify(adaptScheduler).runEntity(eq(player), primary.capture(), retired.capture());

    retired.getValue().run();
    primary.getValue().run();
    retired.getValue().run();
    verify(world, never()).dropItemNaturally(any(Vec3.class), any(ItemRef.class));
    runRegionDrops();

    verify(world, times(1)).dropItemNaturally(golemSpot.position(), rune);
    verify(inventory, never()).addItem(any(ItemRef.class));
  }

  @Test
  void anAlreadyRetiredPlayerGetsExactlyOneDropWhenSchedulingAlsoFails() {
    when(adaptScheduler.runEntity(eq(player), any(Runnable.class), any(Runnable.class))).thenAnswer(invocation -> {
      invocation.<Runnable>getArgument(2).run();
      return false;
    });

    GolemGuiItems.toPlayer(player, rune, golemSpot);
    runRegionDrops();

    verify(adaptScheduler, times(1)).runAt(eq(golemSpot), any(Runnable.class));
    verify(world, times(1)).dropItemNaturally(golemSpot.position(), rune);
    verify(inventory, never()).addItem(any(ItemRef.class));
  }

  @Test
  void aPlayerRefusedWithoutRetiringGetsOneDropAtTheFallback() {
    when(adaptScheduler.runEntity(eq(player), any(Runnable.class), any(Runnable.class))).thenReturn(false);

    GolemGuiItems.toPlayer(player, rune, golemSpot);
    runRegionDrops();

    verify(adaptScheduler, times(1)).runAt(eq(golemSpot), any(Runnable.class));
    verify(world, times(1)).dropItemNaturally(golemSpot.position(), rune);
  }

  @Test
  void aTakeRunsOnceOnTheGolemsSchedulerWithoutWarning() {
    Runnable take = mock(Runnable.class);

    GolemGuiItems.fromGolem(golem, player, take);
    ArgumentCaptor<Runnable> primary = ArgumentCaptor.forClass(Runnable.class);
    ArgumentCaptor<Runnable> retired = ArgumentCaptor.forClass(Runnable.class);
    verify(adaptScheduler).runEntity(eq(golem), primary.capture(), retired.capture());
    primary.getValue().run();
    retired.getValue().run();

    verify(take, times(1)).run();
    verify(adaptScheduler, never()).runEntity(eq(player), any(Runnable.class));
  }

  @Test
  void anAlreadyRetiredGolemTakesNothingAndWarnsOnce() {
    when(adaptScheduler.runEntity(eq(golem), any(Runnable.class), any(Runnable.class))).thenAnswer(invocation -> {
      invocation.<Runnable>getArgument(2).run();
      return false;
    });
    Runnable take = mock(Runnable.class);

    GolemGuiItems.fromGolem(golem, player, take);

    verify(take, never()).run();
    verify(adaptScheduler, times(1)).runEntity(eq(player), any(Runnable.class));
  }

  @Test
  void itemsThatDoNotFitDropAtThePlayer() {
    ItemRef leftover = mock(ItemRef.class);
    when(inventory.addItem(rune)).thenReturn(List.of(leftover));

    GolemGuiItems.give(player, rune);

    verify(world).dropItemNaturally(playerSpot.position(), leftover);
  }

  private void runPlayerGives() {
    ArgumentCaptor<Runnable> gives = ArgumentCaptor.forClass(Runnable.class);
    verify(adaptScheduler, atLeast(0)).runEntity(eq(player), gives.capture(), any(Runnable.class));
    for (Runnable give : gives.getAllValues()) {
      give.run();
    }
  }

  private void runRegionDrops() {
    ArgumentCaptor<Runnable> drops = ArgumentCaptor.forClass(Runnable.class);
    verify(adaptScheduler, atLeast(0)).runAt(any(Loc.class), drops.capture());
    for (Runnable drop : drops.getAllValues()) {
      drop.run();
    }
  }
}
