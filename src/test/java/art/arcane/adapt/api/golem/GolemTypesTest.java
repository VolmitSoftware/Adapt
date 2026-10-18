package art.arcane.adapt.api.golem;

import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
    assertThat(GolemBody.of(mock(Snowman.class))).contains(GolemBody.FROST);
    assertThat(GolemBody.of(mock(CopperGolem.class))).contains(GolemBody.COPPER);
    assertThat(GolemBody.of(mock(IronGolem.class))).contains(GolemBody.IRON);
    assertThat(GolemBody.of(mock(Skeleton.class))).contains(GolemBody.BONE);
    assertThat(GolemBody.of(mock(WitherSkeleton.class))).isEmpty();
    assertThat(GolemBody.of(mock(Zombie.class))).isEmpty();
    assertThat(GolemBody.of(null)).isEmpty();
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
}
