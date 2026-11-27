package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.xp.MobOrigin;
import art.arcane.adapt.content.skill.SkillHunter;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Big Game Hunter's bonus comes from the mob's own loot table, never from what it carried, and never from farmed big game. */
class HunterBigGameHunterLootTest {
  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Hunter", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillHunter.class));
    AdaptPlayer profile = adapt.join(player);
    HunterBigGameHunter hunter = (HunterBigGameHunter) adapt.core().getAdaptServer().getSkillRegistry()
        .getAdaptation(Id.adapt("hunter/big_game"));
    hunter.getConfig().extraDropChanceBase = 1D;
    hunter.getConfig().maxExtraDropChance = 1D;
    profile.getData().getSkillLine("hunter").setAdaptation(hunter, 1);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theBonusRollsTheMobsOwnLootAndNeverCopiesWhatItCarried() {
    FakeLiving ravager = bigGame("ravager");
    platform.host().animals().setLoot(ravager, List.of(FakeItem.of("saddle")));

    EntityDeathEvent death = kill(ravager, FakeItem.of("saddle"), FakeItem.of("diamond_sword"), new FakeItem(Id.minecraft("diamond"), 5));

    assertThat(count(death, "diamond_sword")).isEqualTo(1);
    assertThat(count(death, "diamond")).isEqualTo(5);
    assertThat(count(death, "saddle")).isEqualTo(2);
  }

  @Test
  void farmedOrPlayerBuiltBigGameGetsNoBonus() {
    FakeLiving spawned = bigGame("ravager");
    MobOrigin.record(spawned, CreatureSpawnEvent.Reason.SPAWNER);
    FakeLiving wither = bigGame("wither");
    wither.trait(EntityTrait.BOSS);
    adapt.post(new CreatureSpawnEvent(wither, wither.location(), CreatureSpawnEvent.Reason.BUILD_WITHER));
    platform.host().animals().setLoot(spawned, List.of(FakeItem.of("saddle")));
    platform.host().animals().setLoot(wither, List.of(FakeItem.of("nether_star")));

    assertThat(count(kill(spawned, FakeItem.of("saddle")), "saddle")).isEqualTo(1);
    assertThat(count(kill(wither, FakeItem.of("nether_star")), "nether_star")).isEqualTo(1);
  }

  private FakeLiving bigGame(String type) {
    FakeLiving mob = new FakeLiving(Id.minecraft(type), new Loc(platform.world(), 3D, 61D, 0.5D));
    mob.trait(EntityTrait.MONSTER);
    mob.trait(EntityTrait.ENEMY);
    platform.world().add(mob);
    platform.host().track(mob);
    return mob;
  }

  private EntityDeathEvent kill(FakeLiving victim, ItemRef... drops) {
    return adapt.post(new EntityDeathEvent(victim, player, player, player, DamageCause.ENTITY_ATTACK,
        new ArrayList<>(List.of(drops)), 0));
  }

  private static int count(EntityDeathEvent death, String type) {
    int total = 0;
    for (ItemRef item : death.drops()) {
      if (item != null && !item.isEmpty() && item.type().equals(Id.minecraft(type))) {
        total += item.amount();
      }
    }
    return total;
  }
}
