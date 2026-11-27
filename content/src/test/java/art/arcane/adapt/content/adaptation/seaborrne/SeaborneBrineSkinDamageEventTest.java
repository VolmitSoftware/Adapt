package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.EngineTestBase;
import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.doubleThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeaborneBrineSkinDamageEventTest extends EngineTestBase {
  private SeaborneBrineSkin adaptation;
  private PlayerRef player;
  private EntityDamageEvent event;
  private UUID playerId;

  @BeforeEach
  void prepareDamageEvent() throws ReflectiveOperationException {
    SeaborneBrineSkin original = TestContent.adaptation(Id.adapt("seaborne"), SeaborneBrineSkin::new);
    adaptation = spy(original);
    player = mock(PlayerRef.class);
    event = mock(EntityDamageEvent.class);
    playerId = UUID.randomUUID();
    when(player.uuid()).thenReturn(playerId);
    when(player.world()).thenReturn(mock(WorldRef.class));
    when(event.entity()).thenReturn(player);
    when(event.damage()).thenReturn(20D);
    doReturn(5).when(adaptation).getActiveLevel(player);
    Field feedbackCooldown = SeaborneBrineSkin.class.getDeclaredField("reductionFx");
    feedbackCooldown.setAccessible(true);
    feedbackCooldown.set(adaptation, mock(Cooldowns.class));
    ComponentHost.of(original).activateRuntime();
  }

  @ParameterizedTest
  @CsvSource({"1,18.24", "2,17.68", "3,17.12", "4,16.56", "5,16.0"})
  void wetDamageScalesAtEveryLevelWithoutReplayingFeedbackOnCooldown(int level, double expectedDamage) {
    doReturn(level).when(adaptation).getActiveLevel(player);
    when(player.isInWater()).thenReturn(true);

    dispatch();

    verify(event).setDamage(doubleThat(value -> Math.abs(value - expectedDamage) < 1.0E-9D));
    verify(player, never()).location();
  }

  @Test
  void dryPlayerWithoutLingerTakesUnmodifiedDamage() {
    dispatch();

    verify(event, never()).setDamage(anyDouble());
  }

  @Test
  void unlearnedPlayerInWaterTakesUnmodifiedDamage() {
    doReturn(0).when(adaptation).getActiveLevel(player);
    when(player.isInWater()).thenReturn(true);

    dispatch();

    verify(event, never()).setDamage(anyDouble());
  }

  @Test
  void activeLingerProtectsDryPlayer() throws ReflectiveOperationException {
    wetUntil().put(playerId, Long.MAX_VALUE);

    dispatch();

    verify(event).setDamage(16D);
  }

  @Test
  void expiredLingerDoesNotProtectDryPlayer() throws ReflectiveOperationException {
    wetUntil().put(playerId, 1L);

    dispatch();

    verify(event, never()).setDamage(anyDouble());
  }

  @Test
  void cancelledDamageIsUntouchedByTheActualRuntimeGuard() {
    when(event.isCancelled()).thenReturn(true);
    when(player.isInWater()).thenReturn(true);

    dispatch();

    verify(event, never()).setDamage(anyDouble());
  }

  @Test
  void retiredRuntimeCannotReduceDamage() {
    adaptation.unregister();
    when(player.isInWater()).thenReturn(true);

    dispatch();

    verify(event, never()).setDamage(anyDouble());
  }

  private void dispatch() {
    try (MockedStatic<Tasks> scheduling = SchedulerForwarding.mockTasks()) {
      adaptation.on(event);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<UUID, Long> wetUntil() throws ReflectiveOperationException {
    Field field = SeaborneBrineSkin.class.getDeclaredField("wetUntil");
    field.setAccessible(true);
    return (Map<UUID, Long>) field.get(adaptation);
  }
}
