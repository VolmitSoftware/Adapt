package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.BrewingStandRef;
import art.arcane.adapt.platform.world.FurnaceRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Accelerate's aura: on every pulse, random blocks around each learner are sampled, and crops grow a stage while
 * running furnaces and brewing stands skip ahead.
 */
public final class ChronosAura {
  private static final Rgb AURA_COLOR = new Rgb(230, 210, 150);
  public static final int HARD_MAX_PLAYERS_PER_PASS = 512;
  public static final int HARD_MAX_SAMPLES_PER_PASS = 8192;
  private static final int MAX_CATCH_UP_PULSES = 4;

  private final Adaptation<?> owner;
  private final Tuning tuning;
  private final Map<UUID, Long> nextPulseAt;
  private int playerCursor;

  /** How strong the owner's aura is. {@code accelerated} runs on the player's thread after a pulse changed blocks. */
  public interface Tuning {
    long pulseIntervalMillis();

    int maxPlayersPerPass();

    int maxSamplesPerPass();

    int samples(int level);

    double radius(int level);

    /** The player's own scale on the sampled radius. */
    double radiusScale(PlayerRef player);

    double growChance(int level);

    double cookBoost(int level);

    boolean crops(PlayerRef player);

    boolean furnaces(PlayerRef player);

    boolean brewing(PlayerRef player);

    void accelerated(PlayerRef player, int count);
  }

  /** {@code nextPulseAt} comes from the owner's player state. */
  public ChronosAura(Adaptation<?> owner, Tuning tuning, Map<UUID, Long> nextPulseAt) {
    this.owner = owner;
    this.tuning = tuning;
    this.nextPulseAt = nextPulseAt;
  }

  public void forget(UUID id) {
    nextPulseAt.remove(id);
  }

  public void tick() {
    long now = System.currentTimeMillis();
    List<PlayerProfile> candidates = owner.learnedCandidates(now);
    ChronosWorkBudget.Batch batch = ChronosWorkBudget.batch(
        candidates.size(), playerCursor, tuning.maxPlayersPerPass(), HARD_MAX_PLAYERS_PER_PASS);
    AtomicInteger sampleBudget = new AtomicInteger(
        ChronosWorkBudget.bounded(tuning.maxSamplesPerPass(), 1, HARD_MAX_SAMPLES_PER_PASS));
    for (int i = 0; i < batch.count(); i++) {
      PlayerProfile adaptPlayer = candidates.get((batch.start() + i) % candidates.size());
      PlayerRef player = adaptPlayer.player();
      if (player != null) {
        AdaptApi.scheduler().runEntity(player, () -> prepareAcceleration(player, now, sampleBudget));
      }
    }
    playerCursor = batch.nextCursor();
  }

  private void prepareAcceleration(PlayerRef player, long now, AtomicInteger sampleBudget) {
    if (!player.isOnline()) {
      return;
    }

    UUID playerId = player.uuid();
    int level = owner.getActiveLevel(player);
    if (level <= 0) {
      nextPulseAt.remove(playerId);
      return;
    }

    long interval = Math.max(1L, tuning.pulseIntervalMillis());
    ChronosWorkBudget.Pulse pulse = ChronosWorkBudget.pulse(
        now, nextPulseAt.get(playerId), interval, MAX_CATCH_UP_PULSES);
    nextPulseAt.put(playerId, pulse.nextAt());
    if (pulse.count() <= 0) {
      return;
    }

    int requested = Math.max(1, tuning.samples(level)) * pulse.count();
    int granted = claimSamples(sampleBudget, requested);
    if (granted <= 0) {
      return;
    }

    Loc center = player.location();
    WorldRef world = center.world();
    if (world == null) {
      return;
    }

    AccelerationBatch acceleration = new AccelerationBatch(player, center, level, granted);
    ThreadLocalRandom random = ThreadLocalRandom.current();
    int radius = Math.max(1, (int) Math.round(tuning.radius(level) * tuning.radiusScale(player)));
    for (int i = 0; i < granted; i++) {
      int x = center.blockX() + random.nextInt(-radius, radius + 1);
      int y = center.blockY() + random.nextInt(-2, 3);
      int z = center.blockZ() + random.nextInt(-radius, radius + 1);
      Loc target = new Loc(world, x, y, z);
      if (!AdaptApi.scheduler().runAt(target, () -> acceleration.complete(accelerateBlock(player, target, level)))) {
        acceleration.complete(false);
      }
    }
  }

  private boolean accelerateBlock(PlayerRef player, Loc target, int level) {
    WorldRef world = target.world();
    if (world == null) {
      return false;
    }

    int chunkX = target.blockX() >> 4;
    int chunkZ = target.blockZ() >> 4;
    if (!AdaptApi.scheduler().owns(world, chunkX, chunkZ) || !world.isChunkLoaded(chunkX, chunkZ)) {
      return false;
    }

    BlockRef block = world.blockAt(target);
    Id type = block.type();
    if (block.isAir()) {
      return false;
    }

    if (ChronosAccelerate.FURNACE_TYPES.contains(type)) {
      if (tuning.furnaces(player)) {
        FurnaceRef furnace = block.furnace().orElse(null);
        if (furnace != null
            && advanceFurnace(owner, player, block, furnace, (int) Math.round(furnace.cookTimeTotal() * tuning.cookBoost(level)))) {
          ChronosAccelerate.stationFx(owner, world, target.blockX(), target.blockY(), target.blockZ(), true);
          return true;
        }
      }
      return false;
    }

    if (ChronosAccelerate.BREWING_STAND.equals(type)) {
      if (tuning.brewing(player)) {
        BrewingStandRef stand = block.brewingStand().orElse(null);
        if (stand != null && advanceBrewingStand(owner, player, block, stand, (int) Math.round(400D * tuning.cookBoost(level)))) {
          ChronosAccelerate.stationFx(owner, world, target.blockX(), target.blockY(), target.blockZ(), false);
          return true;
        }
      }
      return false;
    }

    if (!tuning.crops(player) || !growable(block)
        || ThreadLocalRandom.current().nextDouble() >= tuning.growChance(level)) {
      return false;
    }
    return advanceCrop(owner, player, block);
  }

  private static int claimSamples(AtomicInteger remaining, int requested) {
    int observed = remaining.get();
    while (observed > 0) {
      int claimed = Math.min(observed, requested);
      if (remaining.compareAndSet(observed, observed - claimed)) {
        return claimed;
      }
      observed = remaining.get();
    }
    return 0;
  }

  /** An ageable crop below its last stage and not on the growth blacklist. */
  public static boolean growable(BlockRef block) {
    BlockStateRef data = block.state();
    int age = data.age();
    return !block.isAir() && !AdaptApi.settings().isGrowthBlacklisted(block.type()) && age >= 0 && age < data.maxAge();
  }

  /** Grows a crop one stage when the player may change it there, recorded like bone meal; plays the crop effect. */
  public static boolean advanceCrop(Adaptation<?> owner, PlayerRef player, BlockRef block) {
    if (!growable(block)
        || !canAccelerateTarget(owner, player, block, false)
        || !owner.canBlockPlace(player, block.location())
        || !ProtectionProbes.attemptBlockPlaceProbe(player, block)) {
      return false;
    }
    BlockStateRef data = block.state();
    block.setState(data.with("age", String.valueOf(data.age() + 1)), true);
    XpProvenance.recordBonemeal(block);
    ChronosAccelerate.cropFx(owner, block.world(), block.x(), block.y(), block.z());
    return true;
  }

  /** Moves a burning furnace's current smelt {@code ticks} ahead, never finishing it outright. */
  public static boolean advanceFurnace(Adaptation<?> owner, PlayerRef player, BlockRef block, FurnaceRef furnace, int ticks) {
    if (furnace.burnTime() <= 0) {
      return false;
    }
    ItemRef smelting = furnace.smelting();
    if (smelting.isEmpty() || smelting.type().isAir()) {
      return false;
    }
    int total = furnace.cookTimeTotal();
    int current = furnace.cookTime();
    if (total <= 0 || current >= total - 1 || ticks <= 0) {
      return false;
    }
    if (!canAccelerateTarget(owner, player, block, true)) {
      return false;
    }
    furnace.setCookTime((short) Math.min(total - 1, current + ticks));
    return true;
  }

  /** Moves a running brew {@code ticks} ahead, never finishing it outright. */
  public static boolean advanceBrewingStand(Adaptation<?> owner, PlayerRef player, BlockRef block, BrewingStandRef stand,
                                            int ticks) {
    int brewingTime = stand.brewingTime();
    if (brewingTime <= 1 || ticks <= 0) {
      return false;
    }
    if (!canAccelerateTarget(owner, player, block, true)) {
      return false;
    }
    stand.setBrewingTime(Math.max(1, brewingTime - ticks));
    return true;
  }

  private static boolean canAccelerateTarget(Adaptation<?> owner, PlayerRef player, BlockRef block, boolean container) {
    if (!AdaptApi.scheduler().owns(player)
        || !AdaptApi.scheduler().owns(block.location())
        || !player.isOnline()
        || !owner.canInteract(player, block.location())
        || (container && !owner.canAccessChest(player, block.location()))) {
      return false;
    }
    try {
      return ProtectionProbes.attemptBlockUse(player, block);
    } catch (Throwable error) {
      AdaptApi.log().error("Chronos Accelerate could not verify target access for " + player.uuid() + ".", null);
      AdaptApi.log().error("ChronosAura failed", error);
      return false;
    }
  }

  private final class AccelerationBatch {
    private final PlayerRef player;
    private final Loc center;
    private final int level;
    private final AtomicInteger remaining;
    private final AtomicInteger accelerated;
    private final AtomicBoolean finalized;

    private AccelerationBatch(PlayerRef player, Loc center, int level, int samples) {
      this.player = player;
      this.center = center;
      this.level = level;
      remaining = new AtomicInteger(samples);
      accelerated = new AtomicInteger();
      finalized = new AtomicBoolean(false);
    }

    private void complete(boolean changed) {
      if (changed) {
        accelerated.incrementAndGet();
      }
      if (remaining.decrementAndGet() != 0 || !finalized.compareAndSet(false, true)) {
        return;
      }
      AdaptApi.scheduler().runEntity(player, this::finish);
    }

    private void finish() {
      int count = accelerated.get();
      if (!player.isOnline() || count <= 0) {
        return;
      }
      tuning.accelerated(player, count);
      Fx.now(owner, center, FxPriority.AMBIENT).dustRing(AURA_COLOR, tuning.radius(level), 8, 0.9F);
    }
  }
}
