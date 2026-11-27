package art.arcane.adapt.content.golem;

public final class GolemWorkGate {
  private GolemWorkGate() {
  }

  public static boolean canWork(GolemHandle handle) {
    return !handle.state().colossus();
  }
}
