package art.arcane.adapt.api.claims;

public record ClaimAttemptResult(boolean allowed, boolean credited, double progress, int required) {
}
