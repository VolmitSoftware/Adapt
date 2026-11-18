package art.arcane.adapt.api.golem;

public final class GolemWorkGate {
  private GolemWorkGate() {
  }

  public static boolean canWork(GolemHandle handle) {
    return !handle.state().colossus();
  }
}
