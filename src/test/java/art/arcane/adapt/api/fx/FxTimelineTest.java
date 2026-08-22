package art.arcane.adapt.api.fx;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
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

class FxTimelineTest extends AdaptTestBase {
  @Test
  void followDefersAllEntityReads() {
    Skill<?> skill = skill();
    Entity entity = mock(Entity.class);

    FxTimeline timeline = FxTimeline.follow(skill, entity);

    assertThat(timeline).isNotNull();
    verifyNoInteractions(entity);
  }

  @Test
  void followReadsAndRendersOnlyInsideEntityOwnerDispatch() {
    Skill<?> skill = skill();
    Entity entity = mock(Entity.class);
    World world = mock(World.class);
    when(entity.isValid()).thenReturn(true);
    when(entity.isDead()).thenReturn(false);
    when(entity.getLocation()).thenReturn(new Location(world, 4.0D, 70.0D, -2.0D));
    clearInvocations(entity);
    AtomicInteger frames = new AtomicInteger();
    AtomicReference<Runnable> ownerTask = new AtomicReference<>();
    FxTimeline timeline = FxTimeline.follow(skill, entity)
        .duration(1)
        .frame((fx, tick, progress) -> frames.incrementAndGet());

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.runEntity(same(entity), any(Runnable.class))).thenAnswer(invocation -> {
        ownerTask.set(invocation.getArgument(1));
        return true;
      });

      assertThat(timeline.advance()).isTrue();
      assertThat(frames.get()).isZero();
      verify(entity, never()).isValid();
      verify(entity, never()).getLocation();

      ownerTask.get().run();

      assertThat(frames.get()).isEqualTo(1);
      verify(entity).isValid();
      verify(entity).getLocation();
      assertThat(timeline.advance()).isFalse();
    }
  }

  @Test
  void fixedTimelineUsesRegionOwnerDispatchOnFolia() {
    Skill<?> skill = skill();
    World world = mock(World.class);
    Location location = new Location(world, 8.0D, 64.0D, 8.0D);
    AtomicInteger frames = new AtomicInteger();
    AtomicReference<Runnable> ownerTask = new AtomicReference<>();
    FxTimeline timeline = FxTimeline.at(skill, location)
        .duration(1)
        .frame((fx, tick, progress) -> frames.incrementAndGet());

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.runAt(any(Location.class), any(Runnable.class))).thenAnswer(invocation -> {
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
    Entity entity = mock(Entity.class);
    World world = mock(World.class);
    when(entity.isValid()).thenReturn(true);
    when(entity.isDead()).thenReturn(false);
    when(entity.getLocation()).thenReturn(new Location(world, 0.0D, 64.0D, 0.0D));
    AtomicReference<Runnable> ownerTask = new AtomicReference<>();
    FxTimeline timeline = FxTimeline.follow(skill, entity)
        .duration(4)
        .frame((fx, tick, progress) -> {
          throw new IllegalStateException("frame failed");
        });

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.runEntity(same(entity), any(Runnable.class))).thenAnswer(invocation -> {
        ownerTask.set(invocation.getArgument(1));
        return true;
      });

      assertThat(timeline.advance()).isTrue();
      ownerTask.get().run();

      assertThat(timeline.advance()).isFalse();
      scheduling.verify(() -> J.runEntity(same(entity), any(Runnable.class)), times(1));
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
