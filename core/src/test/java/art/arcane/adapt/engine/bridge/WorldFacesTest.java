package art.arcane.adapt.engine.bridge;

import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.content.AdaptationDamageTargets;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.api.xp.CombatXp;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The public world faces answer what the engine classes behind them answer. */
class WorldFacesTest {
  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Prober", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of());
  }

  @AfterEach
  void stop() {
    adapt.close();
  }

  @Test
  void mobOriginAndDamageTargetsReadTheEngine() {
    FakeLiving cow = platform.world().add(new FakeLiving(Id.minecraft("cow"), player.location()));

    assertThat(MobOrigin.isFarmed(cow)).isFalse();
    assertThat(MobOrigin.paysKillReward(player, cow)).isTrue();
    art.arcane.adapt.engine.xp.MobOrigin.record(cow, CreatureSpawnEvent.Reason.SPAWNER);
    assertThat(MobOrigin.isFarmed(cow)).isTrue();
    assertThat(MobOrigin.paysKillReward(player, cow)).isFalse();

    assertThat(AdaptationDamageTargets.allows(player, true)).isTrue();
    assertThat(AdaptationDamageTargets.allows(cow, true)).isFalse();
    assertThat(AdaptationDamageTargets.allows(cow, false)).isTrue();
    assertThat(CombatXp.isGraveMob(cow)).isFalse();
    assertThat(CombatXp.effectiveDamage(8D, 3D)).isEqualTo(3D);
  }

  @Test
  void provenanceAndProbesReachTheEngine() {
    platform.world().set(2, 61, 2, "chest");
    BlockRef chest = platform.world().blockAt(2, 61, 2);

    XpProvenance.recordPlacement(chest);
    assertThat(XpProvenance.isPlayerPlaced(chest))
        .isEqualTo(art.arcane.adapt.engine.xp.XpProvenance.isPlayerPlaced(chest));
    assertThat(XpProvenance.breakXpMultiplier(chest))
        .isEqualTo(art.arcane.adapt.engine.xp.XpProvenance.breakXpMultiplier(chest));

    assertThat(ProtectionProbes.containerBlocks(chest, false))
        .isEqualTo(art.arcane.adapt.engine.protection.ProtectionProbes.containerBlocks(chest, false))
        .contains(chest);
    assertThat(ProtectionProbes.attemptBlockBreakProbe(player, chest))
        .isEqualTo(art.arcane.adapt.engine.protection.ProtectionProbes.attemptBlockBreakProbe(player, chest));
  }

  @Test
  void theAttributeServiceIsTheEnginesOnEveryCall() {
    Id speed = Id.minecraft("movement_speed");

    assertThat(AdaptAttributeService.get().isEmulated(speed))
        .isEqualTo(art.arcane.adapt.engine.attribute.AdaptAttributeService.get().isEmulated(speed));
    assertThat(AdaptAttributeService.get().clearAllAdapt(player)).isZero();
  }
}
