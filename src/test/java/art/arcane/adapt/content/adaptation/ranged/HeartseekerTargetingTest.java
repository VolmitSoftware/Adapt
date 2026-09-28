package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.AdaptTestBase;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Cow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HeartseekerTargetingTest extends AdaptTestBase {
  private RangedHeartseeker adaptation;
  private AbstractArrow arrow;
  private EntityDamageByEntityEvent event;
  private Cow target;

  @BeforeEach
  void prepare() {
    adaptation = new RangedHeartseeker();
    adaptation.getConfig().ignorePassiveMobs = true;
    arrow = mock(AbstractArrow.class);
    Player owner = mock(Player.class);
    when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
    when(arrow.getShooter()).thenReturn(owner);
    when(arrow.hasMetadata(RangedHeartseeker.SEEKING_ARROW_META)).thenReturn(true);
    target = mock(Cow.class);
    when(target.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
    event = mock(EntityDamageByEntityEvent.class);
    when(event.getDamager()).thenReturn(arrow);
    when(event.getEntity()).thenReturn(target);
  }

  @Test
  void seekingArrowCannotDamageProtectedCow() {
    adaptation.protectPassiveMobs(event);
    verify(event).setCancelled(true);
  }

  @Test
  void ordinaryArrowCanStillDamageCow() {
    when(arrow.hasMetadata(RangedHeartseeker.SEEKING_ARROW_META)).thenReturn(false);
    adaptation.protectPassiveMobs(event);
    verify(event, never()).setCancelled(true);
  }

  @Test
  void disabledProtectionPreservesSeekingArrowDamage() {
    adaptation.getConfig().ignorePassiveMobs = false;
    adaptation.protectPassiveMobs(event);
    verify(event, never()).setCancelled(true);
  }

  @Test
  void passiveTargetsAreRejectedBeforeLockAndReseekSnapshots() throws ReflectiveOperationException {
    Method capture = RangedHeartseeker.class.getDeclaredMethod("captureTargetOwned", UUID.class, LivingEntity.class);
    capture.setAccessible(true);
    assertThat(capture.invoke(adaptation, UUID.randomUUID(), target)).isNull();
    verify(target, never()).getLocation();
  }

  @Test
  void summonedMobIsProtectedWithToggleDisabled() {
    adaptation.getConfig().ignorePassiveMobs = false;
    when(target.getPersistentDataContainer().has(
        NamespacedKey.fromString("adapt:tragoul_servant_owner"), PersistentDataType.STRING)).thenReturn(true);
    adaptation.protectPassiveMobs(event);
    verify(event).setCancelled(true);
  }
}
