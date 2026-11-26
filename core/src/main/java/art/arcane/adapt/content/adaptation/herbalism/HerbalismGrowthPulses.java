package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ObjIntConsumer;

/**
 * The Growth Aura engine: every learner pulses on a fixed interval, sampling random spots around them and ageing the
 * crops found there at the cost of hunger. The work is spread over ticks with bounded queues. Grown crops are recorded
 * like bone meal, so they never count as natural growth.
 */
public final class HerbalismGrowthPulses {
  private static final int PLAYER_CHECKS_PER_TICK = 32;
  private static final int LOCATION_SAMPLES_PER_TICK = 32;
  private static final int MUTATIONS_PER_TICK = 16;
  private static final int COMPLETIONS_PER_TICK = 16;
  private static final int MAX_QUEUED_SAMPLES = 4096;
  private static final int MAX_QUEUED_MUTATIONS = 4096;
  private static final int MAX_QUEUED_COMPLETIONS = 2048;
  private static final long ROSTER_REFRESH_MILLIS = 5000L;
  private static final long PULSE_INTERVAL_MILLIS = 850L;
  private static final long IDLE_CHECK_MILLIS = 250L;

  private final Adaptation<?> owner;
  private final Planner planner;
  private final ObjIntConsumer<PlayerRef> onGrown;
  private final Map<UUID, Long> nextCheckAt;
  private final Queue<UUID> playerQueue = new ConcurrentLinkedQueue<>();
  private final Set<UUID> queuedPlayers = ConcurrentHashMap.newKeySet();
  private final Set<UUID> pendingPulses = ConcurrentHashMap.newKeySet();
  private final Queue<GrowthSample> locationSamples = new ConcurrentLinkedQueue<>();
  private final AtomicInteger queuedSamples = new AtomicInteger();
  private final PriorityBlockingQueue<GrowthMutation> mutations = new PriorityBlockingQueue<>();
  private final AtomicInteger queuedMutations = new AtomicInteger();
  private final Queue<GrowthPulse> completedPulses = new ConcurrentLinkedQueue<>();
  private final AtomicInteger queuedCompletions = new AtomicInteger();
  private volatile long nextRosterRefreshAt;

  /** What one owner grows. All methods run on the player's thread. */
  public interface Planner {
    /** The pulse a player at {@code level} starts now; it may also do the owner's own pulse work. */
    Pulse plan(PlayerRef player, int level);

    boolean grows(PlayerRef player, Id block);

    /** Food level the hunger cost never takes the player under. */
    int foodFloor(PlayerRef player);
  }

  public record Pulse(double radius, int samples, double strength, double foodCost, boolean surfaceOnly) {
  }

  /** {@code onGrown} receives the stages each sample grew. */
  public HerbalismGrowthPulses(Adaptation<?> owner, Map<UUID, Long> nextCheckAt, Planner planner,
                               ObjIntConsumer<PlayerRef> onGrown) {
    this.owner = owner;
    this.nextCheckAt = nextCheckAt;
    this.planner = planner;
    this.onGrown = onGrown;
  }

  public boolean hasDemand() {
    return !playerQueue.isEmpty()
        || !queuedPlayers.isEmpty()
        || !pendingPulses.isEmpty()
        || queuedSamples.get() > 0
        || !locationSamples.isEmpty()
        || queuedMutations.get() > 0
        || !mutations.isEmpty()
        || queuedCompletions.get() > 0
        || !completedPulses.isEmpty();
  }

  public void tick() {
    long now = System.currentTimeMillis();
    refreshRoster(now);
    processLocationSamples();
    processMutations(now);
    processCompletions();
    processPlayers(now);
  }

  public void forget(UUID id) {
    nextCheckAt.remove(id);
    pendingPulses.remove(id);
    if (queuedPlayers.remove(id)) {
      playerQueue.remove(id);
    }
  }

  static int workFor(int queued, int limit) {
    return Math.min(Math.max(0, queued), Math.max(0, limit));
  }

  /** Whether the player may age this block: interaction, placement and both protection probes allow it. */
  public static boolean mayGrow(Adaptation<?> owner, PlayerRef player, BlockRef block) {
    return owner.canInteract(player, block.location())
        && owner.canBlockPlace(player, block.location())
        && ProtectionProbes.attemptBlockUse(player, block)
        && ProtectionProbes.attemptBlockPlaceProbe(player, block);
  }

  public static int sampleCountForRadius(double radius) {
    double limit = Math.max(3D, Math.min(Math.max(0D, radius * radius), 256D));
    return (int) Math.ceil(limit);
  }

  private void refreshRoster(long now) {
    if (now < nextRosterRefreshAt) {
      return;
    }
    nextRosterRefreshAt = now + ROSTER_REFRESH_MILLIS;
    for (AdaptPlayer adaptPlayer : owner.learnedCandidates(now)) {
      PlayerRef p = adaptPlayer.getPlayer();
      if (p != null) {
        queuePlayer(p.uuid());
      }
    }
  }

  private void processPlayers(long now) {
    int attempts = workFor(queuedPlayers.size(), PLAYER_CHECKS_PER_TICK);
    for (int i = 0; i < attempts; i++) {
      UUID id = playerQueue.poll();
      if (id == null) {
        break;
      }
      queuedPlayers.remove(id);
      Long due = nextCheckAt.get(id);
      if (due != null && due > now) {
        queuePlayer(id);
        continue;
      }
      PlayerRef p = AdaptCore.platform().host().player(id);
      if (p == null) {
        nextCheckAt.remove(id);
        pendingPulses.remove(id);
        continue;
      }
      owner.withPlayerThread(p, () -> evaluatePlayer(p));
    }
  }

  private void evaluatePlayer(PlayerRef p) {
    UUID id = p.uuid();
    if (!p.isOnline() || owner.getLevel(p) <= 0) {
      nextCheckAt.remove(id);
      pendingPulses.remove(id);
      return;
    }

    long now = System.currentTimeMillis();
    int level = owner.getActiveLevel(p);
    if (level <= 0 || pendingPulses.contains(id) || owner.getPlayer(p) == null) {
      nextCheckAt.put(id, now + IDLE_CHECK_MILLIS);
      queuePlayer(id);
      return;
    }

    pendingPulses.add(id);
    nextCheckAt.put(id, now + PULSE_INTERVAL_MILLIS);
    startGrowthPulse(p, planner.plan(p, level));
    queuePlayer(id);
  }

  private void startGrowthPulse(PlayerRef p, Pulse plan) {
    double radius = plan.radius();
    int sampleCount = plan.samples();
    if (sampleCount <= 0) {
      pendingPulses.remove(p.uuid());
      return;
    }

    ThreadLocalRandom random = ThreadLocalRandom.current();
    Loc center = p.location();
    GrowthPulse pulse = new GrowthPulse(p, sampleCount);
    for (int i = 0; i < sampleCount; i++) {
      double angle = Math.toRadians(random.nextDouble(360D));
      Vec3 offset = new Vec3(Math.sin(angle), 0D, Math.cos(angle));
      offset = offset.withY(random.nextInt(-1, 2));
      offset = offset.multiply(radius <= 0D ? 0D : random.nextDouble(radius));
      Loc target = center.add(offset);
      GrowthSample sample = new GrowthSample(target, plan.strength(), plan.foodCost(), plan.surfaceOnly(), pulse);
      if (!offerLocationSample(sample)) {
        completeGrowthSample(pulse, 0);
      }
    }
  }

  private boolean offerLocationSample(GrowthSample sample) {
    int queued = queuedSamples.incrementAndGet();
    if (queued > MAX_QUEUED_SAMPLES) {
      queuedSamples.decrementAndGet();
      return false;
    }
    locationSamples.add(sample);
    return true;
  }

  private void processLocationSamples() {
    int attempts = workFor(queuedSamples.get(), LOCATION_SAMPLES_PER_TICK);
    for (int i = 0; i < attempts; i++) {
      GrowthSample sample = locationSamples.poll();
      if (sample == null) {
        break;
      }
      queuedSamples.decrementAndGet();
      if (!Tasks.runAt(sample.location, () -> inspectLocationSample(sample))) {
        completeGrowthSample(sample.pulse, 0);
      }
    }
  }

  private void inspectLocationSample(GrowthSample sample) {
    BlockRef block = sample.location.world().blockAt(sample.location);
    if (sample.surfaceOnly && block.world().highestBlockY(sample.location.blockX(), sample.location.blockZ()) + 1 != block.y()) {
      completeGrowthSample(sample.pulse, 0);
      return;
    }
    BlockStateRef ageable = block.state();
    if (ageable.age() < 0) {
      completeGrowthSample(sample.pulse, 0);
      return;
    }

    int remainingAge = ageable.maxAge() - ageable.age();
    if (remainingAge <= 0) {
      completeGrowthSample(sample.pulse, 0);
      return;
    }
    int increments = (int) Math.max(1D, Math.min(sample.strength, remainingAge));
    long dueAt = System.currentTimeMillis() + ThreadLocalRandom.current().nextLong(1500L, 3001L);
    if (!offerMutation(new GrowthMutation(sample.location, increments, sample.foodCost, dueAt, sample.pulse))) {
      completeGrowthSample(sample.pulse, 0);
    }
  }

  private boolean offerMutation(GrowthMutation mutation) {
    int queued = queuedMutations.incrementAndGet();
    if (queued > MAX_QUEUED_MUTATIONS) {
      queuedMutations.decrementAndGet();
      return false;
    }
    mutations.add(mutation);
    return true;
  }

  private void processMutations(long now) {
    int attempts = workFor(queuedMutations.get(), MUTATIONS_PER_TICK);
    for (int i = 0; i < attempts; i++) {
      GrowthMutation mutation = mutations.peek();
      if (mutation == null || mutation.dueAt > now) {
        break;
      }
      mutations.poll();
      queuedMutations.decrementAndGet();
      if (!Tasks.runAt(mutation.location, () -> applyMutation(mutation))) {
        completeGrowthSample(mutation.pulse, 0);
      }
    }
  }

  private void applyMutation(GrowthMutation mutation) {
    PlayerRef p = mutation.pulse.player;
    if ((Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(p)) || !p.isOnline()) {
      completeGrowthSample(mutation.pulse, 0);
      return;
    }

    AdaptPlayer adaptPlayer = owner.getPlayer(p);
    if (adaptPlayer == null) {
      completeGrowthSample(mutation.pulse, 0);
      return;
    }

    BlockRef block = mutation.location.world().blockAt(mutation.location);
    if (!planner.grows(p, block.type())) {
      completeGrowthSample(mutation.pulse, 0);
      return;
    }
    BlockStateRef ageable = block.state();
    if (ageable.age() < 0 || ageable.age() >= ageable.maxAge() || !mayGrow(owner, p, block)) {
      completeGrowthSample(mutation.pulse, 0);
      return;
    }

    int grown = 0;
    while (grown < mutation.increments) {
      BlockStateRef current = block.state();
      if (current.age() < 0
          || current.age() >= current.maxAge()
          || !owner.payHungerCost(p, "hunger", (int) Math.ceil(mutation.foodCost),
          () -> adaptPlayer.consumeFood(mutation.foodCost, planner.foodFloor(p)))) {
        break;
      }
      block.setState(current.with("age", Integer.toString(current.age() + 1)), true);
      grown++;
    }

    if (grown <= 0) {
      completeGrowthSample(mutation.pulse, 0);
      return;
    }
    XpProvenance.recordBonemeal(block);
    BlockStateRef finalData = block.state();
    HerbalismGrowthAura.growFx(owner, block, finalData.age() >= 0 && finalData.age() >= finalData.maxAge());
    completeGrowthSample(mutation.pulse, grown);
  }

  private void completeGrowthSample(GrowthPulse pulse, int grown) {
    if (grown > 0) {
      onGrown.accept(pulse.player, grown);
    }
    if (pulse.remaining.decrementAndGet() != 0) {
      return;
    }
    int queued = queuedCompletions.incrementAndGet();
    if (queued > MAX_QUEUED_COMPLETIONS) {
      queuedCompletions.decrementAndGet();
      pendingPulses.remove(pulse.player.uuid());
      return;
    }
    completedPulses.add(pulse);
  }

  private void processCompletions() {
    int attempts = workFor(queuedCompletions.get(), COMPLETIONS_PER_TICK);
    for (int i = 0; i < attempts; i++) {
      GrowthPulse pulse = completedPulses.poll();
      if (pulse == null) {
        break;
      }
      queuedCompletions.decrementAndGet();
      if (!Tasks.runEntity(pulse.player, () -> pendingPulses.remove(pulse.player.uuid()))) {
        pendingPulses.remove(pulse.player.uuid());
      }
    }
  }

  private void queuePlayer(UUID id) {
    if (queuedPlayers.add(id)) {
      playerQueue.add(id);
    }
  }

  private static final class GrowthPulse {
    private final PlayerRef player;
    private final AtomicInteger remaining;

    private GrowthPulse(PlayerRef player, int samples) {
      this.player = player;
      this.remaining = new AtomicInteger(samples);
    }
  }

  private record GrowthSample(Loc location, double strength, double foodCost, boolean surfaceOnly, GrowthPulse pulse) {
  }

  private record GrowthMutation(Loc location, int increments, double foodCost, long dueAt, GrowthPulse pulse)
      implements Comparable<GrowthMutation> {
    @Override
    public int compareTo(GrowthMutation other) {
      return Long.compare(dueAt, other.dueAt);
    }
  }
}
