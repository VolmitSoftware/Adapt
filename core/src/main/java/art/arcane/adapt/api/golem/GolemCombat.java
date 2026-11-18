package art.arcane.adapt.api.golem;

public final class GolemCombat {
  private GolemCombat() {
  }

  public static boolean locked(GolemHandle handle) {
    return GolemMastery.cores(handle).contains(GolemCoreType.GUARDIAN);
  }

  public static boolean enabled(GolemHandle handle) {
    return !handle.state().combatOff() || locked(handle);
  }
}
