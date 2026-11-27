package art.arcane.adapt.engine.skill;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.ContentComponent;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.engine.world.AdaptStatTracker;
import art.arcane.volmlib.util.collection.KList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class StatTrackerIndexTest {
  private MockedStatic<ComponentHost> hosts;

  @BeforeEach
  void mockHosts() {
    hosts = mockStatic(ComponentHost.class, CALLS_REAL_METHODS);
  }

  @AfterEach
  void closeHosts() {
    hosts.close();
  }

  @Test
  @DisplayName("trackers are indexed by their exact stat key")
  void indexesExactStatKeys() {
    Skill<?> skill = skillWithTrackers(
        tracker("blocks.broken", 10, "ten"),
        tracker("Blocks.Broken", 20, "twenty")
    );

    StatTrackerIndex index = StatTrackerIndex.build(List.of(skill));

    assertThat(index.bindingsFor("blocks.broken"))
        .extracting(binding -> binding.tracker().getAdvancement())
        .containsExactly("ten");
    assertThat(index.bindingsFor("Blocks.Broken"))
        .extracting(binding -> binding.tracker().getAdvancement())
        .containsExactly("twenty");
    assertThat(index.bindingsFor("blocks"))
        .isEmpty();
  }

  @Test
  @DisplayName("skill and adaptation thresholds stay grouped and ordered")
  void groupsBindingsBySkillAndOrdersThresholds() {
    Skill<?> first = skillWithTrackers(
        tracker("damage", 1_000, "first-high"),
        tracker("damage", 100, "first-low")
    );
    SimpleAdaptation<?> adaptation = adaptationWithTrackers(
        tracker("damage", 500, "first-middle")
    );
    KList<Adaptation<?>> firstAdaptations = new KList<>();
    firstAdaptations.add(adaptation);
    when(first.getAdaptations()).thenReturn(firstAdaptations);

    Skill<?> second = skillWithTrackers(tracker("damage", 50, "second"));

    StatTrackerIndex index = StatTrackerIndex.build(List.of(first, second));

    assertThat(index.bindingsFor("damage"))
        .extracting(binding -> binding.tracker().getAdvancement())
        .containsExactly("first-low", "first-middle", "first-high", "second");
    assertThat(index.bindingCount()).isEqualTo(4);
    assertThat(index.trackedStatCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("invalid trackers and absent catalogs produce no bindings")
  void excludesInvalidTrackers() {
    Skill<?> skill = skillWithTrackers(
        null,
        tracker("", 1, "blank-stat"),
        tracker("valid", 1, "")
    );

    StatTrackerIndex index = StatTrackerIndex.build(List.of(skill));

    assertThat(index.bindingCount()).isZero();
    assertThat(index.trackedStats()).isEmpty();
    assertThat(StatTrackerIndex.build(null).bindingsFor("valid")).isEmpty();
  }

  private Skill<?> skillWithTrackers(AdaptStatTracker... trackers) {
    Skill<?> skill = mock(Skill.class);
    hostWithTrackers(skill, trackers);
    when(skill.getAdaptations()).thenReturn(new KList<>());
    return skill;
  }

  private SimpleAdaptation<?> adaptationWithTrackers(AdaptStatTracker... trackers) {
    SimpleAdaptation<?> adaptation = mock(SimpleAdaptation.class);
    hostWithTrackers(adaptation, trackers);
    return adaptation;
  }

  private void hostWithTrackers(ContentComponent component, AdaptStatTracker... trackers) {
    ComponentHost host = mock(ComponentHost.class);
    KList<AdaptStatTracker> trackerList = new KList<>();
    trackerList.add(trackers);
    when(host.getStatTrackers()).thenReturn(trackerList);
    hosts.when(() -> ComponentHost.find(component)).thenReturn(host);
  }

  private static AdaptStatTracker tracker(String stat, double goal, String advancement) {
    return AdaptStatTracker.builder()
        .stat(stat)
        .goal(goal)
        .reward(goal)
        .advancement(advancement)
        .build();
  }
}
