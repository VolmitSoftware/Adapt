package art.arcane.adapt.content.skill;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class SkillCraftingTest {
  @Test
  void firstSmeltForAFurnaceGrantsXp() {
    Map<String, Long> marks = new ConcurrentHashMap<>();
    assertThat(SkillCrafting.markFurnaceXp(marks, "w:0:64:0", 1000L, 10000L)).isTrue();
  }

  @Test
  void repeatSmeltWithinCooldownIsRejected() {
    Map<String, Long> marks = new ConcurrentHashMap<>();
    assertThat(SkillCrafting.markFurnaceXp(marks, "w:0:64:0", 1000L, 10000L)).isTrue();
    assertThat(SkillCrafting.markFurnaceXp(marks, "w:0:64:0", 5000L, 10000L)).isFalse();
  }

  @Test
  void smeltAfterCooldownElapsesGrantsAgain() {
    Map<String, Long> marks = new ConcurrentHashMap<>();
    assertThat(SkillCrafting.markFurnaceXp(marks, "w:0:64:0", 1000L, 10000L)).isTrue();
    assertThat(SkillCrafting.markFurnaceXp(marks, "w:0:64:0", 11000L, 10000L)).isTrue();
  }

  @Test
  void distinctFurnacesTrackIndependentCooldowns() {
    Map<String, Long> marks = new ConcurrentHashMap<>();
    assertThat(SkillCrafting.markFurnaceXp(marks, "w:0:64:0", 1000L, 10000L)).isTrue();
    assertThat(SkillCrafting.markFurnaceXp(marks, "w:1:64:0", 1000L, 10000L)).isTrue();
  }

  @Test
  void nonPositiveCooldownAlwaysGrants() {
    Map<String, Long> marks = new ConcurrentHashMap<>();
    assertThat(SkillCrafting.markFurnaceXp(marks, "w:0:64:0", 1000L, 0L)).isTrue();
    assertThat(SkillCrafting.markFurnaceXp(marks, "w:0:64:0", 1000L, 0L)).isTrue();
    assertThat(marks).isEmpty();
  }

  @Test
  void expiredEntriesArePrunedWhenMapGrowsLarge() {
    Map<String, Long> marks = new ConcurrentHashMap<>();
    for (int i = 0; i < 2100; i++) {
      marks.put("w:" + i + ":64:0", 0L);
    }
    assertThat(SkillCrafting.markFurnaceXp(marks, "w:fresh:64:0", 50000L, 10000L)).isTrue();
    assertThat(marks).hasSize(1);
    assertThat(marks).containsKey("w:fresh:64:0");
  }
}
