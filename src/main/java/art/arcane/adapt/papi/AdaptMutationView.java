package art.arcane.adapt.papi;

import art.arcane.adapt.api.mutation.MutationSnapshot;

public record AdaptMutationView(
    boolean available,
    boolean enabled,
    String slotOneId,
    String slotTwoId,
    String slotOneName,
    String slotTwoName,
    boolean slotOneUnlocked,
    boolean slotTwoUnlocked,
    boolean perfect,
    int expressedCount,
    String expressedCountText,
    long combatLockRemainingMs,
    String combatLockText,
    boolean canSwap,
    MutationSnapshot source
) {
  private static final AdaptMutationView UNAVAILABLE = new AdaptMutationView(
      false, false, "", "", "", "", false, false, false, 0, "0", 0L, "0.00", true, null
  );

  public static AdaptMutationView unavailable() {
    return UNAVAILABLE;
  }
}
