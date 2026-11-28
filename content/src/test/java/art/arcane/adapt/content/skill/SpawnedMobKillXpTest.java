package art.arcane.adapt.content.skill;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.xp.MobOrigin;
import art.arcane.adapt.engine.xp.XpEarnings;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.config.TomlCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** rewardSpawnedMobKills decides whether a kill of a spawner mob pays Hunter XP. */
class SpawnedMobKillXpTest {
  @TempDir
  Path data;

  private AdaptConfig previous;
  private TestAdapt adapt;

  @AfterEach
  void stop() throws ReflectiveOperationException {
    if (adapt != null) {
      adapt.close();
      assertThat(adapt.handlerFailures()).isEmpty();
    }
    install(previous);
  }

  @Test
  void aSpawnerMobKillPaysNoHunterXpByDefault() throws Exception {
    double spawned = hunterXpForKill("rewardSpawnedMobKills = false\n", CreatureSpawnEvent.Reason.SPAWNER);
    adapt.close();
    adapt = null;
    double natural = hunterXpForKill("rewardSpawnedMobKills = false\n", CreatureSpawnEvent.Reason.NATURAL);

    assertThat(spawned).isZero();
    assertThat(natural).isPositive();
  }

  @Test
  void aSpawnerMobKillPaysLikeANaturalOneWhenRewarded() throws Exception {
    double spawned = hunterXpForKill("rewardSpawnedMobKills = true\n", CreatureSpawnEvent.Reason.SPAWNER);
    adapt.close();
    adapt = null;
    double natural = hunterXpForKill("rewardSpawnedMobKills = true\n", CreatureSpawnEvent.Reason.NATURAL);

    assertThat(spawned).isPositive().isEqualTo(natural);
  }

  private double hunterXpForKill(String config, CreatureSpawnEvent.Reason reason) throws Exception {
    AdaptConfig replaced = install(TomlCodec.fromToml(config, AdaptConfig.class));
    if (previous == null) {
      previous = replaced;
    }
    TestPlatform platform = TestPlatform.builder(data.resolve(reason.name())).build();
    FakePlayer player = platform.player("Hunter", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillHunter.class));
    AdaptPlayer profile = adapt.join(player);
    FakeLiving zombie = new FakeLiving(Id.minecraft("zombie"), new Loc(platform.world(), 3D, 61D, 0.5D));
    zombie.trait(EntityTrait.MONSTER);
    platform.world().add(zombie);
    platform.host().track(zombie);
    MobOrigin.record(zombie, reason);

    adapt.post(new EntityDeathEvent(zombie, player, player, player, DamageCause.ENTITY_ATTACK, new ArrayList<>(), 0));

    double requested = 0D;
    for (XpEarnings.SourceSnapshot source : profile.getData().getXpEarnings().snapshot()) {
      if (source.source().endsWith("adapt:hunter:kill:zombie")) {
        requested += source.requested();
      }
    }
    return requested;
  }

  private static AdaptConfig install(AdaptConfig config) throws ReflectiveOperationException {
    Field field = AdaptConfig.class.getDeclaredField("config");
    field.setAccessible(true);
    AdaptConfig replaced = (AdaptConfig) field.get(null);
    field.set(null, config);
    return replaced;
  }
}
