package art.arcane.adapt.content.golem.gui;

import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.entity.EntityRef;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

final class ManualScheduler {
  private final List<Task> queue = new ArrayList<>();
  private final List<String> violations = new ArrayList<>();
  private long now;
  private long sequence;
  private Object current;

  void install(AdaptScheduler adaptScheduler) {
    when(adaptScheduler.runEntity(any(EntityRef.class), any(Runnable.class))).thenAnswer(invocation ->
        add(invocation.getArgument(0), invocation.getArgument(1), 0, null));
    when(adaptScheduler.runEntity(any(EntityRef.class), any(Runnable.class), any(Runnable.class))).thenAnswer(invocation ->
        add(invocation.getArgument(0), invocation.getArgument(1), 0, invocation.getArgument(2)));
    when(adaptScheduler.runEntity(any(EntityRef.class), any(Runnable.class), anyLong(), any(Runnable.class))).thenAnswer(invocation ->
        add(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3)));
  }

  void installAt(AdaptScheduler adaptScheduler, Object owner) {
    when(adaptScheduler.runAt(any(Loc.class), any(Runnable.class))).thenAnswer(invocation ->
        add(owner, invocation.getArgument(1), 0, null));
    when(adaptScheduler.runAt(any(Loc.class), any(Runnable.class), anyLong())).thenAnswer(invocation ->
        add(owner, invocation.getArgument(1), invocation.getArgument(2), null));
  }

  LiveRefresh.Scheduler scheduler(Object owner) {
    return (task, delay, retired) -> add(owner, task, delay, retired);
  }

  boolean add(Object owner, Runnable task, long delay, Runnable retired) {
    queue.add(new Task(owner, now + delay, sequence++, task, retired));
    return true;
  }

  void advance(int ticks) {
    long target = now + ticks;
    Task next = earliest(target);
    while (next != null) {
      queue.remove(next);
      now = Math.max(now, next.due());
      Object previous = current;
      current = next.owner();
      try {
        next.task().run();
      } finally {
        current = previous;
      }
      next = earliest(target);
    }
    now = target;
  }

  void retire(Object owner) {
    List<Task> owned = new ArrayList<>();
    for (Task task : queue) {
      if (task.owner() == owner) {
        owned.add(task);
      }
    }
    queue.removeAll(owned);
    for (Task task : owned) {
      if (task.retired() != null) {
        task.retired().run();
      }
    }
  }

  int pending() {
    return queue.size();
  }

  int pending(Object owner) {
    int count = 0;
    for (Task task : queue) {
      if (task.owner() == owner) {
        count++;
      }
    }
    return count;
  }

  void expect(Object owner, String access) {
    if (current != owner) {
      violations.add(access + " ran under " + current + ", expected " + owner);
    }
  }

  List<String> violations() {
    return violations;
  }

  private Task earliest(long target) {
    Task best = null;
    for (Task task : queue) {
      if (task.due() > target) {
        continue;
      }
      if (best == null || task.due() < best.due() || (task.due() == best.due() && task.order() < best.order())) {
        best = task;
      }
    }
    return best;
  }

  private record Task(Object owner, long due, long order, Runnable task, Runnable retired) {
  }
}
