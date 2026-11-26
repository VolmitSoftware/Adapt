package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

class PlayerDataPowerTest extends AdaptTestBase {
  @Test
  @DisplayName("each held adaptation uses one power whatever its level")
  void heldAdaptationsUseOnePowerEach() {
    RegisteredSkills.register(core, "axes", "axe-chop", "axe-cleave");
    PlayerData data = new PlayerData();
    PlayerSkillLine line = new PlayerSkillLine();
    line.setLine("axes");
    data.getSkillLines().put("axes", line);
    line.getAdaptations().put("axe-chop", held("axe-chop", 5));
    line.getAdaptations().put("axe-cleave", held("axe-cleave", 1));

    assertThat(data.getUsedPower()).isEqualTo(2);
  }

  @Test
  @DisplayName("maximum power grows with the square root of the master level")
  void maximumPowerFollowsTheSquareRootCurve() {
    assertThat(PlayerData.maxPowerFor(1D, 1.6D)).isEqualTo(1);
    assertThat(PlayerData.maxPowerFor(5.99D, 1.6D)).isEqualTo(3);
    assertThat(PlayerData.maxPowerFor(100D, 1.6D)).isEqualTo(16);
    assertThat(PlayerData.maxPowerFor(0D, 1.6D)).isZero();
    assertThat(PlayerData.maxPowerFor(Double.NaN, 1.6D)).isZero();
  }

  @Test
  @DisplayName("skill levels 6, 6, 3, 2, 1 and 1 give three power")
  void earlySkillSpreadGivesThreePower() {
    PlayerData data = new PlayerData();
    data.setMasterXp(10787D);

    assertThat(data.getLevel()).isEqualTo(5);
    assertThat(data.getMaxPower()).isEqualTo(3);
  }

  @Test
  @DisplayName("learning an adaptation costs one power and further levels cost none")
  void onlyTheFirstLevelCostsPower() {
    Adaptation<?> adaptation = mock(Adaptation.class, CALLS_REAL_METHODS);

    assertThat(adaptation.getPowerCostFor(1, 0)).isEqualTo(1);
    assertThat(adaptation.getPowerCostFor(5, 0)).isEqualTo(1);
    assertThat(adaptation.getPowerCostFor(3, 2)).isZero();
    assertThat(adaptation.getPowerCostFor(1, 3)).isZero();
    assertThat(adaptation.getPowerCostFor(0, 3)).isEqualTo(-1);
  }

  private static PlayerAdaptation held(String id, int level) {
    PlayerAdaptation adaptation = new PlayerAdaptation();
    adaptation.setId(id);
    adaptation.setLevel(level);
    return adaptation;
  }
}
