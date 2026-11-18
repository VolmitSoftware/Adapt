package art.arcane.adapt.api.fx;

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FxTimelineTest {
  @Test
  void followDefersAllEntityReads() {
    Skill<?> skill = skill();
    EntityRef entity = mock(EntityRef.class);

    FxTimeline timeline = FxTimeline.follow(skill, entity);

    assertThat(timeline).isNotNull();
    verifyNoInteractions(entity);
  }

  @Test
  void followReadsAndRendersOnlyInsideEntityOwnerDispatch() {
    Skill<?> skill = skill();
    EntityRef entity = mock(EntityRef.class);
    WorldRef world = mock(WorldRef.class);
    when(entity.isValid()).thenReturn(true);
    when(entity.isDead()).thenReturn(false);
    when(entity.location()).thenReturn(new Loc(world, 4.0D, 70.0D, -2.0D));
    clearInvocations(entity);
    AtomicInteger frames = new AtomicInteger();
    AtomicReference<Runnable> ownerTask = new AtomicReference<>();
    FxTimeline timeline = FxTimeline.follow(skill, entity)
        .duration(1)
        .frame((fx, tick, progress) -> frames.incrementAndGet());

    try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class)) {
      scheduling.when(Tasks::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> Tasks.runEntity(same(entity), any(Runnable.class), any(Runnable.class))).thenAnswer(invocation -> {
        ownerTask.set(invocation.getArgument(1));
        return true;
      });

      assertThat(timeline.advance()).isTrue();
      assertThat(frames.get()).isZero();
      verify(entity, never()).isValid();
      verify(entity, never()).location();

      ownerTask.get().run();

      assertThat(frames.get()).isEqualTo(1);
      verify(entity).isValid();
      verify(entity).location();
      assertThat(timeline.advance()).isFalse();
    }
  }

  @Test
  void fixedTimelineUsesRegionOwnerDispatchOnFolia() {
    Skill<?> skill = skill();
    WorldRef world = mock(WorldRef.class);
    Loc location = new Loc(world, 8.0D, 64.0D, 8.0D);
    AtomicInteger frames = new AtomicInteger();
    AtomicReference<Runnable> ownerTask = new AtomicReference<>();
    FxTimeline timeline = FxTimeline.at(skill, location)
        .duration(1)
        .frame((fx, tick, progress) -> frames.incrementAndGet());

    try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class)) {
      scheduling.when(Tasks::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> Tasks.runAt(any(Loc.class), any(Runnable.class))).thenAnswer(invocation -> {
        ownerTask.set(invocation.getArgument(1));
        return true;
      });

      assertThat(timeline.advance()).isTrue();
      assertThat(frames.get()).isZero();

      ownerTask.get().run();

      assertThat(frames.get()).isEqualTo(1);
      assertThat(timeline.advance()).isFalse();
    }
  }

  @Test
  void ownerDispatchedFrameFailureCancelsWithoutRescheduling() {
    Skill<?> skill = skill();
    EntityRef entity = mock(EntityRef.class);
    WorldRef world = mock(WorldRef.class);
    when(entity.isValid()).thenReturn(true);
    when(entity.isDead()).thenReturn(false);
    when(entity.location()).thenReturn(new Loc(world, 0.0D, 64.0D, 0.0D));
    AtomicReference<Runnable> ownerTask = new AtomicReference<>();
    FxTimeline timeline = FxTimeline.follow(skill, entity)
        .duration(4)
        .frame((fx, tick, progress) -> {
          throw new IllegalStateException("frame failed");
        });

    try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class)) {
      scheduling.when(Tasks::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> Tasks.runEntity(same(entity), any(Runnable.class), any(Runnable.class))).thenAnswer(invocation -> {
        ownerTask.set(invocation.getArgument(1));
        return true;
      });

      assertThat(timeline.advance()).isTrue();
      ownerTask.get().run();

      assertThat(timeline.advance()).isFalse();
      scheduling.verify(() -> Tasks.runEntity(same(entity), any(Runnable.class), any(Runnable.class)), times(1));
    }
  }

  @Test
  void anEntityAnchorThatLeftTheWorldEndsTheTimelineWithoutAnError() {
    Skill<?> skill = skill();
    EntityRef entity = mock(EntityRef.class);
    FxTimeline timeline = FxTimeline.follow(skill, entity).duration(4);

    try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class);
         MockedStatic<AdaptLog> log = mockStatic(AdaptLog.class)) {
      scheduling.when(Tasks::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> Tasks.runEntity(same(entity), any(Runnable.class), any(Runnable.class))).thenReturn(false);

      assertThat(timeline.advance()).isFalse();
      assertThat(timeline.advance()).isFalse();
      log.verifyNoInteractions();
    }
  }

  @Test
  void anEntityAnchorRetiredBeforeItsFrameRanEndsTheTimelineWithoutAnError() {
    Skill<?> skill = skill();
    EntityRef entity = mock(EntityRef.class);
    AtomicReference<Runnable> retired = new AtomicReference<>();
    FxTimeline timeline = FxTimeline.follow(skill, entity).duration(4);

    try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class);
         MockedStatic<AdaptLog> log = mockStatic(AdaptLog.class)) {
      scheduling.when(Tasks::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> Tasks.runEntity(same(entity), any(Runnable.class), any(Runnable.class))).thenAnswer(invocation -> {
        retired.set(invocation.getArgument(2));
        return true;
      });

      assertThat(timeline.advance()).isTrue();
      retired.get().run();

      assertThat(timeline.advance()).isFalse();
      log.verifyNoInteractions();
    }
  }

  private Skill<?> skill() {
    Skill<?> skill = mock(Skill.class);
    when(skill.getName()).thenReturn("test-skill");
    when(skill.areParticlesEnabled()).thenReturn(false);
    when(skill.areSoundsEnabled()).thenReturn(false);
    return skill;
  }
}
