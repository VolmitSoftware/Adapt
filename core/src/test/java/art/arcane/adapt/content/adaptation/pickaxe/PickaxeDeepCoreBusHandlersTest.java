package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.platform.event.AnnotatedHandlers;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.HandlerMethod;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDamageEvent;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PickaxeDeepCoreBusHandlersTest {
  @Test
  void handlersMoveToTheBusWithTheirOldPrioritiesAndCancelFilter() {
    List<HandlerMethod> handlers = AnnotatedHandlers.of(PickaxeDeepCore.class);

    assertThat(handlers).hasSize(2);
    HandlerMethod damage = handlers.stream().filter(handler -> handler.eventType() == BlockDamageEvent.class).findFirst().orElseThrow();
    HandlerMethod breaks = handlers.stream().filter(handler -> handler.eventType() == BlockBreakEvent.class).findFirst().orElseThrow();
    assertThat(damage.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(damage.ignoreCancelled()).isTrue();
    assertThat(breaks.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(breaks.ignoreCancelled()).isTrue();
  }

  @Test
  void noBukkitHandlersRemain() {
    for (Method method : PickaxeDeepCore.class.getDeclaredMethods()) {
      for (Annotation annotation : method.getAnnotations()) {
        assertThat(annotation.annotationType().getName()).as(method.getName()).doesNotStartWith("org.bukkit");
      }
    }
  }
}
