package art.arcane.adapt.api.mutation;

public record MutationSelectionResult(
    boolean success,
    String message,
    long cooldownRemainingMillis
) {
  public MutationSelectionResult {
    message = message == null ? "" : message;
    cooldownRemainingMillis = Math.max(0L, cooldownRemainingMillis);
  }

  public static MutationSelectionResult success(String message) {
    return new MutationSelectionResult(true, message, 0L);
  }

  public static MutationSelectionResult rejected(String message) {
    return new MutationSelectionResult(false, message, 0L);
  }

  public static MutationSelectionResult cooldown(String message, long remainingMillis) {
    return new MutationSelectionResult(false, message, remainingMillis);
  }
}
