package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.content.golem.GolemBody;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;

record BindingTarget(PlayerRef owner, MobRef golem, GolemBody body, GolemCoreType core) {
}
