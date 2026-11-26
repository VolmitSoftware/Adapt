package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.entity.AnimalOps;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.math.M;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Animals and neutral mobs caught in a snare. A captive is held in place until its trapper comes within the lead
 * range, then is tied to the trapper with a real lead that slips when they get too far apart, when the game breaks
 * it, or when the trapper leaves. Nothing is spawned or copied, and no lead item is ever made or dropped: the lead
 * the game would drop when it breaks is cancelled.
 */
public final class HunterCaptures {
  private static final String HOLD_SLOT = "capture";
  private static final int HOLD_REFRESH_TICKS = 10;
  private static final double FULL_STOP = -1D;
  private static final double SLIPPED_LEAD_RANGE_SQUARED = 4D;
  private static final Id LEAD = Id.minecraft("lead");
  private static final Set<Id> NEVER = Set.of(Id.minecraft("villager"), Id.minecraft("wandering_trader"),
      Id.minecraft("zombie_villager"));

  private final Adaptation<?> owner;
  private final Map<UUID, Captive> captives = new ConcurrentHashMap<>();

  public HunterCaptures(Adaptation<?> owner) {
    this.owner = owner;
  }

  public record Settings(double leadRange, double breakRange, int maxPerPlayer) {
  }

  /**
   * An untamed, unleashed animal or neutral mob: never a player, villager, trader, boss, hostile, anyone's pet or
   * anyone's leashed animal.
   */
  public static boolean capturable(EntityRef entity) {
    return entity instanceof LivingRef && !(entity instanceof PlayerRef) && !NEVER.contains(entity.type())
        && !entity.has(EntityTrait.MONSTER) && !entity.has(EntityTrait.ENEMY) && !entity.has(EntityTrait.VILLAGER)
        && !entity.has(EntityTrait.BOSS) && !entity.has(EntityTrait.ARMOR_STAND) && !entity.has(EntityTrait.DISPLAY)
        && entity.tameOwner() == null && entity.isValid() && !entity.isDead() && !animals().isLeashed(entity);
  }

  /** Holds {@code creature} for {@code trapper}; false when it is already held or the trapper holds the most allowed. */
  public boolean hold(UUID trapper, LivingRef creature, long holdTicks, int maxPerPlayer) {
    if (captives.containsKey(creature.uuid()) || count(trapper) >= Math.max(1, maxPerPlayer)) {
      return false;
    }
    captives.put(creature.uuid(), new Captive(trapper, M.ms() + (Math.max(1L, holdTicks) * 50L)));
    root(creature);
    return true;
  }

  public boolean isHeld(UUID creature) {
    return captives.containsKey(creature);
  }

  public boolean isLed(UUID creature) {
    Captive captive = captives.get(creature);
    return captive != null && captive.led;
  }

  public int count(UUID trapper) {
    int count = 0;
    for (Captive captive : captives.values()) {
      if (captive.trapper.equals(trapper)) {
        count++;
      }
    }
    return count;
  }

  public boolean isEmpty() {
    return captives.isEmpty();
  }

  public void forget(UUID trapper) {
    PlatformHost host = AdaptCore.platform().host();
    for (Map.Entry<UUID, Captive> entry : captives.entrySet()) {
      if (entry.getValue().trapper.equals(trapper) && captives.remove(entry.getKey(), entry.getValue())) {
        release(host.entity(entry.getKey()), entry.getValue());
      }
    }
  }

  /** True when {@code item}, spawning at {@code at}, is the lead a led captive just slipped. */
  public boolean slippedLead(ItemRef item, Loc at) {
    if (captives.isEmpty() || item == null || !LEAD.equals(item.type())) {
      return false;
    }
    PlatformHost host = AdaptCore.platform().host();
    for (Map.Entry<UUID, Captive> entry : captives.entrySet()) {
      EntityRef entity = entry.getValue().led ? host.entity(entry.getKey()) : null;
      if (entity != null && at.world() != null && at.world().equals(entity.world())
          && entity.location().distanceSquared(at) <= SLIPPED_LEAD_RANGE_SQUARED && !animals().isLeashed(entity)) {
        return true;
      }
    }
    return false;
  }

  public void tick(Settings settings) {
    PlatformHost host = AdaptCore.platform().host();
    long now = M.ms();
    for (Map.Entry<UUID, Captive> entry : captives.entrySet()) {
      UUID id = entry.getKey();
      Captive captive = entry.getValue();
      EntityRef entity = host.entity(id);
      PlayerRef trapper = host.player(captive.trapper);
      if (!(entity instanceof LivingRef creature) || trapper == null || !trapper.isOnline()
          || owner.getActiveLevel(trapper) <= 0 || (!captive.led && captive.holdUntil <= now)) {
        if (captives.remove(id, captive)) {
          release(entity, captive);
        }
        continue;
      }
      Tasks.runEntity(trapper, () -> {
        Loc at = trapper.location();
        Tasks.runEntity(creature, () -> step(id, captive, creature, trapper, at, settings));
      });
    }
  }

  private void step(UUID id, Captive captive, LivingRef creature, PlayerRef trapper, Loc trapperAt, Settings settings) {
    if (captives.get(id) != captive || !creature.isValid() || creature.isDead()) {
      captives.remove(id, captive);
      return;
    }
    Loc here = creature.location();
    boolean sameWorld = here.world() != null && here.world().equals(trapperAt.world());
    double distance = sameWorld ? Math.sqrt(here.distanceSquared(trapperAt)) : Double.MAX_VALUE;
    if (!captive.led) {
      if (distance > settings.leadRange()) {
        root(creature);
        return;
      }
      AdaptAttributeService.get().remove(creature, owner.getName(), HOLD_SLOT, Attributes.MOVEMENT_SPEED);
      if (!animals().setLeashHolder(creature, trapper)) {
        captives.remove(id, captive);
        HunterSnareLine.snareBrokenFx(owner, here);
        return;
      }
      captive.led = true;
      HunterSnareLine.snaredFx(owner, creature);
      return;
    }
    if (!animals().isLeashed(creature)) {
      captives.remove(id, captive);
      HunterSnareLine.snareBrokenFx(owner, here);
      return;
    }
    if (distance > settings.breakRange() || !owner.canPVE(trapper, here) || !owner.canInteract(trapper, here)) {
      captives.remove(id, captive);
      animals().setLeashHolder(creature, null);
      HunterSnareLine.snareBrokenFx(owner, here);
    }
  }

  private static void release(EntityRef entity, Captive captive) {
    if (!captive.led || !(entity instanceof LivingRef creature) || !creature.isValid()) {
      return;
    }
    if (Tasks.isOwnedByCurrentRegion(creature)) {
      animals().setLeashHolder(creature, null);
    } else {
      Tasks.runEntity(creature, () -> animals().setLeashHolder(creature, null));
    }
  }

  private static AnimalOps animals() {
    return AdaptCore.platform().host().animals();
  }

  private void root(LivingRef creature) {
    AdaptAttributeService.get().applyTimed(creature, owner.getName(), HOLD_SLOT, Attributes.MOVEMENT_SPEED,
        FULL_STOP, AttributeOperation.ADD_MULTIPLIED_TOTAL, HOLD_REFRESH_TICKS);
    creature.setVelocity(Vec3.ZERO);
  }

  private static final class Captive {
    private final UUID trapper;
    private final long holdUntil;
    private volatile boolean led;

    private Captive(UUID trapper, long holdUntil) {
      this.trapper = trapper;
      this.holdUntil = holdUntil;
    }
  }
}
