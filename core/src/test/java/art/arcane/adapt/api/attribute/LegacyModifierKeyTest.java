package art.arcane.adapt.api.attribute;

import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakeWorld;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyModifierKeyTest {
  private static final Id KEY = Id.adapt("tame-stable-speed");
  private static final Id LEGACY_UUID_KEY =
      Id.minecraft(UUID.nameUUIDFromBytes("adapt-tame-stable-speed".getBytes(StandardCharsets.UTF_8)).toString());
  private static final Id LEGACY_NAME_KEY = Id.of("bukkit", "adapt-tame-stable-speed");

  @Test
  void matchesCurrentKeyLegacyUuidAndLegacyName() {
    LegacyModifierKey key = LegacyModifierKey.of(KEY);

    assertThat(key.matches(KEY)).isTrue();
    assertThat(key.matches(LEGACY_UUID_KEY)).isTrue();
    assertThat(key.matches(LEGACY_NAME_KEY)).isTrue();
    assertThat(key.matches(Id.adapt("tame-stable-jump"))).isFalse();
    assertThat(key.matches(Id.minecraft(UUID.nameUUIDFromBytes("adapt-tame-stable-jump".getBytes(StandardCharsets.UTF_8)).toString()))).isFalse();
  }

  @Test
  void setReplacesLegacyModifierInsteadOfStacking() {
    FakeLiving horse = new FakeLiving(Id.minecraft("horse"), new Loc(new FakeWorld("world"), 0, 64, 0, 0F, 0F));
    horse.setModifier(Attributes.MOVEMENT_SPEED, new AttributeMod(LEGACY_UUID_KEY, 0.2D, AttributeOperation.ADD_MULTIPLIED_BASE));
    horse.setModifier(Attributes.MOVEMENT_SPEED, new AttributeMod(Id.adapt("other"), 0.5D, AttributeOperation.ADD_VALUE));
    LegacyModifierKey key = LegacyModifierKey.of(KEY);

    assertThat(key.present(horse, Attributes.MOVEMENT_SPEED)).isTrue();
    key.set(horse, Attributes.MOVEMENT_SPEED, 0.3D, AttributeOperation.ADD_MULTIPLIED_BASE);

    assertThat(horse.modifiers(Attributes.MOVEMENT_SPEED))
        .extracting(AttributeMod::key)
        .containsExactlyInAnyOrder(KEY, Id.adapt("other"));

    key.remove(horse, Attributes.MOVEMENT_SPEED);
    assertThat(horse.modifiers(Attributes.MOVEMENT_SPEED)).extracting(AttributeMod::key).containsExactly(Id.adapt("other"));
  }
}
