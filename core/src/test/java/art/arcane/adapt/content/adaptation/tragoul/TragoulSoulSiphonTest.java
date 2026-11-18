package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TragoulSoulSiphonTest {
  @Test
  void damageSourceUsesTheResponsiblePlayerForIndirectDamage() {
    PlayerRef owner = mock(PlayerRef.class);
    ProjectileRef projectile = mock(ProjectileRef.class);

    assertThat(TragoulSoulSiphon.resolveDamageOwner(owner, projectile)).isSameAs(owner);
  }

  @Test
  void entityFallbackCoversProjectilesExplosivesCloudsAndFangs() {
    PlayerRef owner = mock(PlayerRef.class);
    ProjectileRef projectile = mock(ProjectileRef.class);
    EntityRef tnt = mock(EntityRef.class);
    EntityRef cloud = mock(EntityRef.class);
    EntityRef fangs = mock(EntityRef.class);
    when(projectile.shooter()).thenReturn(owner);
    when(tnt.responsibleEntity()).thenReturn(owner);
    when(cloud.responsibleEntity()).thenReturn(owner);
    when(fangs.responsibleEntity()).thenReturn(owner);

    assertThat(TragoulSoulSiphon.resolveDamageOwner(projectile)).isSameAs(owner);
    assertThat(TragoulSoulSiphon.resolveDamageOwner(tnt)).isSameAs(owner);
    assertThat(TragoulSoulSiphon.resolveDamageOwner(cloud)).isSameAs(owner);
    assertThat(TragoulSoulSiphon.resolveDamageOwner(fangs)).isSameAs(owner);
    assertThat(TragoulSoulSiphon.resolveDamageOwner(mock(ItemEntityRef.class))).isNull();
  }

  @Test
  void healingRespectsDamageFractionPerSecondCapAndMissingHealth() {
    assertThat(TragoulSoulSiphon.healAmount(10D, 0.25D, 8D, 20D)).isEqualTo(2.5D);
    assertThat(TragoulSoulSiphon.healAmount(100D, 0.25D, 4D, 20D)).isEqualTo(4D);
    assertThat(TragoulSoulSiphon.healAmount(100D, 0.25D, 20D, 1D)).isEqualTo(1D);
  }

  @Test
  void healingRejectsInvalidOrNonPositiveInputs() {
    assertThat(TragoulSoulSiphon.healAmount(-10D, 0.25D, 8D, 20D)).isZero();
    assertThat(TragoulSoulSiphon.healAmount(10D, -0.25D, 8D, 20D)).isZero();
    assertThat(TragoulSoulSiphon.healAmount(Double.NaN, 0.25D, 8D, 20D)).isZero();
    assertThat(TragoulSoulSiphon.healAmount(10D, 0.25D, 0D, 20D)).isZero();
  }

  @Test
  void acceptedDamageCannotInflateSiphonThroughOverkill() {
    assertThat(TragoulSoulSiphon.acceptedDamage(100D, 4D, 2D)).isEqualTo(6D);
    assertThat(TragoulSoulSiphon.acceptedDamage(5D, 20D, 0D)).isEqualTo(5D);
    assertThat(TragoulSoulSiphon.acceptedDamage(-5D, 20D, 0D)).isZero();
    assertThat(TragoulSoulSiphon.acceptedDamage(Double.NaN, 20D, 0D)).isZero();
  }

  @Test
  void siphonListensToEveryAttributedDamageEventAtMonitor() throws NoSuchMethodException {
    Method handler = TragoulSoulSiphon.class.getDeclaredMethod("on", EntityDamageEvent.class);
    Handle eventHandler = handler.getAnnotation(Handle.class);

    assertThat(eventHandler.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(eventHandler.ignoreCancelled()).isTrue();
  }
}
