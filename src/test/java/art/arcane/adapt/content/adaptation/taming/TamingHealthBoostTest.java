package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.IBindings;
import art.arcane.adapt.api.version.Version;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Tameable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class TamingHealthBoostTest extends AdaptTestBase {
  @BeforeAll
  static void initializeAttributeRegistry() {
    RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
    try (MockedStatic<RegistryAccess> registry = mockStatic(RegistryAccess.class)) {
      registry.when(RegistryAccess::registryAccess).thenReturn(access);
      Registry<Attribute> attributes = Registry.ATTRIBUTE;
      when(access.getRegistry(RegistryKey.ATTRIBUTE)).thenReturn(attributes);
      doAnswer(call -> {
        Key key = call.getArgument(0);
        Attribute attribute = mock(Attribute.class);
        when(attribute.getKey()).thenReturn(new NamespacedKey(key.namespace(), key.value()));
        return attribute;
      }).when(attributes).getOrThrow(any(Key.class));
      doAnswer(call -> {
        NamespacedKey key = call.getArgument(0);
        Attribute attribute = mock(Attribute.class);
        when(attribute.getKey()).thenReturn(key);
        return attribute;
      }).when(attributes).get(any(NamespacedKey.class));
      assertThat(Attribute.MAX_HEALTH).isNotNull();
    }
  }

  @ParameterizedTest
  @CsvSource({"true,40", "true,10", "false,40", "false,10"})
  void removingOwnedHealthBonusClampsOnlyExcessCurrentHealth(boolean cleanup, double health) throws ReflectiveOperationException {
    TamingHealthBoost boost = new TamingHealthBoost();
    Tameable pet = mock(Tameable.class);
    IAttribute attribute = mock(IAttribute.class);
    IBindings bindings = mock(IBindings.class);
    when(pet.getUniqueId()).thenReturn(UUID.randomUUID());
    when(pet.isValid()).thenReturn(true);
    when(pet.getHealth()).thenReturn(health);
    when(attribute.hasModifier(any(), any())).thenReturn(true);
    when(attribute.getValue()).thenReturn(20D);
    when(bindings.getAttribute(eq(pet), any())).thenReturn(attribute);
    try (MockedStatic<Version> version = mockStatic(Version.class)) {
      version.when(Version::get).thenReturn(bindings);
      Method removal = cleanup
          ? TamingHealthBoost.class.getDeclaredMethod("removeHealthModifier", Tameable.class)
          : TamingHealthBoost.class.getDeclaredMethod("update", Tameable.class, int.class);
      removal.setAccessible(true);
      if (cleanup) {
        removal.invoke(boost, pet);
      } else {
        removal.invoke(boost, pet, 0);
      }
      verify(attribute).removeModifier(any(), any());
      if (health > 20D) {
        verify(pet).setHealth(20D);
      } else {
        verify(pet, never()).setHealth(anyDouble());
      }
    }
  }

  @Test
  void firstActiveSampleDoesNotPreCreditTime() {
    assertThat(TamingHealthBoost.elapsedActiveTicks(null, 4753L)).isZero();
  }

  @Test
  void laterActiveSamplesUseActualElapsedTime() {
    assertThat(TamingHealthBoost.elapsedActiveTicks(1000L, 5753L)).isCloseTo(95.06D, offset(0.0001D));
  }
}
