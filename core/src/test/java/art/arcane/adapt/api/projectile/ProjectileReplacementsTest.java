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

class ProjectileReplacementsTest {
  @BeforeEach
  void clearBeforeTest() {
    ProjectileReplacements.clear();
  }

  @AfterEach
  void clearAfterTest() {
    ProjectileReplacements.clear();
  }

  @Test
  void registeredClaimBeginsOnceForItsProjectile() {
    UUID projectileId = UUID.randomUUID();
    ProjectileRef source = mock(ProjectileRef.class);
    ProjectileReplacements.Ticket ticket =
        mock(ProjectileReplacements.Ticket.class);
    AtomicInteger begins = new AtomicInteger();
    when(source.uuid()).thenReturn(projectileId);
    ProjectileReplacements.register(source, projectile -> {
      begins.incrementAndGet();
      return ticket;
    });

    ProjectileReplacements.Ticket first =
        ProjectileReplacements.begin(source);
    ProjectileReplacements.Ticket second =
        ProjectileReplacements.begin(source);

    assertThat(first).isSameAs(ticket);
    assertThat(second).isNull();
    assertThat(begins.get()).isEqualTo(1);
  }

  @Test
  void unregisterPreventsAClaimFromBeginning() {
    UUID projectileId = UUID.randomUUID();
    ProjectileRef source = mock(ProjectileRef.class);
    ProjectileReplacements.Ticket ticket =
        mock(ProjectileReplacements.Ticket.class);
    when(source.uuid()).thenReturn(projectileId);
    ProjectileReplacements.register(source, projectile -> ticket);

    ProjectileReplacements.unregister(projectileId);

    assertThat(ProjectileReplacements.begin(source)).isNull();
  }

  @Test
  void missingClaimReturnsNoTicket() {
    ProjectileRef source = mock(ProjectileRef.class);
    when(source.uuid()).thenReturn(UUID.randomUUID());

    assertThat(ProjectileReplacements.begin(source)).isNull();
  }
}
