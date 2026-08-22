package art.arcane.adapt.content.adaptation.tragoul;

import org.bukkit.damage.DamageSource;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TragoulSoulSiphonTest {
  @Test
  void damageSourceUsesTheResponsiblePlayerForIndirectDamage() {
    DamageSource source = mock(DamageSource.class);
    Player owner = mock(Player.class);
    Projectile projectile = mock(Projectile.class);
    when(source.getCausingEntity()).thenReturn(owner);
    when(source.getDirectEntity()).thenReturn(projectile);

    assertThat(TragoulSoulSiphon.resolveDamageOwner(source)).isSameAs(owner);
  }

  @Test
  void entityFallbackCoversProjectilesExplosivesCloudsAndFangs() {
    Player owner = mock(Player.class);
    Projectile projectile = mock(Projectile.class);
    TNTPrimed tnt = mock(TNTPrimed.class);
    AreaEffectCloud cloud = mock(AreaEffectCloud.class);
    EvokerFangs fangs = mock(EvokerFangs.class);
    when(projectile.getShooter()).thenReturn(owner);
    when(tnt.getSource()).thenReturn(owner);
    when(cloud.getSource()).thenReturn(owner);
    when(fangs.getOwner()).thenReturn(owner);

    assertThat(TragoulSoulSiphon.resolveDamageOwner(projectile)).isSameAs(owner);
    assertThat(TragoulSoulSiphon.resolveDamageOwner(tnt)).isSameAs(owner);
    assertThat(TragoulSoulSiphon.resolveDamageOwner(cloud)).isSameAs(owner);
    assertThat(TragoulSoulSiphon.resolveDamageOwner(fangs)).isSameAs(owner);
    assertThat(TragoulSoulSiphon.resolveDamageOwner(mock(Item.class))).isNull();
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
    EventHandler eventHandler = handler.getAnnotation(EventHandler.class);

    assertThat(eventHandler.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(eventHandler.ignoreCancelled()).isTrue();
  }
}
