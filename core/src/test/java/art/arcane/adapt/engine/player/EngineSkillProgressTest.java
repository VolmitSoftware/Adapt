package art.arcane.adapt.engine.player;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.player.SkillProgress;
import art.arcane.adapt.api.xp.XpAward;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.platform.value.Id;
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

class EngineSkillProgressTest {
  private static final Id SWORDS = Id.adapt("swords");

  @TempDir
  Path data;

  private TestAdapt adapt;
  private EnginePlayerProfile profile;

  @BeforeEach
  void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    FakePlayer player = platform.player("Earner", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class));
    adapt.join(player);
    profile = PlayerApi.directory().require(player);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void levelReflectsAnAwardAsSoonAsItReturns() {
    assertThat(AdaptConfig.get().getXpIntegrity().isPooledPayoutEnabled()).isTrue();
    SkillProgress swords = profile.skill(SWORDS);

    swords.award(XpAward.of(XP.getXpForLevel(14) + 1D));

    assertThat(swords.level()).isEqualTo(14);
    assertThat(swords.xp()).isEqualTo(XP.getXpForLevel(14) + 1D);
  }
}
