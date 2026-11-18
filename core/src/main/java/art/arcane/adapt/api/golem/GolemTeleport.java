package art.arcane.adapt.api.golem;

import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.concurrent.CompletableFuture;

public final class GolemTeleport {
  private GolemTeleport() {
  }

  public static CompletableFuture<Boolean> teleport(GolemHandle handle, Loc destination) {
    MobRef golem = handle.entity();
    if (crossesWorlds(golem.world(), destination.world())) {
      GolemancyRuntime.reveal().ifPresent(reveal -> reveal.forget(handle));
    }
    return golem.teleport(destination, TeleportCause.PLUGIN, true);
  }

  private static boolean crossesWorlds(WorldRef from, WorldRef to) {
    return from != null && to != null && !from.uid().equals(to.uid());
  }
}
