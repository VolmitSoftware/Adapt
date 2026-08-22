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
  public static AdaptMutationView unavailable() {
    return new AdaptMutationView(false, false, "", "", "", "", false, false, false, 0, "0", 0L, "0.00", true, null);
  }
}
