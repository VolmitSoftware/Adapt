package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.projectile.ProjectileReplacementRegistry;
import art.arcane.adapt.util.common.scheduling.J;
import net.kyori.adventure.util.TriState;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.LingeringPotion;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.SplashPotion;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Trident;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RicochetRuntimeTest extends AdaptTestBase {
  @Test
  void regionHandoffIsOnlyRequiredForForeignFoliaDestinations() {
    assertThat(RicochetTransferRules.requiresRegionHandoff(true, false)).isTrue();
    assertThat(RicochetTransferRules.requiresRegionHandoff(true, true)).isFalse();
    assertThat(RicochetTransferRules.requiresRegionHandoff(false, false)).isFalse();
  }

  @Test
  void resolvesEverySupportedProjectileWithoutCollapsingPotionSubtypes() {
    assertKind(SpectralArrow.class, RicochetProjectileKind.SPECTRAL_ARROW);
    assertKind(Trident.class, RicochetProjectileKind.TRIDENT);
    assertKind(Arrow.class, RicochetProjectileKind.ARROW);
    assertKind(Snowball.class, RicochetProjectileKind.SNOWBALL);
    assertKind(Egg.class, RicochetProjectileKind.EGG);
    assertKind(EnderPearl.class, RicochetProjectileKind.ENDER_PEARL);
    assertKind(LingeringPotion.class, RicochetProjectileKind.LINGERING_POTION);
    assertKind(SplashPotion.class, RicochetProjectileKind.SPLASH_POTION);
    assertKind(ThrownPotion.class, RicochetProjectileKind.THROWN_POTION);
    assertKind(ThrownExpBottle.class, RicochetProjectileKind.EXPERIENCE_BOTTLE);
  }

  @Test
  void emptyPersistentDataAvoidsSerializationAndRestoreWork() throws Exception {
    PersistentDataContainer source = mock(PersistentDataContainer.class);
    PersistentDataContainer target = mock(PersistentDataContainer.class);
    when(source.isEmpty()).thenReturn(true);

    byte[] snapshot = RicochetPersistentData.snapshot(source);
    RicochetPersistentData.restore(target, snapshot);

    assertThat(snapshot).isNull();
    verify(source, never()).serializeToBytes();
    verify(target, never()).readFromBytes(any(), anyBoolean());
  }

  @Test
  void persistentDataUsesPaperByteSerializationAcrossTheHandoff() throws Exception {
    PersistentDataContainer source = mock(PersistentDataContainer.class);
    PersistentDataContainer target = mock(PersistentDataContainer.class);
    byte[] encoded = new byte[]{4, 8, 15, 16, 23, 42};
    when(source.isEmpty()).thenReturn(false);
    when(source.serializeToBytes()).thenReturn(encoded);

    byte[] snapshot = RicochetPersistentData.snapshot(source);
    RicochetPersistentData.restore(target, snapshot);

    assertThat(snapshot).containsExactly(encoded);
    verify(target).readFromBytes(snapshot, true);
  }

  @Test
  void detachedTemplateProtectsMutableFlightAndPersistentState() {
    World world = mock(World.class);
    Player shooter = mock(Player.class);
    ItemStack item = mock(ItemStack.class);
    when(item.clone()).thenReturn(item);
    Location spawn = new Location(world, 8D, 72D, -4D);
    Location hit = new Location(world, 7D, 72D, -4D);
    Vector velocity = new Vector(1.5D, 0.25D, -0.5D);
    Vector reflected = new Vector(-1D, 0D, 0D);
    byte[] persistentData = new byte[]{1, 2, 3};
    RicochetCommonState common = new RicochetCommonState(
        true,
        10,
        true,
        true,
        false,
        20,
        0F,
        TriState.NOT_SET,
        0,
        false,
        false,
        false,
        false,
        false,
        0,
        null,
        false,
        true,
        Set.of("ricochet"),
        0,
        persistentData
    );
    RicochetTransition transition = new RicochetTransition(reflected, 2D, 1, 0.5D);
    RicochetProjectileTemplate template = new RicochetProjectileTemplate(
        RicochetProjectileKind.SNOWBALL,
        world,
        spawn,
        velocity,
        shooter,
        new RicochetProfile(3, 0.1D, 0.5D, 0.01D, 0.4D),
        1,
        3,
        0.5D,
        hit,
        transition,
        common,
        new RicochetThrowablePayload(item)
    );

    spawn.add(100D, 0D, 0D);
    velocity.zero();
    reflected.setX(1D);
    persistentData[0] = 99;
    byte[] firstRead = common.persistentData();
    firstRead[1] = 99;

    assertThat(template.spawnLocation().getX()).isEqualTo(8D);
    assertThat(template.velocity()).isEqualTo(new Vector(1.5D, 0.25D, -0.5D));
    assertThat(template.transition().direction()).isEqualTo(new Vector(-1D, 0D, 0D));
    assertThat(common.persistentData()).containsExactly(1, 2, 3);
  }

  @Test
  void publicImpactSnapshotReadsOnlyAdaptOwnedMetadata() {
    Projectile projectile = mock(Projectile.class);
    MetadataValue foreignCount = mock(MetadataValue.class);
    MetadataValue ownedCount = mock(MetadataValue.class);
    MetadataValue ownedDamage = mock(MetadataValue.class);
    Plugin foreignPlugin = mock(Plugin.class);
    when(foreignCount.getOwningPlugin()).thenReturn(foreignPlugin);
    when(foreignCount.asInt()).thenReturn(11);
    when(ownedCount.getOwningPlugin()).thenReturn(plugin);
    when(ownedCount.asInt()).thenReturn(3);
    when(ownedDamage.getOwningPlugin()).thenReturn(plugin);
    when(ownedDamage.asDouble()).thenReturn(4.25D);
    when(projectile.getMetadata("adapt-ricochet-count"))
        .thenReturn(List.of(foreignCount, ownedCount));
    when(projectile.getMetadata("adapt-ricochet-bonus-damage"))
        .thenReturn(List.of(ownedDamage));

    RangedRicochetBolt.RicochetImpact impact = RangedRicochetBolt.impactOf(projectile);

    assertThat(impact.count()).isEqualTo(3);
    assertThat(impact.bonusDamage()).isEqualTo(4.25D);
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
    RangedRicochetBolt adaptation = new RangedRicochetBolt();
    Player player = mock(Player.class);
    Location deathLocation = new Location(mock(World.class), 4D, 8D, 12D);

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(same(player), any(Runnable.class))).thenReturn(true);

      adaptation.rewardManualRicochetKill(player, deathLocation, 0);
      adaptation.rewardManualRicochetKill(player, deathLocation, -1);
      adaptation.rewardManualRicochetKill(player, deathLocation, 2);

      scheduling.verify(
          () -> J.runEntity(same(player), any(Runnable.class)),
          times(1)
      );
    }
  }

  @Test
  void replacementTicketCompletesOnlyOnce() {
    ProjectileReplacementRegistry.Ticket delegate =
        mock(ProjectileReplacementRegistry.Ticket.class);
    Projectile first = mock(Projectile.class);
    Projectile second = mock(Projectile.class);
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
    Projectile first = mock(Projectile.class);
    Projectile second = mock(Projectile.class);
    RicochetReplacementTicket ticket = RicochetReplacementTicket.of(null);

    assertThat(ticket.complete(first)).isTrue();
    assertThat(ticket.complete(second)).isFalse();
  }

  @Test
  void cancelledUnclaimedReplacementCannotCompleteLater() {
    RicochetReplacementTicket ticket = RicochetReplacementTicket.of(null);

    ticket.cancel();

    assertThat(ticket.complete(mock(Projectile.class))).isFalse();
  }

  @Test
  void rejectedReplacementCancelsTicketExactlyOnce() {
    ProjectileReplacementRegistry.Ticket delegate =
        mock(ProjectileReplacementRegistry.Ticket.class);
    Projectile replacement = mock(Projectile.class);
    when(delegate.complete(replacement)).thenReturn(false);
    RicochetReplacementTicket ticket = RicochetReplacementTicket.of(delegate);

    assertThat(ticket.complete(replacement)).isFalse();
    ticket.cancel();

    verify(delegate, times(1)).complete(replacement);
    verify(delegate, times(1)).cancel();
  }

  @Test
  void cancelledReplacementCannotCompleteLater() {
    ProjectileReplacementRegistry.Ticket delegate =
        mock(ProjectileReplacementRegistry.Ticket.class);
    Projectile replacement = mock(Projectile.class);
    RicochetReplacementTicket ticket = RicochetReplacementTicket.of(delegate);

    ticket.cancel();
    ticket.cancel();

    assertThat(ticket.complete(replacement)).isFalse();
    verify(delegate, times(1)).cancel();
    verify(delegate, never()).complete(replacement);
  }

  @Test
  void failedReplacementCompletionCancelsBeforePropagating() {
    ProjectileReplacementRegistry.Ticket delegate =
        mock(ProjectileReplacementRegistry.Ticket.class);
    Projectile replacement = mock(Projectile.class);
    IllegalStateException failure = new IllegalStateException("transfer failed");
    when(delegate.complete(replacement)).thenThrow(failure);
    RicochetReplacementTicket ticket = RicochetReplacementTicket.of(delegate);

    assertThatThrownBy(() -> ticket.complete(replacement)).isSameAs(failure);
    verify(delegate, times(1)).cancel();
  }

  private <T extends Projectile> void assertKind(
      Class<T> projectileClass,
      RicochetProjectileKind expected
  ) {
    Projectile projectile = mock(projectileClass);
    assertThat(RicochetProjectileKind.resolve(projectile)).isEqualTo(expected);
    assertThat(expected.projectileClass()).isEqualTo(projectileClass);
  }
}
