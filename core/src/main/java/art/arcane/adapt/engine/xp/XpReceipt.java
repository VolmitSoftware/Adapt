package art.arcane.adapt.engine.xp;

import art.arcane.adapt.api.xp.XpAward;

public record XpReceipt(String skill, String source, XpAward.Kind kind, double requested,
                        double contextual, double boosted, double eligible, double credited, long atMillis) {
}
