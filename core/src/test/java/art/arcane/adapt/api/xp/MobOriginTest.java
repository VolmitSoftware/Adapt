package art.arcane.adapt.api.xp;

import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MobOriginTest {
  @Test
  void farmReasonsAreFarmedAndNaturalSpawnsAreNot() {
    assertThat(MobOrigin.isFarmedReason(CreatureSpawnEvent.Reason.SPAWNER)).isTrue();
    assertThat(MobOrigin.isFarmedReason(CreatureSpawnEvent.Reason.BUCKET)).isTrue();
    assertThat(MobOrigin.isFarmedReason(CreatureSpawnEvent.Reason.BREEDING)).isTrue();
    assertThat(MobOrigin.isFarmedReason(CreatureSpawnEvent.Reason.NETHER_PORTAL)).isTrue();
    assertThat(MobOrigin.isFarmedReason(CreatureSpawnEvent.Reason.NATURAL)).isFalse();
    assertThat(MobOrigin.isFarmedReason(CreatureSpawnEvent.Reason.TRIAL_SPAWNER)).isFalse();
  }

  @Test
  void playerBuiltWithersAreFarmed() {
    assertThat(MobOrigin.isFarmedReason(CreatureSpawnEvent.Reason.BUILD_WITHER)).isTrue();
  }
}
