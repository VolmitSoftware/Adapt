package art.arcane.adapt.content.mutation.runtime;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class MutationRuntimeStore {
  static final long UTILITY_ECHO_WINDOW_MILLIS = 50L;

  private final AtomicLong loadoutGenerations = new AtomicLong();

  final Map<BlockPosition, TemporaryBlock> temporaryBlocks = new ConcurrentHashMap<>();
  final Map<UUID, SpawnProvenance> spawnProvenance = new ConcurrentHashMap<>();
  final Map<UUID, DeathMutationGrant> deathMutationGrants = new ConcurrentHashMap<>();
  final Map<UUID, PlayerRuntimeState> players = new ConcurrentHashMap<>();
  final Map<UUID, Set<UUID>> quarryOwners = new ConcurrentHashMap<>();
  final Map<UUID, UUID> paradoxMarkers = new ConcurrentHashMap<>();

  PlayerRuntimeState player(UUID playerId) {
    return players.computeIfAbsent(playerId, ignored -> new PlayerRuntimeState(nextLoadoutGeneration()));
  }

  long nextLoadoutGeneration() {
    return loadoutGenerations.incrementAndGet();
  }

  PlayerRuntimeState existing(UUID playerId) {
    return players.get(playerId);
  }

  PlayerRuntimeState remove(UUID playerId) {
    return players.remove(playerId);
  }

  void clear() {
    players.clear();
    temporaryBlocks.clear();
    spawnProvenance.clear();
    deathMutationGrants.clear();
    quarryOwners.clear();
    paradoxMarkers.clear();
  }

  static final class PlayerRuntimeState {
    long loadoutGeneration;
    long utilityEchoClaimedUntil;
    long resourceSaveGeneration;
    boolean resourceSavePending;
    final GaleState gale = new GaleState();
    final BastionState bastion = new BastionState();
    final MoltState molt = new MoltState();
    final ParadoxState paradox = new ParadoxState();
    final ArsenalState arsenal = new ArsenalState();
    final PackState pack = new PackState();
    final TrophyState trophy = new TrophyState();
    final UmbralState umbral = new UmbralState();
    final LatticeState lattice = new LatticeState();
    final MycelialState mycelial = new MycelialState();
    final GraveState grave = new GraveState();
    final FormulaState formula = new FormulaState();
    long temperboundRejectionUntil;
    long trophyClearConfirmUntil;

    PlayerRuntimeState(long loadoutGeneration) {
      this.loadoutGeneration = loadoutGeneration;
    }

    synchronized boolean tryClaimUtilityEcho(long now) {
      if (now < utilityEchoClaimedUntil) {
        return false;
      }
      utilityEchoClaimedUntil = now + UTILITY_ECHO_WINDOW_MILLIS;
      return true;
    }

    synchronized long requestResourceSave() {
      if (resourceSavePending) {
        return 0L;
      }
      resourceSavePending = true;
      resourceSaveGeneration++;
      return resourceSaveGeneration;
    }

    synchronized boolean claimResourceSave(long expectedGeneration) {
      if (!resourceSavePending || resourceSaveGeneration != expectedGeneration) {
        return false;
      }
      resourceSavePending = false;
      return true;
    }

    synchronized boolean claimImmediateResourceSave() {
      if (!resourceSavePending) {
        return false;
      }
      resourceSavePending = false;
      resourceSaveGeneration++;
      return true;
    }

    void clearTransient() {
      utilityEchoClaimedUntil = 0L;
      resourceSavePending = false;
      resourceSaveGeneration++;
      gale.clear();
      bastion.clear();
      molt.clear();
      paradox.clear();
      arsenal.clear();
      pack.clear();
      trophy.clear();
      umbral.clear();
      lattice.clearTransient();
      mycelial.clear();
      grave.clear();
      formula.clearTransient();
      temperboundRejectionUntil = 0L;
      trophyClearConfirmUntil = 0L;
    }
  }

  static final class GaleState {
    double momentum;
    Location lastLocation;
    Vector lastMovement = new Vector();
    long lastPurposefulAt;
    long lastMovementAt;
    long lastIncomingHitAt;
    UUID reservedProjectile;
    long reservedProjectileExpiresAt;
    long ventGeneration;
    boolean ventScheduled;

    void clear() {
      momentum = 0D;
      lastLocation = null;
      lastMovement.zero();
      lastPurposefulAt = 0L;
      lastMovementAt = 0L;
      lastIncomingHitAt = 0L;
      reservedProjectile = null;
      reservedProjectileExpiresAt = 0L;
      ventGeneration++;
      ventScheduled = false;
    }
  }

  static final class BastionState {
    Location stillLocation;
    long stillSince;
    boolean anchored;
    double stability;
    long anchorGeneration;
    boolean anchorScheduled;

    void clear() {
      stillLocation = null;
      stillSince = 0L;
      anchored = false;
      stability = 0D;
      anchorGeneration++;
      anchorScheduled = false;
    }
  }

  static final class MoltState {
    Location origin;
    long generation;
    long readyAt;
    long recoveryUntil;
    PotionEffectType guardedType;
    final Set<PotionEffectType> nonCleansableEffects = new HashSet<>();

    void clear() {
      origin = null;
      generation++;
      recoveryUntil = 0L;
      guardedType = null;
      nonCleansableEffects.clear();
    }
  }

  static final class ParadoxState {
    Location origin;
    long expiresAt;
    long generation;
    Entity marker;
    boolean returning;

    void clear() {
      origin = null;
      expiresAt = 0L;
      generation++;
      marker = null;
      returning = false;
    }
  }

  static final class ArsenalState {
    MutationWeaponFamily family;
    MutationUtilityTag tag = MutationUtilityTag.NONE;
    int chainLength;
    long expiresAt;
    long dullUntil;

    void clear() {
      family = null;
      tag = MutationUtilityTag.NONE;
      chainLength = 0;
      expiresAt = 0L;
      dullUntil = 0L;
    }
  }

  static final class PackState {
    UUID quarryId;
    UUID quarryWorldId;
    long expiresAt;
    long generation;
    int tempo;
    final LinkedHashMap<UUID, Long> members = new LinkedHashMap<>();

    void clear() {
      quarryId = null;
      quarryWorldId = null;
      expiresAt = 0L;
      generation++;
      tempo = 0;
      members.clear();
    }
  }

  static final class UmbralState {
    final LinkedHashMap<UUID, UmbralMemory> memories = new LinkedHashMap<>(8, 0.75F, true);
    final LinkedHashMap<UUID, Long> exposedViewers = new LinkedHashMap<>(8, 0.75F, true);
    long generation;
    long exposureGeneration;

    void clear() {
      memories.clear();
      exposedViewers.clear();
      generation++;
      exposureGeneration++;
    }
  }

  static final class TrophyState {
    long reservationGeneration;
    long recognitionGeneration;
    long nextRecognitionAt;
    boolean recognitionScheduled;
    String reservedFamily;
    long reservedExpiry;
    boolean reservationCommitted;

    long reserve(String family, long expiry) {
      if (reservedFamily != null) {
        return 0L;
      }
      reservationGeneration++;
      reservedFamily = family;
      reservedExpiry = expiry;
      reservationCommitted = false;
      return reservationGeneration;
    }

    boolean matches(long generation, String family, long expiry) {
      return generation == reservationGeneration
          && family != null
          && family.equals(reservedFamily)
          && expiry == reservedExpiry;
    }

    boolean commit(long generation, String family, long expiry) {
      if (!matches(generation, family, expiry) || reservationCommitted) {
        return false;
      }
      reservationCommitted = true;
      return true;
    }

    boolean committed(long generation, String family, long expiry) {
      return matches(generation, family, expiry) && reservationCommitted;
    }

    void release(long generation) {
      if (generation != reservationGeneration) {
        return;
      }
      reservationGeneration++;
      reservedFamily = null;
      reservedExpiry = 0L;
      reservationCommitted = false;
    }

    void clear() {
      reservationGeneration++;
      recognitionGeneration++;
      nextRecognitionAt = 0L;
      recognitionScheduled = false;
      reservedFamily = null;
      reservedExpiry = 0L;
      reservationCommitted = false;
    }
  }

  static final class LatticeState {
    final Map<BlockPosition, HarvestRecord> harvested = new HashMap<>();
    final Set<BlockPosition> activeBlocks = new HashSet<>();
    final Map<Long, LatticeStructure> structures = new HashMap<>();
    long nextStructureId = 1L;
    int reservedBlocks;
    int pendingRootRefunds;
    long collapseLockUntil;

    void clearTransient() {
      harvested.clear();
      activeBlocks.clear();
      structures.clear();
      reservedBlocks = 0;
      pendingRootRefunds = 0;
      collapseLockUntil = 0L;
    }
  }

  static final class LatticeStructure {
    final long loadoutGeneration;
    final long expiresAt;
    final Set<Integer> pendingReservations = new HashSet<>();
    int pendingPlacements;
    int placedBlocks;
    boolean told;

    LatticeStructure(long loadoutGeneration, long expiresAt, int pendingPlacements) {
      this.loadoutGeneration = loadoutGeneration;
      this.expiresAt = expiresAt;
      this.pendingPlacements = Math.max(0, pendingPlacements);
      for (int reservationId = 0; reservationId < this.pendingPlacements; reservationId++) {
        pendingReservations.add(reservationId);
      }
    }

    boolean completePlacement(int reservationId, boolean placed) {
      if (!pendingReservations.remove(reservationId)) {
        return false;
      }
      pendingPlacements--;
      if (placed) {
        placedBlocks++;
      }
      return true;
    }

    boolean hasReservation(int reservationId) {
      return pendingReservations.contains(reservationId);
    }

    int expireReservations() {
      int expired = pendingPlacements;
      pendingReservations.clear();
      pendingPlacements = 0;
      return expired;
    }

    boolean isPlacementComplete() {
      return pendingPlacements == 0;
    }
  }

  static final class MycelialState {
    long generation;
    long reconnectAt;
    final Map<EffectKey, CopiedEffect> copies = new HashMap<>();

    void clear() {
      generation++;
      copies.clear();
      reconnectAt = 0L;
    }
  }

  static final class GraveState {
    long generation;
    final List<Bloom> blooms = new ArrayList<>();

    void clear() {
      generation++;
      blooms.clear();
    }
  }

  static final class FormulaState {
    long generation;
    long collapseLockUntil;
    boolean echoing;

    void clearTransient() {
      generation++;
      collapseLockUntil = 0L;
      echoing = false;
    }
  }

  record BlockPosition(UUID worldId, int x, int y, int z) {
    static BlockPosition of(Location location) {
      return new BlockPosition(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
  }

  record TemporaryBlock(
      UUID ownerId,
      long generation,
      BlockData originalData,
      long expiresAt
  ) {
  }

  record HarvestRecord(Material material, long expiresAt) {
  }

  record UmbralMemory(int angleBucket, MutationWeaponFamily family, long expiresAt) {
  }

  record EffectKey(UUID recipientId, PotionEffectType type) {
  }

  record CopiedEffect(
      UUID rootId,
      UUID recipientId,
      PotionEffectType type,
      int amplifier,
      long expiresAt,
      long generation
  ) {
  }

  record Bloom(
      BlockPosition position,
      long expiresAt,
      long graveGeneration,
      long loadoutGeneration,
      boolean perfect
  ) {
  }

  record SpawnProvenance(boolean natural, long recordedAt) {
  }

  record DeathMutationGrant(
      UUID ownerId,
      UUID sourceEntityId,
      long loadoutGeneration,
      boolean trophyCrucible,
      boolean gravebloom,
      long expiresAt
  ) {
    boolean active(long now) {
      return ownerId != null && sourceEntityId != null && loadoutGeneration > 0L && expiresAt >= now
          && (trophyCrucible || gravebloom);
    }
  }
}
