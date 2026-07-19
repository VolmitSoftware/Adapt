package art.arcane.adapt.content.adaptation;

import art.arcane.adapt.content.adaptation.blocking.BlockingBastionStance;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedDisarm;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedGrapple;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class DamageHandlerCancellationPolicyTest {
  @Test
  void disarmNeverReceivesAlreadyCancelledDamage() throws ReflectiveOperationException {
    assertIgnoresCancelledDamage(UnarmedDisarm.class);
  }

  @Test
  void grappleNeverReceivesAlreadyCancelledDamage() throws ReflectiveOperationException {
    assertIgnoresCancelledDamage(UnarmedGrapple.class);
  }

  @Test
  void bastionNeverReceivesAlreadyCancelledDamage() throws ReflectiveOperationException {
    assertIgnoresCancelledDamage(BlockingBastionStance.class);
  }

  private static void assertIgnoresCancelledDamage(Class<?> adaptationType) throws ReflectiveOperationException {
    Method handler = adaptationType.getDeclaredMethod("on", EntityDamageByEntityEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.ignoreCancelled()).isTrue();
  }
}
