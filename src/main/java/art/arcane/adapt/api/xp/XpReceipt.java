package art.arcane.adapt.api.xp;

public record XpReceipt(String skill, String source, XpAward.Kind kind, double requested,
                        double contextual, double boosted, double eligible, double credited, long atMillis) {
}
