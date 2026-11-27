package art.arcane.adapt.content.adaptation.unarmed;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnarmedSecondWindDamageSourceTest {
  @Test
  void directPlayerDamageQualifies() {
    EntityDeathEvent event = mock(EntityDeathEvent.class);
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(UUID.randomUUID());
    when(event.causingEntity()).thenReturn(player);
    when(event.directDamager()).thenReturn(player);

    assertThat(UnarmedSecondWind.directPlayerKiller(event)).isSameAs(player);
  }

  @Test
  void projectilesAndEnvironmentalDamageDoNotQualify() {
    EntityDeathEvent projectileDeath = mock(EntityDeathEvent.class);
    PlayerRef shooter = mock(PlayerRef.class);
    ProjectileRef projectile = mock(ProjectileRef.class);
    when(shooter.uuid()).thenReturn(UUID.randomUUID());
    when(projectile.uuid()).thenReturn(UUID.randomUUID());
    when(projectileDeath.causingEntity()).thenReturn(shooter);
    when(projectileDeath.directDamager()).thenReturn(projectile);

    EntityDeathEvent environmentalDeath = mock(EntityDeathEvent.class);
    EntityRef environmentalSourceEntity = mock(EntityRef.class);
    when(environmentalSourceEntity.uuid()).thenReturn(UUID.randomUUID());
    when(environmentalDeath.causingEntity()).thenReturn(environmentalSourceEntity);
    when(environmentalDeath.directDamager()).thenReturn(environmentalSourceEntity);

    assertThat(UnarmedSecondWind.directPlayerKiller(projectileDeath)).isNull();
    assertThat(UnarmedSecondWind.directPlayerKiller(environmentalDeath)).isNull();
  }
}
