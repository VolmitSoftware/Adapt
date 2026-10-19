package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRoster;
import org.bukkit.Material;

record AwakenCheck(boolean readable, boolean owner, boolean worldEnabled, int level, boolean rosterKnown, GolemRoster.Status status, boolean sealed,
                   boolean bodyUnlocked, GolemRegistry.BindBlock registryBlock, boolean spaceClear, Material replaced, boolean waterlogged,
                   GolemBody body, boolean allowed) {
}
