package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegionGrantedPlayerDataTest extends AdaptTestBase {

  @Test
  @DisplayName("a region granted adaptation contributes no used power")
  void regionGrantsAreFreeOfPowerCost() {
    PlayerData data = new PlayerData();
    PlayerSkillLine line = line(data);
    line.getAdaptations().put("axe-shield-splitter", adaptation("axe-shield-splitter", 2, false));
    line.getAdaptations().put("axe-heavy-hands", adaptation("axe-heavy-hands", 1, true));

    assertThat(data.getUsedPower()).isEqualTo(2);
  }

  @Test
  @DisplayName("the power budget pruner never strips a region granted adaptation")
  void prunerSkipsRegionGrants() {
    PlayerData data = new PlayerData();
    PlayerSkillLine line = line(data);
    line.getAdaptations().put("axe-shield-splitter", adaptation("axe-shield-splitter", 1, false));
    line.getAdaptations().put("axe-heavy-hands", adaptation("axe-heavy-hands", 1, true));

    data.pruneAdaptationsForPowerBudget();

    assertThat(line.getAdaptations()).containsOnlyKeys("axe-heavy-hands");
    assertThat(data.getUsedPower()).isZero();
  }

  @Test
  @DisplayName("the region power bonus raises max power and clamps at zero")
  void regionPowerBonusAdjustsMaxPower() {
    PlayerData data = new PlayerData();
    int base = data.getMaxPower();

    data.setRegionPowerBonus(9);
    assertThat(data.getMaxPower()).isEqualTo(base + 9);

    data.setRegionPowerBonus(-4096);
    assertThat(data.getMaxPower()).isZero();
  }

  @Test
  @DisplayName("loading player data strips every region granted adaptation")
  void loadSweepStripsRegionGrants() {
    PlayerData data = new PlayerData();
    PlayerSkillLine line = line(data);
    line.getAdaptations().put("axe-shield-splitter", adaptation("axe-shield-splitter", 2, false));
    line.getAdaptations().put("axe-heavy-hands", adaptation("axe-heavy-hands", 1, true));

    PlayerData restored = PlayerData.fromJson(data.toJson(false));

    assertThat(restored).isNotNull();
    assertThat(restored.getSkillLines().get("axes").getAdaptations()).containsOnlyKeys("axe-shield-splitter");
    assertThat(restored.getRegionGrantedCount()).isZero();
    assertThat(restored.getUsedPower()).isEqualTo(2);
  }

  @Test
  @DisplayName("stripping region grants reports how many were removed and leaves learned data alone")
  void strippingReportsRemovedGrants() {
    PlayerData data = new PlayerData();
    PlayerSkillLine line = line(data);
    line.getAdaptations().put("axe-shield-splitter", adaptation("axe-shield-splitter", 2, false));
    line.getAdaptations().put("axe-heavy-hands", adaptation("axe-heavy-hands", 1, true));
    line.getAdaptations().put("axe-cleave", adaptation("axe-cleave", 1, true));
    data.setRegionGrantedCount(2);

    assertThat(data.stripRegionGrantedAdaptations()).isEqualTo(2);
    assertThat(line.getAdaptations()).containsOnlyKeys("axe-shield-splitter");
    assertThat(data.getRegionGrantedCount()).isZero();
    assertThat(data.stripRegionGrantedAdaptations()).isZero();
  }

  private static PlayerSkillLine line(PlayerData data) {
    PlayerSkillLine line = new PlayerSkillLine();
    line.setLine("axes");
    data.getSkillLines().put("axes", line);
    return line;
  }

  private static PlayerAdaptation adaptation(String id, int level, boolean regionGranted) {
    PlayerAdaptation adaptation = new PlayerAdaptation();
    adaptation.setId(id);
    adaptation.setLevel(level);
    adaptation.setRegionGranted(regionGranted);
    return adaptation;
  }
}
