package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.api.content.ReceiveCancelledEvents;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BlockingDamageHandlerCancellationPolicyTest {
  private static final List<Class<?>> CANCELLATION_AWARE_DAMAGE_HANDLERS = List.of(
      BlockingBastionStance.class
  );

  @Test
  void everyDamageHandlerNeverReceivesAlreadyCancelledDamage() throws ReflectiveOperationException {
    for (Class<?> adaptationType : CANCELLATION_AWARE_DAMAGE_HANDLERS) {
      Method handler = adaptationType.getDeclaredMethod("on", EntityDamageByEntityEvent.class);
      Handle policy = handler.getAnnotation(Handle.class);
      assertThat(policy).as("%s damage handler policy", adaptationType.getSimpleName()).isNotNull();
      assertThat(policy.ignoreCancelled())
          .as("%s must ignore cancelled damage", adaptationType.getSimpleName())
          .isTrue();
      assertThat(handler.getAnnotation(ReceiveCancelledEvents.class))
          .as("%s must not opt into cancelled damage", adaptationType.getSimpleName())
          .isNull();
    }
  }
}
