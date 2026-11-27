package art.arcane.adapt.client.view;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerViewDiffTest {
  private static final PlayerHeader HEADER = new PlayerHeader(10, 0.5D, 3, 10, 0L, true);
  private static final PreferenceKey SKILL_ENABLED = new PreferenceKey(PreferenceOwner.SKILL, "rift", "enabled");
  private static final PreferenceKey BLINK_LANDING = new PreferenceKey(PreferenceOwner.ADAPTATION, "adapt:rift/blink", "landing");
  private static final PreferenceKey GATE_ENABLED = new PreferenceKey(PreferenceOwner.ADAPTATION, "adapt:rift/gate", "enabled");

  private static PlayerView view(PlayerHeader header, double riftXp, Map<String, Integer> levels,
                                 List<PreferenceValue> preferences) {
    return new PlayerView(header, List.of(
        new SkillLineView("rift", riftXp, 2, 0.1D, 40D, 4L),
        new SkillLineView("agility", 10D, 1, 0.2D, 30D, 0L)
    ), levels, preferences, PlayerAccess.NONE);
  }

  @Test
  void identicalViewsProduceAnEmptyDelta() {
    PlayerView view = view(HEADER, 50D, Map.of("adapt:rift/blink", 1),
        List.of(new PreferenceValue(SKILL_ENABLED, "ON")));

    PlayerDelta delta = PlayerViewDiff.diff(view, view);

    assertThat(delta.isEmpty()).isTrue();
  }

  @Test
  void onlyChangedPartsAreReported() {
    PlayerView before = view(HEADER, 50D, Map.of("adapt:rift/blink", 1, "adapt:rift/gate", 2), List.of(
        new PreferenceValue(SKILL_ENABLED, "ON"),
        new PreferenceValue(BLINK_LANDING, "SERVER"),
        new PreferenceValue(GATE_ENABLED, "ON")
    ));
    PlayerHeader header = new PlayerHeader(10, 0.5D, 2, 10, 0L, true);
    PlayerView after = view(header, 75D, Map.of("adapt:rift/blink", 2), List.of(
        new PreferenceValue(SKILL_ENABLED, "ON"),
        new PreferenceValue(BLINK_LANDING, "NEAR")
    ));

    PlayerDelta delta = PlayerViewDiff.diff(before, after);

    assertThat(delta.header()).isEqualTo(header);
    assertThat(delta.skills()).extracting(SkillLineView::skillId).containsExactly("rift");
    assertThat(delta.adaptationLevels()).containsExactlyInAnyOrderEntriesOf(Map.of("adapt:rift/blink", 2, "adapt:rift/gate", 0));
    assertThat(delta.preferences()).containsExactly(new PreferenceValue(BLINK_LANDING, "NEAR"));
    assertThat(delta.clearedPreferences()).containsExactly(GATE_ENABLED);
  }

  @Test
  void anUnchangedHeaderIsLeftOut() {
    PlayerView before = view(HEADER, 50D, Map.of(), List.of());
    PlayerView after = view(HEADER, 51D, Map.of(), List.of());

    PlayerDelta delta = PlayerViewDiff.diff(before, after);

    assertThat(delta.header()).isNull();
    assertThat(delta.isEmpty()).isFalse();
  }
}
