package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.IntToLongFunction;

/** Keeps an owned pet alive at 1 HP on a lethal hit, shields it briefly and teleports it to its owner. */
public final class TameLastStand {
  private final Adaptation<?> owner;
  private final Cooldowns saveCd;
  private final Consumer<PlayerRef> onSaved;
  private final Map<UUID, Long> protectedPets = new ConcurrentHashMap<>();

  /** {@code onSaved} runs on the owner's thread for every save, before the pet is teleported. */
  public TameLastStand(Adaptation<?> owner, Cooldowns saveCd, Consumer<PlayerRef> onSaved) {
    this.owner = owner;
    this.saveCd = saveCd;
    this.onSaved = onSaved;
  }

  /** Cancels damage to a pet still inside its protected window. */
  public void guard(EntityDamageEvent e) {
    UUID entityId = e.entity().uuid();
    Long until = protectedPets.get(entityId);
    if (until == null) {
      return;
    }

    if (until < System.currentTimeMillis()) {
      protectedPets.remove(entityId);
      return;
    }

    e.setCancelled(true);
  }

  /**
   * Saves the pet when {@code e} would kill an owned pet whose online owner has the owning adaptation, and returns the
   * saved pet, or null when nothing was saved.
   */
  public LivingRef save(EntityDamageEvent e, IntToLongFunction cooldownMillis, int invulnTicks) {
    if (!(e.entity() instanceof LivingRef living) || !living.has(EntityTrait.TAMEABLE)
        || !AdaptApi.platform().host().animals().isTamed(living)) {
      return null;
    }
    UUID petOwner = living.tameOwner();
    PlayerRef player = petOwner == null ? null : AdaptApi.platform().host().player(petOwner);
    if (player == null) {
      return null;
    }

    double finalDamage = e.finalDamage();
    if (living.health() - finalDamage > 0.0) {
      return null;
    }

    int level = owner.getActiveLevel(player);
    if (level <= 0 || !owner.preference(player, TamingPreferences.PETS).accepts(living.type())) {
      return null;
    }

    UUID petId = living.uuid();
    if (!saveCd.isReady(petId, cooldownMillis.applyAsLong(level))) {
      return null;
    }

    saveCd.mark(petId);
    e.setDamage(0);
    e.setCancelled(true);
    living.setHealth(1.0);
    living.setFireTicks(0);

    long now = System.currentTimeMillis();
    protectedPets.values().removeIf(until -> until < now);
    protectedPets.put(petId, now + (invulnTicks * 50L));

    TamingLastBreath.saveFx(owner, living);

    UUID ownerId = player.uuid();
    AdaptApi.scheduler().runEntity(player, () -> recallPet(player, ownerId, living));
    return living;
  }

  private void recallPet(PlayerRef player, UUID ownerId, LivingRef pet) {
    if (!player.isOnline()) {
      return;
    }

    Loc safe = findSafeLocation(player);
    if (safe == null) {
      safe = player.location();
    }

    onSaved.accept(player);
    beginRecallTeleport(ownerId, pet, safe);
  }

  private void beginRecallTeleport(UUID ownerId, LivingRef pet, Loc destination) {
    CompletableFuture<Boolean> teleport;
    try {
      teleport = pet.teleport(destination, TeleportCause.PLUGIN);
    } catch (RuntimeException error) {
      AdaptApi.log().error("Last Breath could not start pet teleport for owner " + ownerId + ".", null);
      AdaptApi.log().error("TameLastStand failed", error);
      return;
    }
    if (teleport == null) {
      return;
    }
    teleport.whenComplete((success, failure) ->
        completeRecallTeleport(ownerId, pet, destination, success, failure));
  }

  private void completeRecallTeleport(UUID ownerId, LivingRef pet, Loc destination,
                                      Boolean success, Throwable failure) {
    if (failure != null) {
      AdaptApi.log().error("Last Breath pet teleport failed for owner " + ownerId + ".", null);
      AdaptApi.log().error("TameLastStand failed", failure);
    }
    if (!TameRecall.successfulRecallTeleport(success, failure)) {
      return;
    }
    if (!AdaptApi.scheduler().runEntity(pet, () -> showRecallArrivalOwned(pet, destination))) {
      AdaptApi.log().warn("Last Breath completed pet teleport but could not schedule arrival effects for owner "
          + ownerId + ".");
    }
  }

  private void showRecallArrivalOwned(LivingRef pet, Loc destination) {
    if (!pet.isValid() || pet.isDead()) {
      return;
    }

    TamingLastBreath.arrivalFx(owner, destination);
  }

  private static Loc findSafeLocation(PlayerRef p) {
    Loc base = p.location();
    int[][] offsets = {
        {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1},
        {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    for (int y = 0; y <= 2; y++) {
      for (int[] offset : offsets) {
        Loc candidate = base.add(offset[0], y, offset[1]);
        if (isSafeSpot(candidate)) {
          return candidate;
        }
      }
    }

    return null;
  }

  private static boolean isSafeSpot(Loc location) {
    WorldRef world = location.world();
    BlockRef feet = world.blockAt(location);
    BlockRef head = world.blockAt(location.add(0, 1, 0));
    BlockRef below = world.blockAt(location.add(0, -1, 0));
    return feet.isPassable() && head.isPassable() && below.isSolid();
  }
}
