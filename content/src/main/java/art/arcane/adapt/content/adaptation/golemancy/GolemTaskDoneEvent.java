package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemTask;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.value.Loc;

import java.util.Objects;

/** A bound golem finished a task; posted on the owner's thread with the task and where the golem stood. Read-only. */
public class GolemTaskDoneEvent extends AdaptEvent {
  private final PlayerRef owner;
  private final GolemHandle golem;
  private final Loc at;
  private final GolemTask task;

  public GolemTaskDoneEvent(PlayerRef owner, GolemHandle golem, Loc at, GolemTask task) {
    this.owner = Objects.requireNonNull(owner, "owner");
    this.golem = Objects.requireNonNull(golem, "golem");
    this.at = Objects.requireNonNull(at, "at");
    this.task = Objects.requireNonNull(task, "task");
  }

  public PlayerRef owner() {
    return owner;
  }

  public GolemHandle golem() {
    return golem;
  }

  public Loc at() {
    return at;
  }

  public GolemTask task() {
    return task;
  }
}
