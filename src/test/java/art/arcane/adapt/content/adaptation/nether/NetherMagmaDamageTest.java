package art.arcane.adapt.content.adaptation.nether;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Registry;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class NetherMagmaDamageTest {
  @BeforeAll
  static void initializeDamageTypes() {
    RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
    try (MockedStatic<RegistryAccess> registry = mockStatic(RegistryAccess.class)) {
      registry.when(RegistryAccess::registryAccess).thenReturn(access);
      assertThat(Registry.SOUNDS).isNotNull();
      Registry<DamageType> types = access.getRegistry(RegistryKey.DAMAGE_TYPE);
      doAnswer(call -> mock(DamageType.class)).when(types).getOrThrow(any(Key.class));
      assertThat(DamageType.HOT_FLOOR).isNotNull();
    }
  }

  @Test
  void contactFromMagmaTriggersBothNetherAdaptations() {
    EntityDamageEvent event = damage(EntityDamageEvent.DamageCause.CONTACT, DamageType.HOT_FLOOR);
    assertThat(NetherBlazeLeech.isFireDamage(event)).isTrue();
    assertThat(NetherAshwalker.isMagmaDamage(event)).isTrue();
  }

  @Test
  void otherContactDamageCannotTriggerMagmaProtectionOrLeech() {
    for (DamageType type : new DamageType[]{DamageType.CACTUS, DamageType.SWEET_BERRY_BUSH}) {
      EntityDamageEvent event = damage(EntityDamageEvent.DamageCause.CONTACT, type);
      assertThat(NetherBlazeLeech.isFireDamage(event)).isFalse();
      assertThat(NetherAshwalker.isMagmaDamage(event)).isFalse();
    }
  }

  @Test
  void ordinaryFireStillTriggersLeechWithoutMagmaImmunity() {
    EntityDamageEvent event = damage(EntityDamageEvent.DamageCause.FIRE, DamageType.IN_FIRE);
    assertThat(NetherBlazeLeech.isFireDamage(event)).isTrue();
    assertThat(NetherAshwalker.isMagmaDamage(event)).isFalse();
  }

  private static EntityDamageEvent damage(EntityDamageEvent.DamageCause cause, DamageType type) {
    EntityDamageEvent event = mock(EntityDamageEvent.class);
    DamageSource source = mock(DamageSource.class);
    when(event.getCause()).thenReturn(cause);
    when(event.getDamageSource()).thenReturn(source);
    when(source.getDamageType()).thenReturn(type);
    return event;
  }
}
