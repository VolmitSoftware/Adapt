package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.EngineTestBase;
import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.api.projectile.ProjectileReplacements;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.entity.ProjectileSnapshot;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RicochetRuntimeTest extends EngineTestBase {
  @Test
  void regionHandoffIsOnlyRequiredForForeignFoliaDestinations() {
    assertThat(RicochetTransferRules.requiresRegionHandoff(true, false)).isTrue();
    assertThat(RicochetTransferRules.requiresRegionHandoff(true, true)).isFalse();
    assertThat(RicochetTransferRules.requiresRegionHandoff(false, false)).isFalse();
  }

  @Test
  void detachedTemplateProtectsMutableFlightAndPersistentState() {
    WorldRef world = mock(WorldRef.class);
    PlayerRef shooter = mock(PlayerRef.class);
    ProjectileSnapshot snapshot = mock(ProjectileSnapshot.class);
    Loc spawn = new Loc(world, 8D, 72D, -4D);
    Loc hit = new Loc(world, 7D, 72D, -4D);
    Vec3 velocity = new Vec3(1.5D, 0.25D, -0.5D);
    Vec3 reflected = new Vec3(-1D, 0D, 0D);
    RicochetCarriedState carried = new RicochetCarriedState(2, true, 1);
    RicochetTransition transition = new RicochetTransition(reflected, 2D, 1, 0.5D);
    RicochetProjectileTemplate template = new RicochetProjectileTemplate(
        snapshot,
        spawn,
        velocity,
        shooter,
        new RicochetProfile(3, 0.1D, 0.5D, 0.01D, 0.4D),
        1,
        3,
        0.5D,
        hit,
        transition,
        carried
    );

    spawn.add(100D, 0D, 0D);
    velocity.multiply(0D);
    reflected.withX(1D);
    RicochetProjectileTemplate moved = template.withSpawnLocation(new Loc(world, 1D, 2D, 3D));

    assertThat(template.spawnLocation().x()).isEqualTo(8D);
    assertThat(template.velocity()).isEqualTo(new Vec3(1.5D, 0.25D, -0.5D));
    assertThat(template.transition().direction()).isEqualTo(new Vec3(-1D, 0D, 0D));
    assertThat(template.snapshot()).isSameAs(snapshot);
    assertThat(moved.spawnLocation().x()).isEqualTo(1D);
    assertThat(moved.carried()).isEqualTo(carried);
  }

  @Test
  void publicImpactSnapshotReadsOnlyAdaptOwnedMetadata() {
    ProjectileRef projectile = mock(ProjectileRef.class);
    ProjectileRef other = mock(ProjectileRef.class);
    when(projectile.uuid()).thenReturn(UUID.randomUUID());
    when(other.uuid()).thenReturn(UUID.randomUUID());
    RangedRicochetBolt adaptation = TestContent.adaptation(Id.adapt("ranged"), RangedRicochetBolt::new);

    adaptation.applyRicochetState(projectile, 3, 5, 4.25D);
    RangedRicochetBolt.RicochetImpact impact = RangedRicochetBolt.impactOf(projectile);

    assertThat(impact.count()).isEqualTo(3);
    assertThat(impact.bonusDamage()).isEqualTo(4.25D);
    assertThat(RangedRicochetBolt.impactOf(other)).isEqualTo(new RangedRicochetBolt.RicochetImpact(0, 0D));
  }

  @Test
  void publicImpactSnapshotNormalizesMissingAndInvalidState() {
    RangedRicochetBolt.RicochetImpact missing = RangedRicochetBolt.impactOf(null);
    RangedRicochetBolt.RicochetImpact invalid =
        new RangedRicochetBolt.RicochetImpact(Integer.MAX_VALUE, Double.NaN);

    assertThat(missing).isEqualTo(new RangedRicochetBolt.RicochetImpact(0, 0D));
    assertThat(invalid.count()).isEqualTo(12);
    assertThat(invalid.bonusDamage()).isZero();
  }

  @Test
  void manualRicochetKillRewardQueuesOnlyPositiveRicochetCounts() {
    RangedRicochetBolt adaptation = TestContent.adaptation(Id.adapt("ranged"), RangedRicochetBolt::new);
    PlayerRef player = mock(PlayerRef.class);
    Loc deathLocation = new Loc(mock(WorldRef.class), 4D, 8D, 12D);

    try (MockedStatic<Tasks> scheduling = SchedulerForwarding.mockTasks()) {
      scheduling.when(() -> Tasks.runEntity(same(player), any(Runnable.class))).thenReturn(true);

      adaptation.rewardManualRicochetKill(player, deathLocation, 0);
      adaptation.rewardManualRicochetKill(player, deathLocation, -1);
      adaptation.rewardManualRicochetKill(player, deathLocation, 2);

      scheduling.verify(
          () -> Tasks.runEntity(same(player), any(Runnable.class)),
          times(1)
      );
    }
  }

  @Test
  void replacementTicketCompletesOnlyOnce() {
    ProjectileReplacements.Ticket delegate =
        mock(ProjectileReplacements.Ticket.class);
    ProjectileRef first = mock(ProjectileRef.class);
    ProjectileRef second = mock(ProjectileRef.class);
    when(delegate.complete(first)).thenReturn(true);
    RicochetReplacementTicket ticket = RicochetReplacementTicket.of(delegate);

    assertThat(ticket.complete(first)).isTrue();
    assertThat(ticket.complete(second)).isFalse();
    ticket.cancel();

    verify(delegate, times(1)).complete(first);
    verify(delegate, never()).complete(second);
    verify(delegate, never()).cancel();
  }

  @Test
  void unclaimedReplacementStillResolvesOnlyOnce() {
    ProjectileRef first = mock(ProjectileRef.class);
    ProjectileRef second = mock(ProjectileRef.class);
    RicochetReplacementTicket ticket = RicochetReplacementTicket.of(null);

    assertThat(ticket.complete(first)).isTrue();
    assertThat(ticket.complete(second)).isFalse();
  }

  @Test
  void cancelledUnclaimedReplacementCannotCompleteLater() {
    RicochetReplacementTicket ticket = RicochetReplacementTicket.of(null);

    ticket.cancel();

    assertThat(ticket.complete(mock(ProjectileRef.class))).isFalse();
  }

  @Test
  void rejectedReplacementCancelsTicketExactlyOnce() {
    ProjectileReplacements.Ticket delegate =
        mock(ProjectileReplacements.Ticket.class);
    ProjectileRef replacement = mock(ProjectileRef.class);
    when(delegate.complete(replacement)).thenReturn(false);
    RicochetReplacementTicket ticket = RicochetReplacementTicket.of(delegate);

    assertThat(ticket.complete(replacement)).isFalse();
    ticket.cancel();

    verify(delegate, times(1)).complete(replacement);
    verify(delegate, times(1)).cancel();
  }

  @Test
  void cancelledReplacementCannotCompleteLater() {
    ProjectileReplacements.Ticket delegate =
        mock(ProjectileReplacements.Ticket.class);
    ProjectileRef replacement = mock(ProjectileRef.class);
    RicochetReplacementTicket ticket = RicochetReplacementTicket.of(delegate);

    ticket.cancel();
    ticket.cancel();

    assertThat(ticket.complete(replacement)).isFalse();
    verify(delegate, times(1)).cancel();
    verify(delegate, never()).complete(replacement);
  }

  @Test
  void failedReplacementCompletionCancelsBeforePropagating() {
    ProjectileReplacements.Ticket delegate =
        mock(ProjectileReplacements.Ticket.class);
    ProjectileRef replacement = mock(ProjectileRef.class);
    IllegalStateException failure = new IllegalStateException("transfer failed");
    when(delegate.complete(replacement)).thenThrow(failure);
    RicochetReplacementTicket ticket = RicochetReplacementTicket.of(delegate);

    assertThatThrownBy(() -> ticket.complete(replacement)).isSameAs(failure);
    verify(delegate, times(1)).cancel();
  }

}
