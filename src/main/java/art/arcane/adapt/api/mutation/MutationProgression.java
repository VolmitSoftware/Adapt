package art.arcane.adapt.api.mutation;

public record MutationProgression(
    int slotOneLevel,
    int slotTwoLevel,
    int perfectLevel,
    boolean perfectEnabled
) {
  public MutationProgression {
    slotOneLevel = Math.max(0, slotOneLevel);
    slotTwoLevel = Math.max(slotOneLevel, slotTwoLevel);
    perfectLevel = Math.max(slotTwoLevel, perfectLevel);
  }

  public boolean isSlotUnlocked(int level, int slot) {
    return switch (slot) {
      case 1 -> level >= slotOneLevel;
      case 2 -> level >= slotTwoLevel;
      default -> false;
    };
  }

  public int unlockedSlotCount(int level) {
    if (isSlotUnlocked(level, 2)) {
      return 2;
    }
    return isSlotUnlocked(level, 1) ? 1 : 0;
  }

  public boolean isPerfect(int level) {
    return perfectEnabled && level >= perfectLevel;
  }

  public boolean isBurdenActive(int level) {
    return !isPerfect(level);
  }
}
