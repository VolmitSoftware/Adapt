package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.content.golem.GolemBody;
import art.arcane.adapt.content.golem.GolemRegistry;
import art.arcane.adapt.content.golem.GolemRoster;
import art.arcane.adapt.platform.value.Id;

record AwakenCheck(boolean readable, boolean owner, boolean worldEnabled, int level, boolean rosterKnown, GolemRoster.Status status, boolean sealed,
                   boolean bodyUnlocked, GolemRegistry.BindBlock registryBlock, boolean spaceClear, Id replaced, boolean waterlogged,
                   GolemBody body, boolean allowed) {
}
