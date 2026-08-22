package art.arcane.adapt.content.adaptation.hunter;

import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class HunterDamageHandlerCancellationPolicyTest {
  @Test
  void predatorFocusIgnoresCancelledDamage() throws ReflectiveOperationException {
    assertIgnoresCancelledDamage(HunterPredatorFocus.class);
  }

  @Test
  void bigGameHunterIgnoresCancelledDamage() throws ReflectiveOperationException {
    assertIgnoresCancelledDamage(HunterBigGameHunter.class);
  }

  @Test
  void bloodTrailIgnoresCancelledDamage() throws ReflectiveOperationException {
    assertIgnoresCancelledDamage(HunterBloodTrail.class);
  }

  private static void assertIgnoresCancelledDamage(Class<?> adaptationType) throws ReflectiveOperationException {
    Method handler = adaptationType.getDeclaredMethod("on", EntityDamageByEntityEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.ignoreCancelled()).isTrue();
  }
}
