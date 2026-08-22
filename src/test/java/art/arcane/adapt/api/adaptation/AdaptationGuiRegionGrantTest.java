package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerSkillLine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptationGuiRegionGrantTest {
  private static final String ADAPTATION = "test-adaptation";

  @Test
  void regionGrantedEntriesArePricedFromLevelZero() {
    PlayerSkillLine line = line(1, true);

    assertThat(line.getAdaptation(ADAPTATION).isRegionGranted()).isTrue();
    assertThat(line.getAdaptationLevel(ADAPTATION)).isEqualTo(1);
    assertThat(AdaptationGuiSupport.paidLevel(line, ADAPTATION)).isZero();
  }

  @Test
  void ownedEntriesArePricedFromTheirCurrentLevel() {
    PlayerSkillLine line = line(3, false);

    assertThat(line.getAdaptation(ADAPTATION).isRegionGranted()).isFalse();
    assertThat(AdaptationGuiSupport.paidLevel(line, ADAPTATION)).isEqualTo(3);
  }

  @Test
  void missingEntriesAndMissingLinesArePricedFromLevelZero() {
    assertThat(AdaptationGuiSupport.paidLevel(new PlayerSkillLine(), ADAPTATION)).isZero();
    assertThat(AdaptationGuiSupport.paidLevel(null, ADAPTATION)).isZero();
  }

  @Test
  void theLevelOneTileStaysBuyableWhileTheGrantIsRegionOwned() {
    PlayerSkillLine granted = line(1, true);
    PlayerSkillLine owned = line(1, false);

    assertThat(AdaptationGuiSupport.ownsLevel(AdaptationGuiSupport.paidLevel(granted, ADAPTATION), 1)).isFalse();
    assertThat(AdaptationGuiSupport.ownsLevel(AdaptationGuiSupport.paidLevel(owned, ADAPTATION), 1)).isTrue();
  }

  @Test
  void buyingPastARegionGrantNeverRoutesToUnlearn() {
    PlayerSkillLine granted = line(1, true);

    for (int tileLevel = 1; tileLevel <= 5; tileLevel++) {
      assertThat(AdaptationGuiSupport.ownsLevel(AdaptationGuiSupport.paidLevel(granted, ADAPTATION), tileLevel)).isFalse();
    }
  }

  @Test
  void learningARegionGrantedEntryRestoresNormalOwnedRouting() {
    PlayerSkillLine line = line(1, true);
    line.getAdaptation(ADAPTATION).setRegionGranted(false);

    assertThat(AdaptationGuiSupport.paidLevel(line, ADAPTATION)).isEqualTo(1);
    assertThat(AdaptationGuiSupport.ownsLevel(AdaptationGuiSupport.paidLevel(line, ADAPTATION), 1)).isTrue();
  }

  private PlayerSkillLine line(int level, boolean regionGranted) {
    PlayerSkillLine line = new PlayerSkillLine();
    line.setLine("test-skill");
    PlayerAdaptation adaptation = new PlayerAdaptation();
    adaptation.setId(ADAPTATION);
    adaptation.setLevel(level);
    adaptation.setRegionGranted(regionGranted);
    line.getAdaptations().put(ADAPTATION, adaptation);
    return line;
  }
}
