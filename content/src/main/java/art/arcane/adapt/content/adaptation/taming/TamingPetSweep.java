package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.platform.entity.LivingRef;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Walks the loaded tamed pets a bounded slice at a time, for upgrades outside this package. */
public final class TamingPetSweep {
  private final TameableOwnershipIndex ownershipIndex = TameableOwnershipIndex.instance();
  private final TameableOwnershipIndex.Generation generation;
  private final AtomicBoolean closed = new AtomicBoolean();
  private Iterator<TameableOwnershipIndex.TrackedTameable> cursor = Collections.emptyIterator();

  public TamingPetSweep(String scope) {
    generation = ownershipIndex.claimGeneration(scope);
  }

  /** Visits up to {@code limit} pets on their own threads with their current owner, or null once they have none. */
  public void pass(List<PlayerProfile> owners, long now, int limit, BiConsumer<LivingRef, UUID> visit) {
    if (closed.get() || !ownershipIndex.isGenerationCurrent(generation)) {
      return;
    }

    ownershipIndex.ensureBootstrapped();
    ownershipIndex.discoverNearbyFolia(owners, now);
    if (!cursor.hasNext()) {
      cursor = ownershipIndex.iterator();
    }

    int examined = 0;
    while (examined < Math.max(1, limit) && cursor.hasNext()) {
      TameableOwnershipIndex.TrackedTameable tracked = cursor.next();
      examined++;
      Runnable task = () -> ownershipIndex.runIfGenerationCurrent(generation, () -> {
        if (closed.get()) {
          return;
        }
        UUID ownerId = ownershipIndex.refreshOwner(tracked);
        LivingRef pet = tracked.entity();
        if (pet != null) {
          visit.accept(pet, ownerId);
        }
      });
      if (AdaptApi.scheduler().isRegionized()) {
        LivingRef pet = tracked.entity();
        if (pet != null) {
          AdaptApi.scheduler().runEntity(pet, task);
        }
      } else {
        task.run();
      }
    }
  }

  /** Stops the sweep and runs {@code cleanup} on every loaded pet. */
  public void close(Consumer<LivingRef> cleanup) {
    if (closed.compareAndSet(false, true)) {
      ownershipIndex.cleanupLoaded(generation, cleanup);
    }
  }
}
