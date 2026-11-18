package art.arcane.adapt.api.golem;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GolemTypesTest {
  @Test
  void bodyIdsAreStableAndRoundTrip() {
    assertThat(GolemBody.FROST.id()).isEqualTo("frost");
    assertThat(GolemBody.COPPER.id()).isEqualTo("copper");
    assertThat(GolemBody.IRON.id()).isEqualTo("iron");
    assertThat(GolemBody.BONE.id()).isEqualTo("bone");
    for (GolemBody body : GolemBody.values()) {
      assertThat(GolemBody.fromId(body.id())).contains(body);
    }
    assertThat(GolemBody.fromId("FROST")).isEmpty();
    assertThat(GolemBody.fromId(null)).isEmpty();
  }

  @Test
  void bodyResolvesFromTheEntityType() {
    assertThat(GolemBody.of(typed(MobRef.class, Id.minecraft("snow_golem")))).contains(GolemBody.FROST);
    assertThat(GolemBody.of(typed(MobRef.class, Id.minecraft("copper_golem")))).contains(GolemBody.COPPER);
    assertThat(GolemBody.of(typed(MobRef.class, Id.minecraft("iron_golem")))).contains(GolemBody.IRON);
    assertThat(GolemBody.of(typed(MobRef.class, Id.minecraft("skeleton")))).contains(GolemBody.BONE);
    assertThat(GolemBody.of(typed(MobRef.class, Id.minecraft("wither_skeleton")))).isEmpty();
    assertThat(GolemBody.of(typed(MobRef.class, Id.minecraft("zombie")))).isEmpty();
    assertThat(GolemBody.of(null)).isEmpty();
  }

  @Test
  void everyBodyButCopperFights() {
    assertThat(GolemBody.FROST.fights()).isTrue();
    assertThat(GolemBody.IRON.fights()).isTrue();
    assertThat(GolemBody.BONE.fights()).isTrue();
    assertThat(GolemBody.COPPER.fights()).isFalse();
  }

  @Test
  void bodyDisplayNamesAreDistinctLocaleKeys() {
    Set<String> keys = new HashSet<>();
    for (GolemBody body : GolemBody.values()) {
      keys.add(body.displayName().id());
    }
    assertThat(keys).containsExactlyInAnyOrder("golemancy.body.frost", "golemancy.body.copper", "golemancy.body.iron", "golemancy.body.bone");
  }

  @Test
  void modeIdsAreStableAndRoundTrip() {
    assertThat(GolemMode.WORK.id()).isEqualTo("work");
    assertThat(GolemMode.FOLLOW.id()).isEqualTo("follow");
    assertThat(GolemMode.STAY.id()).isEqualTo("stay");
    assertThat(GolemMode.HOME.id()).isEqualTo("home");
    for (GolemMode mode : GolemMode.values()) {
      assertThat(GolemMode.fromId(mode.id())).contains(mode);
    }
    assertThat(GolemMode.fromId("wander")).isEmpty();
    assertThat(GolemMode.fromId(null)).isEmpty();
  }

  private static <T extends EntityRef> EntityRef typed(Class<T> type, Id entityType) {
    T entity = mock(type);
    when(entity.uuid()).thenReturn(UUID.randomUUID());
    when(entity.type()).thenReturn(entityType);
    return entity;
  }
}
