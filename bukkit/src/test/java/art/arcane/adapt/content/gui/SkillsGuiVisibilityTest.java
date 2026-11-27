package art.arcane.adapt.engine.gui;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillsGuiVisibilityTest extends AdaptTestBase {
  @Test
  void untouchedSkillsStayHiddenWhileShowAllIsDisabled() {
    PlayerSkillLine line = new PlayerSkillLine();
    line.setLine("adapt:mining");

    assertThat(SkillsGui.isSkillEntryVisible(true, false, false, line, 0)).isFalse();
  }

  @Test
  void showAllRevealsUntouchedSkills() {
    PlayerSkillLine line = new PlayerSkillLine();
    line.setLine("adapt:mining");

    assertThat(SkillsGui.isSkillEntryVisible(true, false, true, line, 0)).isTrue();
  }

  @Test
  void showAllNeverBypassesTheUsePermission() {
    PlayerSkillLine line = new PlayerSkillLine();
    line.setLine("adapt:mining");

    assertThat(SkillsGui.isSkillEntryVisible(false, false, true, line, 0)).isFalse();
    assertThat(SkillsGui.isSkillEntryVisible(false, true, true, line, 0)).isFalse();
  }

  @Test
  void earnedProgressStaysVisibleWithoutShowAll() {
    PlayerSkillLine xpLine = new PlayerSkillLine();
    xpLine.setLine("adapt:mining");
    xpLine.setXp(25D);

    PlayerSkillLine knowledgeLine = new PlayerSkillLine();
    knowledgeLine.setLine("adapt:mining");
    knowledgeLine.setKnowledge(3L);

    PlayerSkillLine adaptedLine = new PlayerSkillLine();
    adaptedLine.setLine("adapt:mining");

    assertThat(SkillsGui.isSkillEntryVisible(true, false, false, xpLine, 0)).isTrue();
    assertThat(SkillsGui.isSkillEntryVisible(true, false, false, knowledgeLine, 0)).isTrue();
    assertThat(SkillsGui.isSkillEntryVisible(true, false, false, adaptedLine, 2)).isTrue();
  }

  @Test
  void showAllBuildsATransientLineWithoutPersistingItToPlayerData() {
    PlayerData data = new PlayerData();

    PlayerSkillLine resolved = SkillsGui.resolveSkillLine(data, "mining", false, true);

    assertThat(resolved).isNotNull();
    assertThat(resolved.getLine()).isEqualTo("mining");
    assertThat(resolved.getXp()).isZero();
    assertThat(resolved.getKnowledge()).isZero();
    assertThat(data.getSkillLines()).isEmpty();
  }

  @Test
  void untrackedSkillsResolveToNothingWhileShowAllIsDisabled() {
    PlayerData data = new PlayerData();

    assertThat(SkillsGui.resolveSkillLine(data, "mining", false, false)).isNull();
    assertThat(data.getSkillLines()).isEmpty();
  }

  @Test
  void storedLinesAlwaysWinOverTransientOnes() {
    PlayerData data = new PlayerData();
    PlayerSkillLine stored = new PlayerSkillLine();
    stored.setLine("adapt:mining");
    stored.setKnowledge(7L);
    data.getSkillLines().put("adapt:mining", stored);

    assertThat(SkillsGui.resolveSkillLine(data, "mining", false, true)).isSameAs(stored);
    assertThat(SkillsGui.resolveSkillLine(data, "mining", false, false)).isSameAs(stored);
    assertThat(data.getSkillLines()).hasSize(1);
  }
}
