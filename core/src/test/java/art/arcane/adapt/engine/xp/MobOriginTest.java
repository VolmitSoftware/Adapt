package art.arcane.adapt.engine.xp;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.util.config.TomlCodec;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class MobOriginTest {
  private static final FakeWorld WORLD = new FakeWorld("world");

  @Test
  void farmReasonsAreFarmedAndNaturalSpawnsAreNot() {
    assertThat(MobOrigin.isFarmedReason(CreatureSpawnEvent.Reason.SPAWNER)).isTrue();
    assertThat(MobOrigin.isFarmedReason(CreatureSpawnEvent.Reason.BUCKET)).isTrue();
    assertThat(MobOrigin.isFarmedReason(CreatureSpawnEvent.Reason.BREEDING)).isTrue();
    assertThat(MobOrigin.isFarmedReason(CreatureSpawnEvent.Reason.NETHER_PORTAL)).isTrue();
    assertThat(MobOrigin.isFarmedReason(CreatureSpawnEvent.Reason.NATURAL)).isFalse();
  }

  @Test
  void trialSpawnerMobsAreFarmed() {
    assertThat(MobOrigin.isFarmedReason(CreatureSpawnEvent.Reason.TRIAL_SPAWNER)).isTrue();
  }

  @Test
  void playerBuiltWithersAreFarmed() {
    assertThat(MobOrigin.isFarmedReason(CreatureSpawnEvent.Reason.BUILD_WITHER)).isTrue();
  }

  @Test
  void spawnedMobKillsAreNotRewardedByDefault() throws Exception {
    assertThat(new AdaptConfig().isRewardSpawnedMobKills()).isFalse();
    AdaptConfig previous = install(TomlCodec.fromToml("rewardSpawnedMobKills = false\n", AdaptConfig.class));
    try {
      assertThat(MobOrigin.isFarmed(spawned(CreatureSpawnEvent.Reason.SPAWNER))).isTrue();
      assertThat(MobOrigin.isFarmed(spawned(CreatureSpawnEvent.Reason.SPAWNER_EGG))).isTrue();
      assertThat(MobOrigin.isFarmed(spawned(CreatureSpawnEvent.Reason.TRIAL_SPAWNER))).isTrue();
      assertThat(MobOrigin.isFarmed(spawned(CreatureSpawnEvent.Reason.NATURAL))).isFalse();
    } finally {
      install(previous);
    }
  }

  @Test
  void rewardingSpawnedMobKillsTreatsThemAsNatural() throws Exception {
    AdaptConfig previous = install(TomlCodec.fromToml("rewardSpawnedMobKills = true\n", AdaptConfig.class));
    try {
      assertThat(MobOrigin.isFarmed(spawned(CreatureSpawnEvent.Reason.SPAWNER))).isFalse();
      assertThat(MobOrigin.isFarmed(spawned(CreatureSpawnEvent.Reason.SPAWNER_EGG))).isFalse();
      assertThat(MobOrigin.isFarmed(spawned(CreatureSpawnEvent.Reason.NATURAL))).isFalse();
    } finally {
      install(previous);
    }
  }

  private static FakeLiving spawned(CreatureSpawnEvent.Reason reason) {
    FakeLiving mob = new FakeLiving(Id.minecraft("zombie"), new Loc(WORLD, 0D, 64D, 0D));
    MobOrigin.record(mob, reason);
    return mob;
  }

  private static AdaptConfig install(AdaptConfig config) throws ReflectiveOperationException {
    Field field = AdaptConfig.class.getDeclaredField("config");
    field.setAccessible(true);
    AdaptConfig previous = (AdaptConfig) field.get(null);
    field.set(null, config);
    return previous;
  }
}
