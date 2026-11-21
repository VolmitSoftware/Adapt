package art.arcane.adapt.client.view;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerViewPatchTest {
  private static final PlayerHeader HEADER = new PlayerHeader(10, 0.5D, 3, 10, 0L, true);
  private static final List<String> SKILLS = List.of("rift", "agility", "taming", "herbalism", "nether");
  private static final List<String> ADAPTATIONS = List.of("rift-blink", "rift-gate", "agility-wall-jump",
      "taming-health", "herbalism-hungry-shield", "nether-skull-toss");
  private static final List<String> CHOICES = List.of("ON", "OFF", "NEAR", "FAR", "SERVER");

  @Test
  void patchingADiffReproducesTheTargetView() {
    PlayerView before = new PlayerView(HEADER, List.of(
        new SkillLineView("rift", 50D, 2, 0.1D, 40D, 4L),
        new SkillLineView("agility", 10D, 1, 0.2D, 30D, 0L)
    ), Map.of("rift-blink", 1, "rift-gate", 2), sorted(List.of(
        new PreferenceValue(key(PreferenceOwner.SKILL, "rift", "enabled"), "ON"),
        new PreferenceValue(key(PreferenceOwner.ADAPTATION, "rift-blink", "landing"), "SERVER"),
        new PreferenceValue(key(PreferenceOwner.ADAPTATION, "rift-gate", "enabled"), "ON")
    )), PlayerAccess.NONE);
    PlayerView after = new PlayerView(new PlayerHeader(11, 0.1D, 2, 12, 5L, false), List.of(
        new SkillLineView("rift", 75D, 3, 0.0D, 60D, 5L),
        new SkillLineView("agility", 10D, 1, 0.2D, 30D, 0L),
        new SkillLineView("taming", 1D, 1, 0.01D, 99D, 0L)
    ), Map.of("rift-blink", 2, "agility-wall-jump", 1), sorted(List.of(
        new PreferenceValue(key(PreferenceOwner.SKILL, "rift", "enabled"), "ON"),
        new PreferenceValue(key(PreferenceOwner.ADAPTATION, "rift-blink", "landing"), "NEAR"),
        new PreferenceValue(key(PreferenceOwner.ADAPTATION, "agility-wall-jump", "enabled"), "OFF")
    )), PlayerAccess.NONE);

    assertThat(PlayerViewPatch.apply(before, PlayerViewDiff.diff(before, after))).isEqualTo(after);
  }

  @Test
  void anEmptyDeltaReturnsTheSameView() {
    PlayerView view = new PlayerView(HEADER, List.of(new SkillLineView("rift", 1D, 1, 0D, 1D, 0L)), Map.of(),
        List.of(), PlayerAccess.NONE);

    assertThat(PlayerViewPatch.apply(view, PlayerViewDiff.diff(view, view))).isSameAs(view);
  }

  @Test
  void unlearnedAdaptationsAndClearedPreferencesAreRemoved() {
    PreferenceKey gate = key(PreferenceOwner.ADAPTATION, "rift-gate", "enabled");
    PlayerView before = new PlayerView(HEADER, List.of(), Map.of("rift-gate", 3),
        List.of(new PreferenceValue(gate, "ON")), PlayerAccess.NONE);
    PlayerDelta delta = new PlayerDelta(null, List.of(), Map.of("rift-gate", 0), List.of(), List.of(gate), null);

    PlayerView patched = PlayerViewPatch.apply(before, delta);

    assertThat(patched.header()).isEqualTo(HEADER);
    assertThat(patched.adaptationLevels()).isEmpty();
    assertThat(patched.preferences()).isEmpty();
  }

  @Test
  void randomViewSequencesRoundTrip() {
    Random random = new Random(7L);
    for (int run = 0; run < 200; run++) {
      PlayerView current = randomView(random, 2);
      for (int step = 0; step < 6; step++) {
        PlayerView next = randomView(random, current.skills().size());
        PlayerView patched = PlayerViewPatch.apply(current, PlayerViewDiff.diff(current, next));
        assertThat(patched).isEqualTo(next);
        current = patched;
      }
    }
  }

  private static PlayerView randomView(Random random, int minimumSkills) {
    PlayerHeader header = random.nextInt(3) == 0 ? HEADER
        : new PlayerHeader(random.nextInt(5), random.nextInt(4) / 4D, random.nextInt(5), 10, random.nextInt(3), true);
    int skillCount = minimumSkills + random.nextInt(SKILLS.size() - minimumSkills + 1);
    List<SkillLineView> skills = new ArrayList<>();
    for (int index = 0; index < skillCount; index++) {
      skills.add(new SkillLineView(SKILLS.get(index), random.nextInt(4) * 10D, random.nextInt(3), 0.5D, 10D,
          random.nextInt(2)));
    }
    Map<String, Integer> levels = new HashMap<>();
    List<PreferenceValue> preferences = new ArrayList<>();
    for (String adaptation : ADAPTATIONS) {
      if (random.nextBoolean()) {
        levels.put(adaptation, 1 + random.nextInt(3));
        if (random.nextBoolean()) {
          preferences.add(new PreferenceValue(key(PreferenceOwner.ADAPTATION, adaptation, "mode"),
              CHOICES.get(random.nextInt(CHOICES.size()))));
        }
      }
    }
    for (SkillLineView skill : skills) {
      if (random.nextBoolean()) {
        preferences.add(new PreferenceValue(key(PreferenceOwner.SKILL, skill.skillId(), "enabled"),
            CHOICES.get(random.nextInt(2))));
      }
    }
    return new PlayerView(header, skills, levels, sorted(preferences), PlayerAccess.NONE);
  }

  private static List<PreferenceValue> sorted(List<PreferenceValue> values) {
    List<PreferenceValue> copy = new ArrayList<>(values);
    copy.sort((PreferenceValue left, PreferenceValue right) -> left.key().compareTo(right.key()));
    return copy;
  }

  private static PreferenceKey key(PreferenceOwner owner, String ownerId, String preferenceId) {
    return new PreferenceKey(owner, ownerId, preferenceId);
  }
}
