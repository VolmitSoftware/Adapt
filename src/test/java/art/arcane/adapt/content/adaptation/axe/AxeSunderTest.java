package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.api.version.IAttribute;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    LivingEntity tracked = mock(LivingEntity.class);
    when(tracked.isValid()).thenReturn(true);
    IAttribute armor = mock(IAttribute.class);
    IAttribute toughness = mock(IAttribute.class);
    Map<UUID, Object> targets = field(adaptation, "targets");
    targets.put(UUID.randomUUID(), sunderState(tracked, armor, toughness));

    adaptation.unregister();

    UUID expectedId = UUID.nameUUIDFromBytes("adapt-axe-sunder".getBytes());
    ArgumentCaptor<NamespacedKey> armorKey = ArgumentCaptor.forClass(NamespacedKey.class);
    ArgumentCaptor<NamespacedKey> toughnessKey = ArgumentCaptor.forClass(NamespacedKey.class);
    verify(armor).removeModifier(eq(expectedId), armorKey.capture());
    verify(toughness).removeModifier(eq(expectedId), toughnessKey.capture());
    assertThat(armorKey.getValue().toString()).isEqualTo("adapt:axe-sunder");
    assertThat(toughnessKey.getValue().toString()).isEqualTo("adapt:axe-sunder");
    assertThat(targets).isEmpty();
  }

  @Test
  void unregisterSkipsInvalidTargetsAndStillClearsTracking() throws Exception {
    AxeSunder adaptation = new AxeSunder();
    LivingEntity unloaded = mock(LivingEntity.class);
    when(unloaded.isValid()).thenReturn(false);
    IAttribute armor = mock(IAttribute.class);
    IAttribute toughness = mock(IAttribute.class);
    Map<UUID, Object> targets = field(adaptation, "targets");
    targets.put(UUID.randomUUID(), sunderState(unloaded, armor, toughness));
    targets.put(UUID.randomUUID(), sunderState(null, null, null));

    adaptation.unregister();

    verifyNoInteractions(armor, toughness);
    assertThat(targets).isEmpty();
  }

  private static Object sunderState(LivingEntity target, IAttribute armor, IAttribute toughness) throws Exception {
    Class<?> stateClass = Class.forName("art.arcane.adapt.content.adaptation.axe.AxeSunder$SunderState");
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
