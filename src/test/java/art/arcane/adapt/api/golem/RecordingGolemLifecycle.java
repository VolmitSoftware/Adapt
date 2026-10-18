package art.arcane.adapt.api.golem;

import java.util.ArrayList;
import java.util.List;

final class RecordingGolemLifecycle implements GolemLifecycle {
  final List<Event> events = new ArrayList<>();
  final List<Boolean> boundOnDetach = new ArrayList<>();
  RuntimeException awakeFailure;

  @Override
  public void awake(GolemHandle handle) {
    events.add(new Event(Kind.AWAKE, handle));
    if (awakeFailure != null) {
      throw awakeFailure;
    }
  }

  @Override
  public void asleep(GolemHandle handle) {
    events.add(new Event(Kind.ASLEEP, handle));
  }

  @Override
  public void detached(GolemHandle handle) {
    events.add(new Event(Kind.DETACHED, handle));
    boundOnDetach.add(GolemState.has(handle.entity()));
  }

  List<Kind> kinds() {
    List<Kind> kinds = new ArrayList<>(events.size());
    for (Event event : events) {
      kinds.add(event.kind());
    }
    return kinds;
  }

  enum Kind {
    AWAKE,
    ASLEEP,
    DETACHED
  }

  record Event(Kind kind, GolemHandle handle) {
  }
}
