package art.arcane.adapt.api.golem.node;

public final class ScanBudget {
  private final int start;
  private final int count;
  private int taskRoom;
  private final int[] indices;

  ScanBudget(int start, int count, int taskRoom) {
    this.start = Math.max(0, start);
    this.count = Math.max(0, count);
    this.taskRoom = Math.max(0, taskRoom);
    this.indices = new int[this.count];
    for (int index = 0; index < this.count; index++) {
      indices[index] = this.start + index;
    }
  }

  ScanBudget(NodeScanCursor.Window window, int taskRoom) {
    this.start = window.start();
    this.count = window.count();
    this.taskRoom = Math.max(0, taskRoom);
    this.indices = window.indices() == null ? new int[count] : window.indices();
    if (window.indices() == null) {
      for (int index = 0; index < count; index++) {
        indices[index] = start + index;
      }
    }
  }

  public int[] indices() {
    return indices;
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

  public boolean hasTaskRoom() {
    return taskRoom > 0;
  }

  void taken() {
    if (taskRoom > 0) {
      taskRoom--;
    }
  }
}
