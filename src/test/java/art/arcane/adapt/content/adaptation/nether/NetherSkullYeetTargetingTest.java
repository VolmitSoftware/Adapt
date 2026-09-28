package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.AdaptTestBase;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Cow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NetherSkullYeetTargetingTest extends AdaptTestBase {
  private NetherSkullYeet adaptation;
  private WitherSkull skull;
  private EntityDamageByEntityEvent event;

  @BeforeEach
  void prepare() {
    adaptation = new NetherSkullYeet();
    adaptation.getConfig().setIgnorePassiveMobs(true);
    skull = mock(WitherSkull.class);
    Player owner = mock(Player.class);
    when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
    when(skull.getShooter()).thenReturn(owner);
    when(skull.hasMetadata("adapt-nether-skull-toss")).thenReturn(true);
    event = mock(EntityDamageByEntityEvent.class);
    when(event.getDamager()).thenReturn(skull);
    when(event.getCause()).thenReturn(DamageCause.ENTITY_EXPLOSION);
  }

  @Test
  void explosionProtectsCowWhenEnabled() {
    target(Cow.class);
    adaptation.protectPassiveMobs(event);
    verify(event).setCancelled(true);
  }

  @Test
  void explosionStillDamagesHostileMob() {
    Husk husk = target(Husk.class);
    when(husk.getType()).thenReturn(EntityType.HUSK);
    adaptation.protectPassiveMobs(event);
    verify(event, never()).setCancelled(true);
  }

  @Test
  void disabledProtectionPreservesExplosionDamage() {
    adaptation.getConfig().setIgnorePassiveMobs(false);
    target(Cow.class);
    adaptation.protectPassiveMobs(event);
    verify(event, never()).setCancelled(true);
  }

  @Test
  void deliberateDirectImpactIsUnchanged() {
    target(Cow.class);
    when(event.getCause()).thenReturn(DamageCause.PROJECTILE);
    adaptation.protectPassiveMobs(event);
    verify(event, never()).setCancelled(true);
  }

  @Test
  void unrelatedSkullExplosionIsUnchanged() {
    when(skull.hasMetadata("adapt-nether-skull-toss")).thenReturn(false);
    target(Cow.class);
    adaptation.protectPassiveMobs(event);
    verify(event, never()).setCancelled(true);
  }

  @Test
  void summonedMobIsProtectedFromDirectImpactWithToggleDisabled() {
    adaptation.getConfig().setIgnorePassiveMobs(false);
    LivingEntity servant = target(LivingEntity.class);
    when(servant.getPersistentDataContainer().has(
        NamespacedKey.fromString("adapt:tragoul_servant_owner"), PersistentDataType.STRING)).thenReturn(true);
    when(event.getCause()).thenReturn(DamageCause.PROJECTILE);
    adaptation.protectPassiveMobs(event);
    verify(event).setCancelled(true);
  }

  private <T extends LivingEntity> T target(Class<T> type) {
    T target = mock(type);
    when(target.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
    when(event.getEntity()).thenReturn(target);
    return target;
  }
}
