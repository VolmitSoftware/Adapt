package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class AxeSunderTest {
  @Test
  void toughnessShredRatioDefaultIsAFractionOfArmorShred() {
    AxeSunder.Config config = new AxeSunder.Config();
    assertThat(config.toughnessShredPerStackRatio).isGreaterThan(0D);
    assertThat(config.toughnessShredPerStackRatio).isLessThanOrEqualTo(1D);
  }

  @Test
  void toughnessShredAtBaseStaysBelowArmorShred() {
    AxeSunder.Config config = new AxeSunder.Config();
    double toughnessShred = config.shredPerStackBase * config.toughnessShredPerStackRatio;
    assertThat(toughnessShred).isGreaterThan(0D);
    assertThat(toughnessShred).isLessThan(config.shredPerStackBase);
  }

  @Test
  void unregisterRemovesModifiersFromTrackedLivingTargets() throws Exception {
    AxeSunder adaptation = new AxeSunder();
    LivingRef tracked = mock(LivingRef.class);
    when(tracked.isValid()).thenReturn(true);
    Map<UUID, Object> targets = field(field(adaptation, "marks"), "targets");
    targets.put(UUID.randomUUID(), sunderState(tracked, true, true));

    adaptation.unregister();

    Id expectedKey = Id.parse("adapt:axe-sunder");
    verify(tracked).removeModifier(Attributes.ARMOR, expectedKey);
    verify(tracked).removeModifier(Attributes.ARMOR_TOUGHNESS, expectedKey);
    assertThat(expectedKey.toString()).isEqualTo("adapt:axe-sunder");
    assertThat(targets).isEmpty();
  }

  @Test
  void unregisterSkipsInvalidTargetsAndStillClearsTracking() throws Exception {
    AxeSunder adaptation = new AxeSunder();
    LivingRef unloaded = mock(LivingRef.class);
    when(unloaded.isValid()).thenReturn(false);
    Map<UUID, Object> targets = field(field(adaptation, "marks"), "targets");
    targets.put(UUID.randomUUID(), sunderState(unloaded, true, true));
    targets.put(UUID.randomUUID(), sunderState(null, false, false));

    adaptation.unregister();

    verify(unloaded, never()).removeModifier(any(), any());
    assertThat(targets).isEmpty();
  }

  private static Object sunderState(LivingRef target, boolean armor, boolean toughness) throws Exception {
    Class<?> stateClass = Class.forName("art.arcane.adapt.content.adaptation.axe.AxeSunderMarks$SunderState");
    Constructor<?> constructor = stateClass.getDeclaredConstructor();
    constructor.setAccessible(true);
    Object state = constructor.newInstance();
    set(state, "target", target);
    set(state, "armor", armor);
    set(state, "toughness", toughness);
    return state;
  }

  private static void set(Object state, String name, Object value) throws Exception {
    Field field = state.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(state, value);
  }

  @SuppressWarnings("unchecked")
  private static <T> T field(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return (T) field.get(target);
  }
}
