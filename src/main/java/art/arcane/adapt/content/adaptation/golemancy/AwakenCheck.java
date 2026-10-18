package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRoster;

record AwakenCheck(boolean readable, boolean owner, int level, boolean rosterKnown, GolemRoster.Status status, boolean sealed, boolean bodyUnlocked,
                   GolemRegistry.BindBlock registryBlock, boolean spaceClear, boolean allowed) {
}
