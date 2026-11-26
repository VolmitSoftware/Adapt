package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationDamageTargets;
import art.arcane.adapt.api.adaptation.ItemCooldowns;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Skull Toss's throw: a right click with a wither skeleton skull spends it and launches a wither skull. */
public final class NetherSkullToss {
  public static final Id WITHER_SKELETON_SKULL = Id.minecraft("wither_skeleton_skull");
  public static final Id WITHER_SKULL = Id.minecraft("wither_skull");

  /**
   * Thrown skulls by entity id, mapped to the adaptation that threw them. Kept like plugin metadata: in memory only,
   * kept across chunk unloads, dropped once the skull leaves the world for good.
   */
  private static final Map<UUID, String> THROWN_SKULLS = new ConcurrentHashMap<>();

  private final Adaptation<?> owner;

  /**
   * The skull is both the ability trigger and the ammunition, so the sweep and the gate share one state on the whole
   * material.
   */
  private final ItemCooldowns cooldowns;

  public NetherSkullToss(Adaptation<?> owner) {
    this.owner = owner;
    this.cooldowns = ItemCooldowns.forMaterial(WITHER_SKELETON_SKULL).ownedBy(owner);
  }

  public static boolean isThrownSkull(EntityRef entity) {
    return entity != null && THROWN_SKULLS.containsKey(entity.uuid());
  }

  public static boolean thrownBy(EntityRef entity, Adaptation<?> owner) {
    return entity != null && owner.getName().equals(THROWN_SKULLS.get(entity.uuid()));
  }

  /**
   * Handles a right click; returns whether a skull was thrown. Gives {@code xpOnThrow} skill XP once the skull spawns.
   */
  public boolean toss(PlayerInteractEvent e, PlayerRef p, long cooldownMillis, double xpOnThrow) {
    if (e.action() != InteractAction.RIGHT_CLICK_AIR && e.action() != InteractAction.RIGHT_CLICK_BLOCK) {
      return false;
    }
    ItemRef item = e.item();
    if (e.hand() != Hand.MAIN_HAND || item == null || item.isEmpty() || !item.type().equals(WITHER_SKELETON_SKULL)) {
      return false;
    }

    boolean vetoed = e.clickedBlock() != null
        ? e.useBlock() == UseResult.DENY
        : e.useItem() == UseResult.DENY;
    if (vetoed) {
      e.setCancelled(true);
      return false;
    }

    if (!cooldowns.isReady(p, cooldownMillis)) {
      e.setCancelled(true);
      NetherSkullYeet.tossDeniedFx(owner, p);
      return false;
    }

    e.setCancelled(true);
    if (p.gameMode() != GameMode.CREATIVE
        && !owner.payItemCost(p, "skull", AdaptCore.platform().host().items().create(WITHER_SKELETON_SKULL), 1, () -> {
          item.setAmount(item.amount() - 1);
          p.inventory().setMainHand(item);
          return true;
        })) {
      return false;
    }

    cooldowns.mark(p, cooldownMillis);

    Loc eye = p.eyeLocation();
    Vec3 dir = eye.direction();
    Loc spawn = eye.add(.5, -.5, .5).add(dir);
    EntityRef skull = p.world().spawn(WITHER_SKULL, spawn, (EntityRef entity) -> {
      if (entity instanceof ProjectileRef projectile) {
        projectile.setRotation(eye.yaw(), eye.pitch());
        projectile.setVelocity(dir.normalize());
        projectile.setShooter(p);
      }
      THROWN_SKULLS.put(entity.uuid(), owner.getName());
      owner.xp(p, xpOnThrow);
    });
    NetherSkullYeet.tossFx(owner, spawn, dir, skull);
    return true;
  }

  /** Whether a blast or hit from a skull {@code owner} threw must spare the victim. */
  public static boolean spares(Adaptation<?> owner, EntityDamageByEntityEvent e, boolean ignorePassiveMobs) {
    return e.damager().type().equals(WITHER_SKULL)
        && e.damager() instanceof ProjectileRef skull
        && thrownBy(skull, owner)
        && skull.shooter() instanceof PlayerRef thrower
        && (owner.isProtectedFriendly(thrower, e.entity())
        || (e.cause() == DamageCause.ENTITY_EXPLOSION
        && !AdaptationDamageTargets.allows(e.entity(), ignorePassiveMobs)));
  }

  public static void forget(EntityRemoveEvent e) {
    if (e.cause() != EntityRemoveEvent.Cause.UNLOAD && e.cause() != EntityRemoveEvent.Cause.CHANGED_DIMENSION
        && !THROWN_SKULLS.isEmpty()) {
      THROWN_SKULLS.remove(e.entity().uuid());
    }
  }
}
