package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.testkit.MemoryData;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class GolemSnowballTest {
  private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-0000000000f3");

  @Test
  void taggedSnowballsCarryTheLevelAndTheTargetUnderTheGolemSnowballKey() {
    ProjectileRef snowball = snowball();

    GolemSnowball.tag(snowball, 4, TARGET, false);

    assertThat(GolemSnowball.KEY.id().toString()).isEqualTo("adapt:golem_snowball");
    assertThat(snowball.data().get(GolemSnowball.KEY)).isEqualTo(4);
    assertThat(GolemSnowball.read(snowball)).contains(new GolemSnowball.Tag(4, TARGET, false));
    assertThat(snowball.data().get(GolemSnowball.EXTRA_KEY)).isNull();
  }

  @Test
  void snowballsWithoutATargetOrTagReadAsExpected() {
    ProjectileRef untargeted = snowball();
    GolemSnowball.tag(untargeted, 2, null, false);
    ProjectileRef vanilla = snowball();
    ProjectileRef corrupt = snowball();
    corrupt.data().set(GolemSnowball.KEY, 3);
    corrupt.data().set(GolemSnowball.TARGET_KEY, "not-a-uuid");

    assertThat(GolemSnowball.read(untargeted)).contains(new GolemSnowball.Tag(2, null, false));
    assertThat(GolemSnowball.read(vanilla)).isEmpty();
    assertThat(GolemSnowball.read(corrupt)).contains(new GolemSnowball.Tag(3, null, false));
  }

  @Test
  void extraSnowballsCarryTheExtraFlag() {
    ProjectileRef extra = snowball();

    GolemSnowball.tag(extra, 4, TARGET, true);

    assertThat(GolemSnowball.EXTRA_KEY.id().toString()).isEqualTo("adapt:golem_snowball_extra");
    assertThat(extra.data().get(GolemSnowball.EXTRA_KEY)).isEqualTo((byte) 1);
    assertThat(GolemSnowball.read(extra)).contains(new GolemSnowball.Tag(4, TARGET, true));
  }

  private static ProjectileRef snowball() {
    MemoryData data = new MemoryData();
    ProjectileRef snowball = mock(ProjectileRef.class);
    lenient().when(snowball.data()).thenReturn(data);
    return snowball;
  }
}
