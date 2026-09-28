package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.PlayerStateRegistry;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KineticsRubberSoulLandingTest extends AdaptTestBase {
  private final List<Runnable> pending = new ArrayList<>();
  private Player player;
  private World world;
  private KineticsRubberSoul adaptation;
  private MockedStatic<J> scheduling;

  @BeforeEach
  void prepareLanding() {
    player = mock(Player.class);
    world = mock(World.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    when(player.isOnline()).thenReturn(true);
    when(player.isValid()).thenReturn(true);
    adaptation = spy(new KineticsRubberSoul());
    doReturn(true).when(adaptation).isRuntimeRegistered();
    doReturn(true).when(adaptation).hasActiveAdaptation(player);
    doNothing().when(adaptation).applySpringload(same(player), any(PlayerMoveEvent.class));
    scheduling = mockStatic(J.class);
    acceptScheduling();
  }

  @AfterEach
  void releaseLanding() {
    adaptation.unregister();
    scheduling.close();
  }

  @Test
  void finalStationaryLandingUsesGroundStateCommittedAfterItsOnlyMoveEvent() {
    PlayerMoveEvent event = movement();
    adaptation.on(event);

    assertThat(pending).hasSize(1);
    verify(adaptation, never()).applySpringload(any(), any());
    when(player.isOnGround()).thenReturn(true);
    pending.removeFirst().run();

    verify(adaptation).applySpringload(player, event);
    assertThat(pending).isEmpty();
  }

  @Test
  void subThresholdFinalDescentLandsWithoutAnotherMoveEventAfterTheFirstOwnerTick() {
    PlayerMoveEvent lastMovement = movement();
    adaptation.on(lastMovement);
    for (int tick = 0; tick < 4; tick++) {
      assertThat(pending).hasSize(1);
      pending.removeFirst().run();
    }
    verify(adaptation, never()).applySpringload(any(), any());

    when(player.isOnGround()).thenReturn(true);
    pending.removeFirst().run();

    verify(adaptation).applySpringload(player, lastMovement);
    assertThat(pending).isEmpty();
  }

  @Test
  void coalescesMovementPacketsWithoutLosingTheLatestProtectionDecision() {
    adaptation.on(movement());
    PlayerMoveEvent last = movement();
    adaptation.on(last);
    adaptation.on(last);
    assertThat(pending).hasSize(1);

    last.setCancelled(true);
    when(player.isOnGround()).thenReturn(true);
    pending.removeFirst().run();

    verify(adaptation, never()).applySpringload(any(), any());
  }

  @Test
  void groundMovementDoesNotScheduleOrRepeatALanding() {
    when(player.isOnGround()).thenReturn(true);
    adaptation.on(movement());
    assertThat(pending).isEmpty();

    when(player.isOnGround()).thenReturn(false);
    adaptation.on(movement());
    when(player.isOnGround()).thenReturn(true);
    pending.removeFirst().run();
    adaptation.on(movement());
    adaptation.on(movement());

    verify(adaptation, times(1)).applySpringload(any(), any());
    assertThat(pending).isEmpty();
  }

  @Test
  void anAirborneObservationRetainsThePendingLandingAcrossTicks() {
    adaptation.on(movement());
    pending.removeFirst().run();
    verify(adaptation, never()).applySpringload(any(), any());

    PlayerMoveEvent landing = movement();
    adaptation.on(landing);
    when(player.isOnGround()).thenReturn(true);
    pending.removeFirst().run();

    verify(adaptation).applySpringload(player, landing);
  }

  @ParameterizedTest
  @ValueSource(strings = {"offline", "invalid", "dead", "unlearned", "unregistered"})
  void invalidatedPlayerOrAdaptationCannotReceiveDelayedSpringload(String reason) {
    adaptation.on(movement());
    switch (reason) {
      case "offline" -> when(player.isOnline()).thenReturn(false);
      case "invalid" -> when(player.isValid()).thenReturn(false);
      case "dead" -> when(player.isDead()).thenReturn(true);
      case "unlearned" -> doReturn(false).when(adaptation).hasActiveAdaptation(player);
      case "unregistered" -> doReturn(false).when(adaptation).isRuntimeRegistered();
      default -> throw new IllegalArgumentException(reason);
    }
    when(player.isOnGround()).thenReturn(true);
    pending.removeFirst().run();

    verify(adaptation, never()).applySpringload(any(), any());
  }

  @Test
  void cancelledMovementCannotQueueAnObservation() {
    PlayerMoveEvent event = movement();
    event.setCancelled(true);
    adaptation.on(event);

    assertThat(pending).isEmpty();
    verify(adaptation, never()).applySpringload(any(), any());
  }

  @Test
  void failedSchedulingDoesNotBlockTheNextNaturalLanding() {
    scheduling.when(() -> J.runEntity(same(player), any(Runnable.class), eq(1))).thenReturn(false);
    adaptation.on(movement());
    assertThat(pending).isEmpty();

    acceptScheduling();
    PlayerMoveEvent next = movement();
    adaptation.on(next);
    assertThat(pending).hasSize(1);
    when(player.isOnGround()).thenReturn(true);
    pending.removeFirst().run();

    verify(adaptation).applySpringload(player, next);
  }

  @Test
  void rejectedAirborneRescheduleReleasesTheObservationForANewMovement() {
    adaptation.on(movement());
    scheduling.when(() -> J.runEntity(same(player), any(Runnable.class), eq(1))).thenReturn(false);
    pending.removeFirst().run();
    assertThat(pending).isEmpty();

    acceptScheduling();
    PlayerMoveEvent next = movement();
    adaptation.on(next);
    when(player.isOnGround()).thenReturn(true);
    pending.removeFirst().run();

    verify(adaptation).applySpringload(player, next);
  }

  @Test
  void teleportInvalidatesOldObservationWithoutConsumingANewOne() {
    adaptation.on(movement());
    PlayerMoveEvent first = movement();
    adaptation.on(new PlayerTeleportEvent(player, first.getFrom(), first.getTo()));
    PlayerMoveEvent afterTeleport = movement();
    adaptation.on(afterTeleport);
    assertThat(pending).hasSize(2);
    when(player.isOnGround()).thenReturn(true);

    pending.removeFirst().run();
    verify(adaptation, never()).applySpringload(any(), any());
    pending.removeFirst().run();

    verify(adaptation).applySpringload(player, afterTeleport);
  }

  @Test
  void unregisterClearsAlreadyQueuedLandings() {
    adaptation.on(movement());
    adaptation.unregister();
    when(player.isOnGround()).thenReturn(true);
    pending.removeFirst().run();

    verify(adaptation, never()).applySpringload(any(), any());
  }

  @Test
  void retiredPlayerStateStopsAnAirborneObservationWithoutRetainingTheEvent() {
    adaptation.on(movement());
    pending.removeFirst().run();
    assertThat(pending).hasSize(1);
    PlayerStateRegistry.clearPlayer(player.getUniqueId());
    when(player.isOnGround()).thenReturn(true);
    pending.removeFirst().run();

    verify(adaptation, never()).applySpringload(any(), any());
    assertThat(pending).isEmpty();
  }

  private void acceptScheduling() {
    scheduling.when(() -> J.runEntity(same(player), any(Runnable.class), eq(1))).thenAnswer(call -> {
      pending.add(call.getArgument(1));
      return true;
    });
  }

  private PlayerMoveEvent movement() {
    return new PlayerMoveEvent(player, new Location(world, 0.5D, 65D, 0.5D), new Location(world, 0.5D, 64D, 0.5D));
  }
}
