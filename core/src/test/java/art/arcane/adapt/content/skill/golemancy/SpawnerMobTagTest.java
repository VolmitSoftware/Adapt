package art.arcane.adapt.content.skill.golemancy;

import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent.Reason;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpawnerMobTagTest {
  @Test
  void spawnersAndTrialSpawnersAreTagged() {
    assertThat(SpawnerMobTag.isSpawnerReason(Reason.SPAWNER)).isTrue();
    assertThat(SpawnerMobTag.isSpawnerReason(Reason.TRIAL_SPAWNER)).isTrue();
  }

  @Test
  void otherSpawnReasonsAreNotTagged() {
    assertThat(SpawnerMobTag.isSpawnerReason(Reason.SPAWNER_EGG)).isFalse();
    assertThat(SpawnerMobTag.isSpawnerReason(Reason.OMINOUS_ITEM_SPAWNER)).isFalse();
    assertThat(SpawnerMobTag.isSpawnerReason(Reason.NATURAL)).isFalse();
    assertThat(SpawnerMobTag.isSpawnerReason(Reason.BUILD_IRONGOLEM)).isFalse();
    assertThat(SpawnerMobTag.isSpawnerReason(null)).isFalse();
  }

  @Test
  void markedMobsReadAsSpawnerMobsUnderTheGolemancyKey() {
    MobRef spawned = StoredEntityData.entity(MobRef.class);
    MobRef natural = StoredEntityData.entity(MobRef.class);

    SpawnerMobTag.mark(spawned);

    assertThat(SpawnerMobTag.isTagged(spawned)).isTrue();
    assertThat(SpawnerMobTag.isTagged(natural)).isFalse();
    assertThat(SpawnerMobTag.KEY.id().toString()).isEqualTo("adapt:golemancy_spawner_mob");
    assertThat(spawned.data().get(SpawnerMobTag.KEY)).isEqualTo((byte) 1);
  }
}
