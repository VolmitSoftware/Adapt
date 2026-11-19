package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.TestVersion;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** On 1.20.1 jump strength is the horse-only {@code horse.jump_strength}; core asks for {@code jump_strength}. */
class BukkitLivingRefMountAttributeTest {
  private static final Id JUMP_STRENGTH = Id.minecraft("jump_strength");

  @BeforeAll
  static void registries() {
    TestVersion.initializeRegistries();
  }

  @Test
  void horsesExposeTheirJumpStrengthUnderTheModernName() {
    Horse horse = mock(Horse.class);
    lenient().when(horse.getUniqueId()).thenReturn(UUID.randomUUID());
    AttributeInstance jump = mock(AttributeInstance.class);
    when(jump.getValue()).thenReturn(0.7D);
    when(horse.getAttribute(Attribute.HORSE_JUMP_STRENGTH)).thenReturn(jump);
    BukkitLivingRef living = new BukkitLivingRef(horse);

    assertThat(living.hasAttribute(JUMP_STRENGTH)).isTrue();
    assertThat(living.attributeValue(JUMP_STRENGTH)).isEqualTo(0.7D);
    assertThat(BukkitLivingRef.instance(horse, JUMP_STRENGTH)).isSameAs(jump);
  }

  @Test
  void otherMobsHaveNoJumpStrength() {
    Zombie zombie = mock(Zombie.class);
    lenient().when(zombie.getUniqueId()).thenReturn(UUID.randomUUID());
    BukkitLivingRef living = new BukkitLivingRef(zombie);

    assertThat(living.hasAttribute(JUMP_STRENGTH)).isFalse();
  }
}
