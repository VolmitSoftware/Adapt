package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeaborneBrineSkinDamageEventTest extends AdaptTestBase {
  private SeaborneBrineSkin adaptation;
  private Player player;
  private EntityDamageEvent event;
  private UUID playerId;

  @BeforeEach
  void prepareDamageEvent() throws ReflectiveOperationException {
    adaptation = spy(new SeaborneBrineSkin());
    player = mock(Player.class);
    event = mock(EntityDamageEvent.class);
    playerId = UUID.randomUUID();
    when(player.getUniqueId()).thenReturn(playerId);
    when(player.getWorld()).thenReturn(mock(World.class));
    when(event.getEntity()).thenReturn(player);
    when(event.getDamage()).thenReturn(20D);
    doReturn(5).when(adaptation).getActiveLevel(player);
    Field feedbackCooldown = SeaborneBrineSkin.class.getDeclaredField("reductionFx");
    feedbackCooldown.setAccessible(true);
    feedbackCooldown.set(adaptation, mock(Cooldowns.class));
    adaptation.activateRuntime();
  }

  @ParameterizedTest
  @CsvSource({"1,18.24", "2,17.68", "3,17.12", "4,16.56", "5,16.0"})
  void wetDamageScalesAtEveryLevelWithoutReplayingFeedbackOnCooldown(int level, double expectedDamage) {
    doReturn(level).when(adaptation).getActiveLevel(player);
    when(player.isInWater()).thenReturn(true);

    dispatch();

    verify(event).setDamage(doubleThat(value -> Math.abs(value - expectedDamage) < 1.0E-9D));
    verify(player, never()).getLocation();
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
    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
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
