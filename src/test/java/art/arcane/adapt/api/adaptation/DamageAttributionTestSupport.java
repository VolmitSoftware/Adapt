package art.arcane.adapt.api.adaptation;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Registry;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class DamageAttributionTestSupport {
  private DamageAttributionTestSupport() {
  }

  public static void initializeDamageTypes() {
    RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
    try (MockedStatic<RegistryAccess> registry = mockStatic(RegistryAccess.class)) {
      registry.when(RegistryAccess::registryAccess).thenReturn(access);
      assertThat(Registry.SOUNDS).isNotNull();
      Registry<DamageType> types = access.getRegistry(RegistryKey.DAMAGE_TYPE);
      doAnswer(call -> mock(DamageType.class)).when(types).getOrThrow(any(Key.class));
      assertThat(DamageType.MAGIC).isNotNull();
    }
  }

  public static void assertMagicDamage(DamageProbe probe, Runnable action) {
    DamageSource source = mock(DamageSource.class);
    DamageSource.Builder builder = mock(DamageSource.Builder.class, RETURNS_SELF);
    when(builder.build()).thenReturn(source);
    try (MockedStatic<DamageSource> sources = mockStatic(DamageSource.class)) {
      sources.when(() -> DamageSource.builder(DamageType.MAGIC)).thenReturn(builder);
      action.run();
      verify(builder).withCausingEntity(probe.caster());
      verify(builder).withDirectEntity(probe.caster());
      verify(probe.target()).damage(probe.amount(), source);
      verify(probe.target(), never()).damage(anyDouble());
    }
  }

  public record DamageProbe(LivingEntity target, Player caster, double amount) {
  }
}
