package art.arcane.adapt.api.projectile;

import art.arcane.adapt.platform.entity.ProjectileRef;
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
    ProjectileRef source = mock(ProjectileRef.class);
    ProjectileReplacementRegistry.Ticket ticket =
        mock(ProjectileReplacementRegistry.Ticket.class);
    AtomicInteger begins = new AtomicInteger();
    when(source.uuid()).thenReturn(projectileId);
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
    ProjectileRef source = mock(ProjectileRef.class);
    ProjectileReplacementRegistry.Ticket ticket =
        mock(ProjectileReplacementRegistry.Ticket.class);
    when(source.uuid()).thenReturn(projectileId);
    ProjectileReplacementRegistry.register(source, projectile -> ticket);

    ProjectileReplacementRegistry.unregister(projectileId);

    assertThat(ProjectileReplacementRegistry.begin(source)).isNull();
  }

  @Test
  void missingClaimReturnsNoTicket() {
    ProjectileRef source = mock(ProjectileRef.class);
    when(source.uuid()).thenReturn(UUID.randomUUID());

    assertThat(ProjectileReplacementRegistry.begin(source)).isNull();
  }
}
