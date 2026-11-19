package art.arcane.adapt.platform;

import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContentAliasesTest {
  @Test
  void mountOnlyAttributeNamesMapBackWithoutBecomingOlderNames() {
    Id jumpStrength = Id.minecraft("jump_strength");
    Id horseJumpStrength = Id.minecraft("horse.jump_strength");

    assertThat(ContentAliases.modern(RegistryKind.ATTRIBUTE, horseJumpStrength)).isEqualTo(List.of(jumpStrength));
    assertThat(ContentAliases.legacyIds(RegistryKind.ATTRIBUTE, jumpStrength)).doesNotContain(horseJumpStrength);
    assertThat(ContentAliases.resolve(RegistryKind.ATTRIBUTE, jumpStrength, horseJumpStrength::equals)).isNull();
    assertThat(ContentAliases.mountAttribute(jumpStrength)).isEqualTo(horseJumpStrength);
  }

  @Test
  void renamedAttributesMapBothWays() {
    Id maxHealth = Id.minecraft("max_health");
    Id genericMaxHealth = Id.minecraft("generic.max_health");

    assertThat(ContentAliases.modern(RegistryKind.ATTRIBUTE, genericMaxHealth)).isEqualTo(List.of(maxHealth));
    assertThat(ContentAliases.resolve(RegistryKind.ATTRIBUTE, maxHealth, genericMaxHealth::equals)).isEqualTo(genericMaxHealth);
  }
}
