package art.arcane.adapt.content.adaptation.unarmed;

import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDeathEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnarmedSecondWindDamageSourceTest {
  @Test
  void directPlayerDamageQualifies() {
    EntityDeathEvent event = mock(EntityDeathEvent.class);
    DamageSource source = mock(DamageSource.class);
    Player player = mock(Player.class);
    when(event.getDamageSource()).thenReturn(source);
    when(source.getCausingEntity()).thenReturn(player);
    when(source.getDirectEntity()).thenReturn(player);

    assertThat(UnarmedSecondWind.directPlayerKiller(event)).isSameAs(player);
  }

  @Test
  void projectilesAndEnvironmentalDamageDoNotQualify() {
    EntityDeathEvent projectileDeath = mock(EntityDeathEvent.class);
    DamageSource projectileSource = mock(DamageSource.class);
    Player shooter = mock(Player.class);
    Projectile projectile = mock(Projectile.class);
    when(projectileDeath.getDamageSource()).thenReturn(projectileSource);
    when(projectileSource.getCausingEntity()).thenReturn(shooter);
    when(projectileSource.getDirectEntity()).thenReturn(projectile);

    EntityDeathEvent environmentalDeath = mock(EntityDeathEvent.class);
    DamageSource environmentalSource = mock(DamageSource.class);
    Entity environmentalSourceEntity = mock(Entity.class);
    when(environmentalDeath.getDamageSource()).thenReturn(environmentalSource);
    when(environmentalSource.getCausingEntity()).thenReturn(environmentalSourceEntity);
    when(environmentalSource.getDirectEntity()).thenReturn(environmentalSourceEntity);

    assertThat(UnarmedSecondWind.directPlayerKiller(projectileDeath)).isNull();
    assertThat(UnarmedSecondWind.directPlayerKiller(environmentalDeath)).isNull();
  }
}
