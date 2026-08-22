package art.arcane.adapt.content.adaptation.sword;

import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class SwordDamageHandlerCancellationPolicyTest {
  @Test
  void poisonedBladeIgnoresCancelledDamage() throws ReflectiveOperationException {
    assertIgnoresCancelledDamage(SwordsPoisonedBlade.class);
  }

  @Test
  void bloodyBladeIgnoresCancelledDamage() throws ReflectiveOperationException {
    assertIgnoresCancelledDamage(SwordsBloodyBlade.class);
  }

  @Test
  void dualWieldIgnoresCancelledDamage() throws ReflectiveOperationException {
    assertIgnoresCancelledDamage(SwordsDualWield.class);
  }

  @Test
  void executionersEdgeIgnoresCancelledDamage() throws ReflectiveOperationException {
    assertIgnoresCancelledDamage(SwordsExecutionersEdge.class);
  }

  @Test
  void riposteWindowIgnoresCancelledDamage() throws ReflectiveOperationException {
    assertIgnoresCancelledDamage(SwordsRiposteWindow.class);
  }

  @Test
  void crimsonCycloneIgnoresCancelledDamage() throws ReflectiveOperationException {
    assertIgnoresCancelledDamage(SwordsCrimsonCyclone.class);
  }

  private static void assertIgnoresCancelledDamage(Class<?> adaptationType) throws ReflectiveOperationException {
    Method handler = adaptationType.getDeclaredMethod("on", EntityDamageByEntityEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.ignoreCancelled()).isTrue();
  }
}
