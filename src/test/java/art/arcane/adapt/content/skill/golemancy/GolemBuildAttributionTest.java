package art.arcane.adapt.content.skill.golemancy;

import art.arcane.adapt.content.skill.golemancy.GolemBuildAttribution.Build;
import art.arcane.adapt.content.skill.golemancy.GolemBuildAttribution.GolemSpawn;
import art.arcane.adapt.content.skill.golemancy.GolemBuildAttribution.HeadPlacement;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GolemBuildAttributionTest {
  private static final double RADIUS = 3.0D;
  private static final long WINDOW_MILLIS = 250L;
  private static final long MILLI = 1_000_000L;
  private static final long T0 = 5_000L * MILLI;

  @Test
  void placementThenSpawnCreditsTheBuilder() {
    GolemBuildAttribution attribution = new GolemBuildAttribution();
    UUID builder = UUID.randomUUID();
    GolemSpawn spawn = spawn("world", 10.5D, 64.05D, 20.5D, T0 + MILLI);

    assertThat(attribution.recordPlacement(head("world", 10, 66, 20, builder, T0), RADIUS, WINDOW_MILLIS)).isEmpty();

    assertThat(attribution.recordSpawn(spawn, RADIUS, WINDOW_MILLIS)).contains(new Build(builder, spawn));
  }

  @Test
  void spawnThenPlacementCreditsTheBuilder() {
    GolemBuildAttribution attribution = new GolemBuildAttribution();
    UUID builder = UUID.randomUUID();
    GolemSpawn spawn = spawn("world", 10.5D, 64.05D, 20.5D, T0);

    assertThat(attribution.recordSpawn(spawn, RADIUS, WINDOW_MILLIS)).isEmpty();

    assertThat(attribution.recordPlacement(head("world", 10, 66, 20, builder, T0 + 2 * MILLI), RADIUS, WINDOW_MILLIS))
        .contains(new Build(builder, spawn));
  }

  @Test
  void eachPlacementCreditsOnlyOneGolem() {
    GolemBuildAttribution attribution = new GolemBuildAttribution();
    UUID builder = UUID.randomUUID();
    attribution.recordPlacement(head("world", 10, 66, 20, builder, T0), RADIUS, WINDOW_MILLIS);

    assertThat(attribution.recordSpawn(spawn("world", 10.5D, 64.05D, 20.5D, T0 + MILLI), RADIUS, WINDOW_MILLIS)).isPresent();
    assertThat(attribution.recordSpawn(spawn("world", 10.5D, 64.05D, 20.5D, T0 + MILLI), RADIUS, WINDOW_MILLIS)).isEmpty();
  }

  @Test
  void eachHeldSpawnIsCreditedOnlyOnce() {
    GolemBuildAttribution attribution = new GolemBuildAttribution();
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    attribution.recordSpawn(spawn("world", 10.5D, 64.05D, 20.5D, T0), RADIUS, WINDOW_MILLIS);

    assertThat(attribution.recordPlacement(head("world", 10, 66, 20, first, T0 + MILLI), RADIUS, WINDOW_MILLIS)).isPresent();
    assertThat(attribution.recordPlacement(head("world", 10, 66, 20, second, T0 + MILLI), RADIUS, WINDOW_MILLIS)).isEmpty();
  }

  @Test
  void nearestPlacementWins() {
    GolemBuildAttribution attribution = new GolemBuildAttribution();
    UUID far = UUID.randomUUID();
    UUID near = UUID.randomUUID();
    attribution.recordPlacement(head("world", 12, 66, 20, far, T0), RADIUS, WINDOW_MILLIS);
    attribution.recordPlacement(head("world", 10, 66, 20, near, T0), RADIUS, WINDOW_MILLIS);

    assertThat(attribution.recordSpawn(spawn("world", 10.5D, 64.05D, 20.5D, T0 + MILLI), RADIUS, WINDOW_MILLIS))
        .map(Build::builder)
        .contains(near);
  }

  @Test
  void spawnMustFallInsideTheWindowInEitherOrder() {
    GolemBuildAttribution inside = new GolemBuildAttribution();
    inside.recordPlacement(head("world", 10, 66, 20, UUID.randomUUID(), T0), RADIUS, WINDOW_MILLIS);
    assertThat(inside.recordSpawn(spawn("world", 10.5D, 64.05D, 20.5D, T0 + WINDOW_MILLIS * MILLI), RADIUS, WINDOW_MILLIS)).isPresent();

    GolemBuildAttribution late = new GolemBuildAttribution();
    late.recordPlacement(head("world", 10, 66, 20, UUID.randomUUID(), T0), RADIUS, WINDOW_MILLIS);
    assertThat(late.recordSpawn(spawn("world", 10.5D, 64.05D, 20.5D, T0 + WINDOW_MILLIS * MILLI + 1), RADIUS, WINDOW_MILLIS)).isEmpty();

    GolemBuildAttribution latePlacement = new GolemBuildAttribution();
    latePlacement.recordSpawn(spawn("world", 10.5D, 64.05D, 20.5D, T0), RADIUS, WINDOW_MILLIS);
    assertThat(latePlacement.recordPlacement(head("world", 10, 66, 20, UUID.randomUUID(), T0 + WINDOW_MILLIS * MILLI + 1), RADIUS, WINDOW_MILLIS)).isEmpty();
  }

  @Test
  void spawnMustFallInsideTheRadius() {
    GolemBuildAttribution edge = new GolemBuildAttribution();
    edge.recordPlacement(head("world", 10, 66, 20, UUID.randomUUID(), T0), RADIUS, WINDOW_MILLIS);
    assertThat(edge.recordSpawn(spawn("world", 10.5D, 63.5D, 20.5D, T0), RADIUS, WINDOW_MILLIS)).isPresent();

    GolemBuildAttribution beyond = new GolemBuildAttribution();
    beyond.recordPlacement(head("world", 10, 66, 20, UUID.randomUUID(), T0), RADIUS, WINDOW_MILLIS);
    assertThat(beyond.recordSpawn(spawn("world", 10.5D, 63.49D, 20.5D, T0), RADIUS, WINDOW_MILLIS)).isEmpty();
  }

  @Test
  void differentWorldsNeverMatch() {
    GolemBuildAttribution attribution = new GolemBuildAttribution();
    attribution.recordPlacement(head("world", 10, 66, 20, UUID.randomUUID(), T0), RADIUS, WINDOW_MILLIS);

    assertThat(attribution.recordSpawn(spawn("world_nether", 10.5D, 64.05D, 20.5D, T0), RADIUS, WINDOW_MILLIS)).isEmpty();
  }

  @Test
  void expiredPlacementsAreDropped() {
    GolemBuildAttribution attribution = new GolemBuildAttribution();
    attribution.recordPlacement(head("world", 10, 66, 20, UUID.randomUUID(), T0), RADIUS, WINDOW_MILLIS);

    attribution.expire(T0 + WINDOW_MILLIS * MILLI + 1, WINDOW_MILLIS);

    assertThat(attribution.recordSpawn(spawn("world", 10.5D, 64.05D, 20.5D, T0), RADIUS, WINDOW_MILLIS)).isEmpty();
  }

  @Test
  void expiredSpawnsAreDropped() {
    GolemBuildAttribution attribution = new GolemBuildAttribution();
    attribution.recordSpawn(spawn("world", 10.5D, 64.05D, 20.5D, T0), RADIUS, WINDOW_MILLIS);

    attribution.expire(T0 + WINDOW_MILLIS * MILLI + 1, WINDOW_MILLIS);

    assertThat(attribution.recordPlacement(head("world", 10, 66, 20, UUID.randomUUID(), T0), RADIUS, WINDOW_MILLIS)).isEmpty();
  }

  @Test
  void recordingSweepsEntriesOlderThanTheWindow() {
    GolemBuildAttribution attribution = new GolemBuildAttribution();
    attribution.recordPlacement(head("world", 10, 66, 20, UUID.randomUUID(), T0), RADIUS, WINDOW_MILLIS);

    attribution.recordPlacement(head("world", 500, 66, 500, UUID.randomUUID(), T0 + 10 * WINDOW_MILLIS * MILLI), RADIUS, WINDOW_MILLIS);

    assertThat(attribution.recordSpawn(spawn("world", 10.5D, 64.05D, 20.5D, T0), RADIUS, WINDOW_MILLIS)).isEmpty();
  }

  @Test
  void bodyKeyNamesOnlyTheThreeBuildReasons() {
    assertThat(GolemBuildAttribution.bodyKey(SpawnReason.BUILD_SNOWMAN)).isEqualTo("snow");
    assertThat(GolemBuildAttribution.bodyKey(SpawnReason.BUILD_COPPERGOLEM)).isEqualTo("copper");
    assertThat(GolemBuildAttribution.bodyKey(SpawnReason.BUILD_IRONGOLEM)).isEqualTo("iron");
    assertThat(GolemBuildAttribution.bodyKey(SpawnReason.BUILD_WITHER)).isNull();
    assertThat(GolemBuildAttribution.bodyKey(SpawnReason.NATURAL)).isNull();
    assertThat(GolemBuildAttribution.bodyKey(null)).isNull();
  }

  @Test
  void builderStampRecordsTheBuilderAndTime() {
    IronGolem golem = StoredEntityData.entity(IronGolem.class);
    UUID builder = UUID.randomUUID();

    GolemBuildAttribution.stamp(golem, builder, 1_760_000_000_000L);

    assertThat(golem.getPersistentDataContainer().get(GolemBuildAttribution.BUILDER_KEY, PersistentDataType.STRING))
        .isEqualTo(builder + ";1760000000000");
    assertThat(GolemBuildAttribution.builderOf(golem)).contains(builder);
  }

  @Test
  void builderOfIgnoresMissingOrCorruptStamps() {
    IronGolem unstamped = StoredEntityData.entity(IronGolem.class);
    IronGolem corrupt = StoredEntityData.entity(IronGolem.class);
    IronGolem noTime = StoredEntityData.entity(IronGolem.class);
    corrupt.getPersistentDataContainer().set(GolemBuildAttribution.BUILDER_KEY, PersistentDataType.STRING, "not-a-uuid;5");
    noTime.getPersistentDataContainer().set(GolemBuildAttribution.BUILDER_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());

    assertThat(GolemBuildAttribution.builderOf(unstamped)).isEmpty();
    assertThat(GolemBuildAttribution.builderOf(corrupt)).isEmpty();
    assertThat(GolemBuildAttribution.builderOf(noTime)).isEmpty();
    assertThat(GolemBuildAttribution.builderOf(null)).isEmpty();
  }

  private static HeadPlacement head(String world, int x, int y, int z, UUID builder, long atNanos) {
    return new HeadPlacement(world, x, y, z, builder, atNanos);
  }

  private static GolemSpawn spawn(String world, double x, double y, double z, long atNanos) {
    return new GolemSpawn(mock(Entity.class), SpawnReason.BUILD_SNOWMAN, world, x, y, z, atNanos);
  }
}
