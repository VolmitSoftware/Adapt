package art.arcane.adapt.api.attribute;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdaptAttributeTrackerTest {
  private AdaptAttributeTracker tracker;
  private LivingEntity entity;
  private UUID entityId;
  private Attribute attribute;

  @BeforeEach
  void setUp() {
    tracker = new AdaptAttributeTracker();
    entity = mock(LivingEntity.class);
    entityId = UUID.randomUUID();
    when(entity.getUniqueId()).thenReturn(entityId);
    attribute = new StubAttribute("attribute-a");
  }

  @Test
  void recordAddsEntryAndHandle() {
    AdaptAttributeKey key = AdaptAttributeKey.of("wind-up", null);

    tracker.record(entity, attribute, key);

    assertThat(tracker.entries(entityId)).containsExactly(new AdaptAttributeTracker.Entry(attribute, key));
    assertThat(tracker.handle(entityId)).isSameAs(entity);
    assertThat(tracker.trackedEntities()).containsExactly(entity);
  }

  @Test
  void untrackRemovesEntryAndReleasesHandleWhenEmpty() {
    AdaptAttributeKey key = AdaptAttributeKey.of("wind-up", null);
    tracker.record(entity, attribute, key);

    tracker.untrack(entityId, attribute, key);

    assertThat(tracker.entries(entityId)).isEmpty();
    assertThat(tracker.handle(entityId)).isNull();
    assertThat(tracker.trackedEntities()).isEmpty();
  }

  @Test
  void entriesFilterByAdaptation() {
    AdaptAttributeKey windUp = AdaptAttributeKey.of("wind-up", null);
    AdaptAttributeKey superJump = AdaptAttributeKey.of("super-jump", null);
    Attribute other = new StubAttribute("attribute-b");
    tracker.record(entity, attribute, windUp);
    tracker.record(entity, other, superJump);

    List<AdaptAttributeTracker.Entry> matched = tracker.entries(entityId, "wind-up");

    assertThat(matched).containsExactly(new AdaptAttributeTracker.Entry(attribute, windUp));
  }

  @Test
  void entriesForAdaptationSpanEntities() {
    LivingEntity second = mock(LivingEntity.class);
    UUID secondId = UUID.randomUUID();
    when(second.getUniqueId()).thenReturn(secondId);
    AdaptAttributeKey key = AdaptAttributeKey.of("wind-up", null);
    tracker.record(entity, attribute, key);
    tracker.record(second, attribute, key);

    assertThat(tracker.entriesForAdaptation("wind-up"))
        .containsOnlyKeys(entityId, secondId);
  }

  @Test
  void pruneClearsEntriesHandleAndGenerations() {
    AdaptAttributeKey key = AdaptAttributeKey.of("wind-up", null);
    tracker.record(entity, attribute, key);
    long generation = tracker.beginTimed(entityId, attribute, key.key());

    tracker.prune(entityId);

    assertThat(tracker.entries(entityId)).isEmpty();
    assertThat(tracker.handle(entityId)).isNull();
    assertThat(tracker.shouldExpire(entityId, attribute, key.key(), generation)).isFalse();
  }

  @Test
  void newTimedGenerationSupersedesPrevious() {
    AdaptAttributeKey key = AdaptAttributeKey.of("wind-up", null);
    long first = tracker.beginTimed(entityId, attribute, key.key());
    long second = tracker.beginTimed(entityId, attribute, key.key());

    assertThat(tracker.shouldExpire(entityId, attribute, key.key(), first)).isFalse();
    assertThat(tracker.shouldExpire(entityId, attribute, key.key(), second)).isTrue();
    assertThat(tracker.shouldExpire(entityId, attribute, key.key(), second)).isFalse();
  }

  @Test
  void cancelTimedPreventsExpiry() {
    AdaptAttributeKey key = AdaptAttributeKey.of("wind-up", null);
    long generation = tracker.beginTimed(entityId, attribute, key.key());

    tracker.cancelTimed(entityId, attribute, key.key());

    assertThat(tracker.shouldExpire(entityId, attribute, key.key(), generation)).isFalse();
  }

  @Test
  void generationsNeverRepeatAcrossPrune() {
    AdaptAttributeKey key = AdaptAttributeKey.of("wind-up", null);
    long stale = tracker.beginTimed(entityId, attribute, key.key());
    tracker.prune(entityId);
    long fresh = tracker.beginTimed(entityId, attribute, key.key());

    assertThat(fresh).isNotEqualTo(stale);
    assertThat(tracker.shouldExpire(entityId, attribute, key.key(), stale)).isFalse();
    assertThat(tracker.shouldExpire(entityId, attribute, key.key(), fresh)).isTrue();
  }

  @Test
  void generationsAreIndependentPerAttribute() {
    AdaptAttributeKey key = AdaptAttributeKey.of("steady-hands", null);
    Attribute other = new StubAttribute("attribute-b");
    long first = tracker.beginTimed(entityId, attribute, key.key());
    long second = tracker.beginTimed(entityId, other, key.key());

    assertThat(tracker.shouldExpire(entityId, attribute, key.key(), first)).isTrue();
    assertThat(tracker.shouldExpire(entityId, other, key.key(), second)).isTrue();
  }
}
