package art.arcane.adapt.client.view;

/** Player-wide values. {@code masterProgress} is 0..1 toward the next master level. */
public record PlayerHeader(int masterLevel, double masterProgress, int powerUsed, int powerMax, long wisdom,
                           boolean effectsEnabled) {
}
