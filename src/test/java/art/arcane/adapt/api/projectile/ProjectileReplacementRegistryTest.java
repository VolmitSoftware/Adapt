package art.arcane.adapt.api.projectile;

import org.bukkit.entity.Projectile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectileReplacementRegistryTest {
  @BeforeEach
  void clearBeforeTest() {
    ProjectileReplacementRegistry.clear();
  }

  @AfterEach
  void clearAfterTest() {
    ProjectileReplacementRegistry.clear();
  }

  @Test
  void registeredClaimBeginsOnceForItsProjectile() {
    UUID projectileId = UUID.randomUUID();
    Projectile source = mock(Projectile.class);
    ProjectileReplacementRegistry.Ticket ticket =
        mock(ProjectileReplacementRegistry.Ticket.class);
    AtomicInteger begins = new AtomicInteger();
    when(source.getUniqueId()).thenReturn(projectileId);
    ProjectileReplacementRegistry.register(source, projectile -> {
      begins.incrementAndGet();
      return ticket;
    });

    ProjectileReplacementRegistry.Ticket first =
        ProjectileReplacementRegistry.begin(source);
    ProjectileReplacementRegistry.Ticket second =
        ProjectileReplacementRegistry.begin(source);

    assertThat(first).isSameAs(ticket);
    assertThat(second).isNull();
    assertThat(begins.get()).isEqualTo(1);
  }

  @Test
  void unregisterPreventsAClaimFromBeginning() {
    UUID projectileId = UUID.randomUUID();
    Projectile source = mock(Projectile.class);
    ProjectileReplacementRegistry.Ticket ticket =
        mock(ProjectileReplacementRegistry.Ticket.class);
    when(source.getUniqueId()).thenReturn(projectileId);
    ProjectileReplacementRegistry.register(source, projectile -> ticket);

    ProjectileReplacementRegistry.unregister(projectileId);

    assertThat(ProjectileReplacementRegistry.begin(source)).isNull();
  }

  @Test
  void missingClaimReturnsNoTicket() {
    Projectile source = mock(Projectile.class);
    when(source.getUniqueId()).thenReturn(UUID.randomUUID());

    assertThat(ProjectileReplacementRegistry.begin(source)).isNull();
  }
}
