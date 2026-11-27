package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.engine.adaptation.PlayerStateRegistry;
import art.arcane.adapt.engine.attribute.AdaptAttributeService;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.invocation.Invocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TragoulBloodPactHitOrderTest extends AdaptTestBase {
  @AfterEach
  void resetPlayerState() {
    PlayerStateRegistry.reset();
  }

  /** Absorption or Resistance granted inside the damage event would shield the very hit that paid for the pact on mod loaders. */
  @Test
  void pactBuffsStartAfterTheHitThatPaidForThem() {
    TragoulBloodPact pact = alwaysProcPact();
    PlayerRef player = player();
    List<Runnable> deferred = new ArrayList<>();

    try (MockedStatic<Tasks> tasks = SchedulerForwarding.mockTasks();
         MockedStatic<AdaptAttributeService> attributes = mockStatic(AdaptAttributeService.class)) {
      tasks.when(() -> Tasks.runEntity(eq(player), any(Runnable.class), eq(1))).thenAnswer(call -> {
        deferred.add(call.getArgument(1));
        return true;
      });
      attributes.when(AdaptAttributeService::get).thenReturn(mock(AdaptAttributeService.class));

      pact.on(new EntityDamageEvent(player, DamageCause.ENTITY_ATTACK, Id.minecraft("player_attack"), 8D, FinalDamage.IDENTITY));

      verify(player, never()).addEffect(any(EffectInstance.class));
      assertThat(statTotal(pact)).isEqualTo(8D);
      assertThat(deferred).hasSize(1);

      deferred.get(0).run();
    }

    verify(player, atLeastOnce()).addEffect(any(EffectInstance.class));
  }

  private static PlayerRef player() {
    PlayerRef player = mock(PlayerRef.class);
    WorldRef world = mock(WorldRef.class);
    when(player.uuid()).thenReturn(UUID.randomUUID());
    when(player.health()).thenReturn(200D);
    when(player.location()).thenAnswer(invocation -> new Loc(world, 0D, 100D, 0D));
    return player;
  }

  /** The sum of every stat the pact recorded. */
  private static double statTotal(TragoulBloodPact pact) {
    double total = 0D;
    for (Invocation invocation : mockingDetails(pact).getInvocations()) {
      if (invocation.getMethod().getName().equals("addStat")) {
        total += ((Number) invocation.getArgument(2)).doubleValue();
      }
    }
    return total;
  }

  /** A real Blood Pact that always procs at max level. */
  private static TragoulBloodPact alwaysProcPact() {
    TragoulBloodPact built = TestContent.adaptation(Id.adapt("tragoul"), TragoulBloodPact::new);
    built.getConfig().procChanceBase = 1D;
    built.getConfig().maxProcChance = 1D;
    TragoulBloodPact pact = spy(built);
    doAnswer(call -> {
      call.<Runnable>getArgument(2).run();
      return null;
    }).when(pact).withAdaptedPlayer(any(PlayerRef.class), any(), any(Runnable.class));
    doReturn(built.getMaxLevel()).when(pact).getActiveLevel(any(PlayerRef.class));
    doNothing().when(pact).xp(any(PlayerRef.class), anyDouble());
    return pact;
  }
}
