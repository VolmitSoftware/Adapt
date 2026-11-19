package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.PlayerStateRegistry;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Cancellable;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
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
    AlwaysProcPact pact = new AlwaysProcPact();
    PlayerRef player = player();
    List<Runnable> deferred = new ArrayList<>();

    try (MockedStatic<Tasks> tasks = mockStatic(Tasks.class);
         MockedStatic<AdaptAttributeService> attributes = mockStatic(AdaptAttributeService.class)) {
      tasks.when(() -> Tasks.runEntity(eq(player), any(Runnable.class), eq(1))).thenAnswer(call -> {
        deferred.add(call.getArgument(1));
        return true;
      });
      attributes.when(AdaptAttributeService::get).thenReturn(mock(AdaptAttributeService.class));

      pact.on(new EntityDamageEvent(player, DamageCause.ENTITY_ATTACK, Id.minecraft("player_attack"), 8D, FinalDamage.IDENTITY));

      verify(player, never()).addEffect(any(EffectInstance.class));
      assertThat(pact.sacrificed).isEqualTo(8);
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

  private static final class AlwaysProcPact extends TragoulBloodPact {
    private int sacrificed;

    private AlwaysProcPact() {
      Config config = new Config();
      config.procChanceBase = 1D;
      config.maxProcChance = 1D;
      setConfig(config);
    }

    @Override
    public void withAdaptedPlayer(PlayerRef player, Cancellable event, Runnable action) {
      action.run();
    }

    @Override
    public int getActiveLevel(PlayerRef player) {
      return getMaxLevel();
    }

    @Override
    protected void addStat(PlayerRef player, String stat, double amount) {
      sacrificed += (int) amount;
    }

    @Override
    public void xp(PlayerRef player, double amount) {
    }

    @Override
    protected FxEmitter fx(EntityRef entity, FxPriority priority) {
      return mock(FxEmitter.class, RETURNS_SELF);
    }

    @Override
    protected FxTimeline timeline(EntityRef entity) {
      return mock(FxTimeline.class, RETURNS_SELF);
    }
  }
}
