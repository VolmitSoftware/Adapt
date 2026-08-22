package art.arcane.adapt.service;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.mutation.MutationSnapshot;
import art.arcane.adapt.content.mutation.runtime.MutationRuntimeRouter;
import art.arcane.adapt.content.mutation.runtime.MutationUtilityTag;
import art.arcane.adapt.util.common.plugin.AdaptService;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public final class MutationRuntimeSVC implements AdaptService {
  private static volatile MutationRuntimeSVC instance;

  private volatile MutationRuntimeRouter router;

  public MutationRuntimeSVC() {
  }

  public static MutationRuntimeSVC get() {
    return instance;
  }

  @Override
  public void onEnable() {
    MutationRuntimeRouter created = new MutationRuntimeRouter();
    Adapt.instance.registerListener(created);
    created.recoverLoadedState();
    router = created;
    instance = this;
  }

  @Override
  public void onDisable() {
    if (instance == this) {
      instance = null;
    }
    MutationRuntimeRouter current = router;
    router = null;
    if (current != null) {
      HandlerList.unregisterAll(current);
      current.shutdown();
    }
  }

  public void onLoadoutChanged(Player player, MutationSnapshot previous, MutationSnapshot current) {
    MutationRuntimeRouter currentRouter = router;
    if (currentRouter != null) {
      currentRouter.onLoadoutChanged(player, previous, current);
    }
  }

  public void onConfigReload() {
    MutationRuntimeRouter currentRouter = router;
    if (currentRouter != null) {
      currentRouter.onConfigReload();
    }
  }

  public boolean attuneCurrentArmor(Player player) {
    MutationRuntimeRouter current = router;
    return current != null && current.attuneCurrentArmor(player);
  }

  public boolean dissolveTemperbound(Player player) {
    MutationRuntimeRouter current = router;
    return current != null && current.dissolveTemperbound(player);
  }

  public boolean bindMasterwork(Player player) {
    MutationRuntimeRouter current = router;
    return current != null && current.bindMasterwork(player);
  }

  public boolean abandonMasterwork(Player player) {
    MutationRuntimeRouter current = router;
    return current != null && current.abandonMasterwork(player);
  }

  public boolean bindDeepbloodTool(Player player) {
    MutationRuntimeRouter current = router;
    return current != null && current.bindDeepbloodTool(player);
  }

  public boolean emitAnomalyUtility(
      Player actor,
      LivingEntity target,
      MutationUtilityTag tag,
      double strength,
      boolean recursiveEcho
  ) {
    MutationRuntimeRouter current = router;
    return current != null && current.emitAnomalyUtility(actor, target, tag, strength, recursiveEcho);
  }
}
