package art.arcane.adapt.api.golem.plinth;

public record LevelSpan(int lowest, int highest) {
  public String label() {
    return lowest == highest ? String.valueOf(lowest) : lowest + "-" + highest;
  }
}
