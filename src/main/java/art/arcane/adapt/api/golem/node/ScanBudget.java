package art.arcane.adapt.api.golem.node;

public final class ScanBudget {
  private final int start;
  private final int count;
  private int taskRoom;

  ScanBudget(int start, int count, int taskRoom) {
    this.start = Math.max(0, start);
    this.count = Math.max(0, count);
    this.taskRoom = Math.max(0, taskRoom);
  }

  public int start() {
    return start;
  }

  public int end() {
    return start + count;
  }

  public int count() {
    return count;
  }

  public int taskRoom() {
    return taskRoom;
  }

  public boolean hasTaskRoom() {
    return taskRoom > 0;
  }

  void taken() {
    if (taskRoom > 0) {
      taskRoom--;
    }
  }
}
