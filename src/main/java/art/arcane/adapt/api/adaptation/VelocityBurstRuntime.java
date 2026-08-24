/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.util.common.math.VelocitySpeed;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class VelocityBurstRuntime extends TickedObject {
  public static final int MAX_OWNER_CALLBACKS_PER_TICK = 320;
  public static final int MAX_OWNER_CALLBACKS_PER_SECOND = MAX_OWNER_CALLBACKS_PER_TICK * 20;
  public static final int MAX_SESSIONS_PER_PLAYER = 8;
  public static final int MAX_ELAPSED_TICKS = 4;
  private static final long UPDATE_INTERVAL_MILLIS = 50L;
  private static final Object INSTANCE_LOCK = new Object();
  private static VelocityBurstRuntime instance;

  private final Map<UUID, PlayerBucket> buckets = new ConcurrentHashMap<>();
  private final Queue<PlayerBucket> readyBuckets = new ConcurrentLinkedQueue<>();
  private final Set<Client> clients = ConcurrentHashMap.newKeySet();
  private long dispatchCycle;

  private VelocityBurstRuntime() {
    super("adaptations", "velocity-burst-runtime", UPDATE_INTERVAL_MILLIS);
  }

  public static Client register(String sourceName, Feedback feedback) {
    Objects.requireNonNull(sourceName);
    Objects.requireNonNull(feedback);

    synchronized (INSTANCE_LOCK) {
      VelocityBurstRuntime runtime = instance;
      if (runtime == null) {
        runtime = new VelocityBurstRuntime();
        instance = runtime;
      }

      Client client = new Client(runtime, sourceName, feedback);
      runtime.clients.add(client);
      return client;
    }
  }

  public static void startRuntime() {
    synchronized (INSTANCE_LOCK) {
      if (instance != null) {
        instance.activateRuntime();
      }
    }
  }

  @Override
  public boolean hasTickDemand() {
    return !buckets.isEmpty() || !readyBuckets.isEmpty();
  }

  @Override
  public void onTick() {
    long cycle = ++dispatchCycle;
    int scheduled = 0;
    while (scheduled < MAX_OWNER_CALLBACKS_PER_TICK) {
      PlayerBucket bucket = readyBuckets.poll();
      if (bucket == null) {
        return;
      }
      if (bucket.lastDispatchCycle == cycle) {
        readyBuckets.offer(bucket);
        return;
      }

      bucket.lastDispatchCycle = cycle;
      if (!isCurrentWithSessions(bucket)) {
        removeBucketIfEmpty(bucket);
        completeDispatch(bucket);
        continue;
      }

      boolean accepted = J.runEntity(bucket.player, () -> updateBucket(bucket));
      if (accepted) {
        scheduled++;
      } else {
        completeDispatch(bucket);
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent event) {
    clearPlayer(event.getEntity().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    clearPlayer(event.getPlayer().getUniqueId());
  }

  @Override
  public void unregister() {
    synchronized (INSTANCE_LOCK) {
      if (instance == this) {
        instance = null;
      }
      for (Client client : clients) {
        synchronized (client) {
          client.active.set(false);
          client.sessions.clear();
        }
      }
      clients.clear();
    }

    buckets.clear();
    readyBuckets.clear();
    super.unregister();
  }

  static int boundedOwnerCallbacks(int activePlayers) {
    return Math.max(0, Math.min(activePlayers, MAX_OWNER_CALLBACKS_PER_TICK));
  }

  static double elapsedTickScale(long elapsedMillis) {
    double elapsedTicks = Math.max(UPDATE_INTERVAL_MILLIS, elapsedMillis) / (double) UPDATE_INTERVAL_MILLIS;
    return Math.min(MAX_ELAPSED_TICKS, elapsedTicks);
  }

  static StartDecision decideStart(boolean hasExisting, long existingExpiry, int existingAmplifier,
                                   long now, long durationMillis, int requestedAmplifier, boolean overlap) {
    long safeDuration = Math.max(UPDATE_INTERVAL_MILLIS, durationMillis);
    if (hasExisting && existingExpiry > now) {
      if (!overlap) {
        return new StartDecision(StartResult.REJECTED_ACTIVE, existingExpiry, existingAmplifier);
      }
      return new StartDecision(
          StartResult.EXTENDED,
          saturatedAdd(existingExpiry, safeDuration),
          Math.max(existingAmplifier, requestedAmplifier)
      );
    }
    return new StartDecision(StartResult.STARTED, saturatedAdd(now, safeDuration), requestedAmplifier);
  }

  private static long saturatedAdd(long left, long right) {
    if (right > 0L && left > Long.MAX_VALUE - right) {
      return Long.MAX_VALUE;
    }
    return left + right;
  }

  private StartResult start(Client client, Player player, BurstRequest request) {
    if (request.durationTicks() <= 0) {
      return StartResult.REJECTED_DURATION;
    }

    synchronized (client) {
      if (!client.active.get() || !isRuntimeRegistered()) {
        return StartResult.REJECTED_INACTIVE;
      }

      UUID playerId = player.getUniqueId();
      long now = System.currentTimeMillis();
      long durationMillis = Math.max(UPDATE_INTERVAL_MILLIS, (long) request.durationTicks() * 50L);
      while (true) {
        PlayerBucket bucket = buckets.computeIfAbsent(playerId, ignored -> new PlayerBucket(playerId, player, now));
        StartResult result;
        BurstSession startedSession = null;
        synchronized (bucket) {
          if (buckets.get(playerId) != bucket) {
            continue;
          }
          if (!client.active.get()) {
            return StartResult.REJECTED_INACTIVE;
          }

          BurstSession existing = bucket.sessions.get(client);
          StartDecision decision = decideStart(
              existing != null,
              existing == null ? 0L : existing.expiresAt,
              existing == null ? 0 : existing.amplifier,
              now,
              durationMillis,
              request.amplifier(),
              request.overlap()
          );
          result = decision.result();
          if (!result.accepted()) {
            return result;
          }

          if (result == StartResult.EXTENDED) {
            existing.expiresAt = decision.expiresAt();
            existing.amplifier = decision.amplifier();
            existing.profile = request.profile();
          } else {
            BurstSession replacement = new BurstSession(client, decision.expiresAt(), decision.amplifier(), request.profile());
            if (!bucket.sessions.put(client, replacement)) {
              return StartResult.REJECTED_CAPACITY;
            }
            if (existing != null) {
              client.sessions.remove(playerId, existing);
            }
            client.sessions.put(playerId, replacement);
            startedSession = replacement;
          }
        }

        enqueue(bucket);
        if (startedSession != null && client.sessions.get(playerId) == startedSession) {
          notifyStarted(startedSession, player);
        }
        return result;
      }
    }
  }

  private void updateBucket(PlayerBucket bucket) {
    try {
      if (buckets.get(bucket.playerId) != bucket) {
        return;
      }
      Player player = bucket.player;
      if (!player.isOnline()) {
        clearPlayer(bucket.playerId);
        return;
      }

      long now = System.currentTimeMillis();
      synchronized (bucket) {
        if (buckets.get(bucket.playerId) != bucket) {
          return;
        }

        long elapsed = now >= bucket.lastUpdateMillis ? now - bucket.lastUpdateMillis : UPDATE_INTERVAL_MILLIS;
        bucket.lastUpdateMillis = now;
        removeExpiredSessions(bucket, player, now);
        if (bucket.sessions.isEmpty()) {
          return;
        }

        double tickScale = elapsedTickScale(elapsed);
        if (!isVelocityEligible(player)) {
          invalidateSessions(bucket, player);
          return;
        }

        double fallbackThresholdSquared = minimumFallbackThresholdSquared(bucket);
        VelocitySpeed.InputSnapshot input = VelocitySpeed.readInput(player, fallbackThresholdSquared);
        if (!input.hasHorizontal()) {
          brakeSessions(bucket, player, tickScale);
          return;
        }

        Vector direction = VelocitySpeed.resolveHorizontalDirection(player, input);
        if (direction.lengthSquared() <= VelocitySpeed.EPSILON) {
          brakeSessions(bucket, player, tickScale);
          return;
        }

        accelerateSessions(bucket, player, direction, tickScale);
      }
    } catch (Throwable error) {
      Adapt.error("Exception updating velocity bursts for " + bucket.playerId);
      Adapt.error(error);
    } finally {
      removeBucketIfEmpty(bucket);
      completeDispatch(bucket);
    }
  }

  private void removeExpiredSessions(PlayerBucket bucket, Player player, long now) {
    Iterator<Map.Entry<Client, BurstSession>> iterator = bucket.sessions.entries().iterator();
    while (iterator.hasNext()) {
      Map.Entry<Client, BurstSession> entry = iterator.next();
      Client client = entry.getKey();
      BurstSession session = entry.getValue();
      if (!client.active.get() || client.sessions.get(bucket.playerId) != session) {
        iterator.remove();
        continue;
      }
      if (session.expiresAt > now) {
        continue;
      }

      iterator.remove();
      client.sessions.remove(bucket.playerId, session);
      notifyEnded(session, player);
    }
  }

  private void invalidateSessions(PlayerBucket bucket, Player player) {
    boolean hardStop = false;
    for (BurstSession session : bucket.sessions.values()) {
      if (!session.boosting) {
        continue;
      }
      hardStop |= session.profile.hardStopOnInvalidState();
      session.boosting = false;
    }
    if (hardStop) {
      VelocitySpeed.hardStopHorizontal(player);
    }
  }

  private double minimumFallbackThresholdSquared(PlayerBucket bucket) {
    double threshold = Double.POSITIVE_INFINITY;
    for (BurstSession session : bucket.sessions.values()) {
      threshold = Math.min(threshold, session.profile.fallbackInputVelocityThresholdSquared());
    }
    return threshold == Double.POSITIVE_INFINITY ? 0D : threshold;
  }

  private void accelerateSessions(PlayerBucket bucket, Player player, Vector direction, double tickScale) {
    BurstSession controller = null;
    double targetSpeed = -1D;
    for (BurstSession session : bucket.sessions.values()) {
      double candidate = Math.min(
          session.profile.maxHorizontalSpeed(),
          session.profile.baseHorizontalSpeed() * VelocitySpeed.speedAmplifierScalar(session.amplifier)
      );
      if (candidate > targetSpeed) {
        targetSpeed = candidate;
        controller = session;
      }
    }
    if (controller == null) {
      return;
    }

    Vector horizontal = VelocitySpeed.horizontalOnly(player.getVelocity());
    Vector targetHorizontal = direction.multiply(Math.max(0D, targetSpeed));
    double acceleration = controller.profile.accelerationPerTick() * tickScale;
    Vector nextHorizontal = VelocitySpeed.moveTowards(horizontal, targetHorizontal, acceleration);
    nextHorizontal = VelocitySpeed.clampHorizontal(nextHorizontal, controller.profile.maxHorizontalSpeed());
    VelocitySpeed.setHorizontalVelocity(player, nextHorizontal);

    for (BurstSession session : bucket.sessions.values()) {
      session.boosting = true;
      notifyTrail(session, player, nextHorizontal, session.updateIndex++);
    }
  }

  private void brakeSessions(PlayerBucket bucket, Player player, double tickScale) {
    boolean active = false;
    double brake = 0D;
    double stopThreshold = 0D;
    for (BurstSession session : bucket.sessions.values()) {
      if (!session.boosting) {
        continue;
      }
      active = true;
      brake = Math.max(brake, session.profile.brakePerTick());
      stopThreshold = Math.max(stopThreshold, session.profile.stopThreshold());
    }
    if (!active) {
      return;
    }

    Vector horizontal = VelocitySpeed.horizontalOnly(player.getVelocity());
    double stopThresholdSquared = stopThreshold * stopThreshold;
    if (horizontal.lengthSquared() <= stopThresholdSquared) {
      finishBraking(bucket, player);
      return;
    }

    Vector nextHorizontal = VelocitySpeed.moveTowards(horizontal, new Vector(), brake * tickScale);
    if (nextHorizontal.lengthSquared() <= stopThresholdSquared) {
      finishBraking(bucket, player);
      return;
    }
    VelocitySpeed.setHorizontalVelocity(player, nextHorizontal);
  }

  private void finishBraking(PlayerBucket bucket, Player player) {
    VelocitySpeed.hardStopHorizontal(player);
    for (BurstSession session : bucket.sessions.values()) {
      if (!session.boosting) {
        continue;
      }
      session.boosting = false;
      notifyBraked(session, player);
    }
  }

  private boolean isVelocityEligible(Player player) {
    GameMode mode = player.getGameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      return false;
    }
    return !player.isDead() && !player.isFlying() && !player.isGliding()
        && !player.isSwimming() && player.getVehicle() == null;
  }

  private void unregisterClient(Client client) {
    synchronized (client) {
      if (!client.active.compareAndSet(true, false)) {
        return;
      }
      for (Map.Entry<UUID, BurstSession> entry : client.sessions.entrySet()) {
        removeSession(client, entry.getKey(), entry.getValue());
      }
      client.sessions.clear();
    }

    synchronized (INSTANCE_LOCK) {
      clients.remove(client);
      if (clients.isEmpty() && instance == this) {
        unregister();
      }
    }
  }

  private void removeSession(Client client, UUID playerId, BurstSession expected) {
    PlayerBucket bucket = buckets.get(playerId);
    if (bucket == null) {
      return;
    }
    synchronized (bucket) {
      bucket.sessions.remove(client, expected);
      if (bucket.sessions.isEmpty()) {
        buckets.remove(playerId, bucket);
      }
    }
  }

  private void clearPlayer(UUID playerId) {
    PlayerBucket bucket = buckets.remove(playerId);
    if (bucket == null) {
      return;
    }

    synchronized (bucket) {
      for (Map.Entry<Client, BurstSession> entry : bucket.sessions.entries()) {
        entry.getKey().sessions.remove(playerId, entry.getValue());
      }
      bucket.sessions.clear();
    }
  }

  private boolean isCurrentWithSessions(PlayerBucket bucket) {
    if (buckets.get(bucket.playerId) != bucket) {
      return false;
    }
    synchronized (bucket) {
      return !bucket.sessions.isEmpty();
    }
  }

  private void removeBucketIfEmpty(PlayerBucket bucket) {
    synchronized (bucket) {
      if (bucket.sessions.isEmpty()) {
        buckets.remove(bucket.playerId, bucket);
      }
    }
  }

  private void completeDispatch(PlayerBucket bucket) {
    bucket.queuedOrPending.set(false);
    if (isCurrentWithSessions(bucket)) {
      enqueue(bucket);
    }
  }

  private void enqueue(PlayerBucket bucket) {
    if (bucket.queuedOrPending.compareAndSet(false, true)) {
      readyBuckets.offer(bucket);
    }
  }

  private void notifyStarted(BurstSession session, Player player) {
    try {
      session.client.feedback.onStarted(player);
    } catch (Throwable error) {
      reportFeedbackFailure(session.client, error);
    }
  }

  private void notifyTrail(BurstSession session, Player player, Vector horizontal, int updateIndex) {
    try {
      session.client.feedback.onTrail(player, horizontal, updateIndex);
    } catch (Throwable error) {
      reportFeedbackFailure(session.client, error);
    }
  }

  private void notifyBraked(BurstSession session, Player player) {
    try {
      session.client.feedback.onBraked(player);
    } catch (Throwable error) {
      reportFeedbackFailure(session.client, error);
    }
  }

  private void notifyEnded(BurstSession session, Player player) {
    try {
      session.client.feedback.onEnded(player);
    } catch (Throwable error) {
      reportFeedbackFailure(session.client, error);
    }
  }

  private void reportFeedbackFailure(Client client, Throwable error) {
    Adapt.error("Exception rendering velocity burst feedback for " + client.sourceName);
    Adapt.error(error);
  }

  public enum StartResult {
    STARTED(true),
    EXTENDED(true),
    REJECTED_ACTIVE(false),
    REJECTED_CAPACITY(false),
    REJECTED_DURATION(false),
    REJECTED_INACTIVE(false);

    private final boolean accepted;

    StartResult(boolean accepted) {
      this.accepted = accepted;
    }

    public boolean accepted() {
      return accepted;
    }
  }

  public record Profile(double baseHorizontalSpeed, double maxHorizontalSpeed,
                        double accelerationPerTick, double brakePerTick,
                        double stopThreshold, boolean hardStopOnInvalidState,
                        double fallbackInputVelocityThreshold) {
    public Profile {
      baseHorizontalSpeed = finiteNonNegative(baseHorizontalSpeed);
      maxHorizontalSpeed = finiteNonNegative(maxHorizontalSpeed);
      accelerationPerTick = finiteNonNegative(accelerationPerTick);
      brakePerTick = finiteNonNegative(brakePerTick);
      stopThreshold = finiteNonNegative(stopThreshold);
      fallbackInputVelocityThreshold = finiteNonNegative(fallbackInputVelocityThreshold);
    }

    double fallbackInputVelocityThresholdSquared() {
      return fallbackInputVelocityThreshold * fallbackInputVelocityThreshold;
    }

    private static double finiteNonNegative(double value) {
      return Double.isFinite(value) ? Math.max(0D, value) : 0D;
    }
  }

  public record BurstRequest(int amplifier, int durationTicks, boolean overlap, Profile profile) {
    public BurstRequest {
      Objects.requireNonNull(profile);
    }
  }

  public interface Feedback {
    default void onStarted(Player player) {
    }

    default void onTrail(Player player, Vector horizontal, int updateIndex) {
    }

    default void onBraked(Player player) {
    }

    default void onEnded(Player player) {
    }
  }

  public static final class Client {
    private final VelocityBurstRuntime runtime;
    private final String sourceName;
    private final Feedback feedback;
    private final Map<UUID, BurstSession> sessions = new ConcurrentHashMap<>();
    private final AtomicBoolean active = new AtomicBoolean(true);

    private Client(VelocityBurstRuntime runtime, String sourceName, Feedback feedback) {
      this.runtime = runtime;
      this.sourceName = sourceName;
      this.feedback = feedback;
    }

    public StartResult start(Player player, BurstRequest request) {
      Objects.requireNonNull(player);
      Objects.requireNonNull(request);
      return runtime.start(this, player, request);
    }

    public void unregister() {
      runtime.unregisterClient(this);
    }
  }

  static final class SessionLedger<O, S> {
    private final int capacity;
    private final IdentityHashMap<O, S> sessions = new IdentityHashMap<>();

    SessionLedger(int capacity) {
      this.capacity = Math.max(1, capacity);
    }

    S get(O owner) {
      return sessions.get(owner);
    }

    boolean put(O owner, S session) {
      if (!sessions.containsKey(owner) && sessions.size() >= capacity) {
        return false;
      }
      sessions.put(owner, session);
      return true;
    }

    boolean remove(O owner, S expected) {
      if (sessions.get(owner) != expected) {
        return false;
      }
      sessions.remove(owner);
      return true;
    }

    Set<Map.Entry<O, S>> entries() {
      return sessions.entrySet();
    }

    Iterable<S> values() {
      return sessions.values();
    }

    boolean isEmpty() {
      return sessions.isEmpty();
    }

    int size() {
      return sessions.size();
    }

    void clear() {
      sessions.clear();
    }
  }

  record StartDecision(StartResult result, long expiresAt, int amplifier) {
  }

  private static final class BurstSession {
    private final Client client;
    private long expiresAt;
    private int amplifier;
    private Profile profile;
    private boolean boosting;
    private int updateIndex;

    private BurstSession(Client client, long expiresAt, int amplifier, Profile profile) {
      this.client = client;
      this.expiresAt = expiresAt;
      this.amplifier = amplifier;
      this.profile = profile;
    }
  }

  private static final class PlayerBucket {
    private final UUID playerId;
    private final Player player;
    private final SessionLedger<Client, BurstSession> sessions = new SessionLedger<>(MAX_SESSIONS_PER_PLAYER);
    private final AtomicBoolean queuedOrPending = new AtomicBoolean(false);
    private long lastUpdateMillis;
    private volatile long lastDispatchCycle;

    private PlayerBucket(UUID playerId, Player player, long now) {
      this.playerId = playerId;
      this.player = player;
      this.lastUpdateMillis = now;
    }
  }
}
