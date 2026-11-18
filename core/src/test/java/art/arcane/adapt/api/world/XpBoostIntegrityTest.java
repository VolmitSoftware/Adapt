package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.xp.XPMultiplier;
import art.arcane.volmlib.util.math.M;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class XpBoostIntegrityTest extends AdaptTestBase {
  @Test
  void repeatedPickupsRefreshOneBoostAndPreserveOtherSources() {
    PlayerData profile = new PlayerData();
    PlayerSkillLine line = new PlayerSkillLine();
    AdaptPlayer runtime = mock(AdaptPlayer.class);
    when(runtime.getData()).thenReturn(profile);
    line.bindRuntimeOwner(runtime);
    line.boost(0.2D, 100_000L);
    line.boostOwned("quest", 0.1D, 100_000L);
    for (int index = 0; index < 1000; index++) {
      line.boostOwned("vanilla-xp", 0.03D, 10_000L);
    }
    assertThat(line.getMultipliers()).hasSize(3);
    assertThat(line.getMultiplier()).isCloseTo(1.33D, within(0.000001D));
    assertThat(line.getMultipliers().stream().filter(boost -> "vanilla-xp".equals(boost.getSource())))
        .singleElement().extracting(XPMultiplier::getMultiplier).isEqualTo(0.03D);
  }

  @Test
  void refreshingExtendsExpiryAndSurvivesProfileSerializationWithoutStacking() {
    PlayerData profile = new PlayerData();
    PlayerSkillLine line = new PlayerSkillLine();
    profile.getSkillLines().put("mining", line);
    try (MockedStatic<M> clock = mockStatic(M.class)) {
      clock.when(M::ms).thenReturn(1_000_000L);
      line.boostOwned("vanilla-xp", 0.03D, 10_000L);
      clock.when(M::ms).thenReturn(1_009_000L);
      line.boostOwned("vanilla-xp", 0.03D, 10_000L);
      assertThat(line.getMultipliers()).singleElement().extracting(XPMultiplier::getGoodFor).isEqualTo(1_019_000L);
      PlayerData restored = PlayerData.fromJson(profile.toJson(true));
      PlayerSkillLine restoredLine = restored.getSkillLines().get("mining");
      restoredLine.boostOwned("vanilla-xp", 0.03D, 10_000L);
      assertThat(restoredLine.getMultipliers()).hasSize(1);
      assertThat(restoredLine.getMultipliers().getFirst().getSource()).isEqualTo("vanilla-xp");
    }
  }
}
