package art.arcane.adapt.content.skill.golemancy;

import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpawnerMobTagTest {
  @Test
  void spawnersAndTrialSpawnersAreTagged() {
    assertThat(SpawnerMobTag.isSpawnerReason(SpawnReason.SPAWNER)).isTrue();
    assertThat(SpawnerMobTag.isSpawnerReason(SpawnReason.TRIAL_SPAWNER)).isTrue();
  }

  @Test
  void otherSpawnReasonsAreNotTagged() {
    assertThat(SpawnerMobTag.isSpawnerReason(SpawnReason.SPAWNER_EGG)).isFalse();
    assertThat(SpawnerMobTag.isSpawnerReason(SpawnReason.OMINOUS_ITEM_SPAWNER)).isFalse();
    assertThat(SpawnerMobTag.isSpawnerReason(SpawnReason.NATURAL)).isFalse();
    assertThat(SpawnerMobTag.isSpawnerReason(SpawnReason.BUILD_IRONGOLEM)).isFalse();
    assertThat(SpawnerMobTag.isSpawnerReason(null)).isFalse();
  }

  @Test
  void markedMobsReadAsSpawnerMobsUnderTheGolemancyKey() {
    Zombie spawned = StoredEntityData.entity(Zombie.class);
    Zombie natural = StoredEntityData.entity(Zombie.class);

    SpawnerMobTag.mark(spawned);

    assertThat(SpawnerMobTag.isTagged(spawned)).isTrue();
    assertThat(SpawnerMobTag.isTagged(natural)).isFalse();
    assertThat(SpawnerMobTag.KEY.toString()).isEqualTo("adapt:golemancy_spawner_mob");
    assertThat(spawned.getPersistentDataContainer().get(SpawnerMobTag.KEY, PersistentDataType.BYTE)).isEqualTo((byte) 1);
  }
}
