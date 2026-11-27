package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.content.golem.GolemKillCredit;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Wither Harvest's extra bones, coal and skull odds on a wither skeleton a player killed. */
public final class NetherSkeletonHarvest {
  private static final Id WITHER_SKELETON = Id.minecraft("wither_skeleton");
  private static final Id WITHER_SKELETON_SKULL = Id.minecraft("wither_skeleton_skull");

  private NetherSkeletonHarvest() {
  }

  /**
   * The player who killed the wither skeleton by hand, or null when the death is not one to harvest: a golem kill or a
   * spawner, egg or other farmed skeleton.
   */
  public static PlayerRef harvester(EntityDeathEvent e) {
    if (e.platformCancelled()) {
      return null;
    }
    LivingRef skeleton = e.entity();
    if (!skeleton.type().equals(WITHER_SKELETON)) {
      return null;
    }
    PlayerRef p = e.killer();
    return p == null || GolemKillCredit.isGolemKill(skeleton) || !MobOrigin.paysKillReward(p, skeleton) ? null : p;
  }

  /** Adds the bonus drops and plays the matching burst; returns whether a skull was added. */
  public static boolean harvest(Adaptation<?> owner, EntityDeathEvent e, int bones, int coal, double skullChance) {
    LivingRef skeleton = e.entity();
    ItemOps items = AdaptApi.platform().host().items();
    List<ItemRef> drops = e.drops();
    drops.add(items.create(Id.minecraft("bone"), bones));
    drops.add(items.create(Id.minecraft("coal"), coal));
    boolean skull = false;
    if (ThreadLocalRandom.current().nextDouble() <= skullChance && !containsSkull(drops)) {
      drops.add(items.create(WITHER_SKELETON_SKULL, 1));
      skull = true;
    }

    NetherWitherHarvest.harvestFx(owner, skeleton, skull);
    return skull;
  }

  private static boolean containsSkull(List<ItemRef> drops) {
    for (ItemRef drop : drops) {
      if (drop != null && drop.type().equals(WITHER_SKELETON_SKULL)) {
        return true;
      }
    }
    return false;
  }
}
