package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

record BindingTarget(Player owner, Mob golem, GolemBody body, GolemCoreType core) {
}
