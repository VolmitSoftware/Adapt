package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

public final class AdaptationOwnerPulse extends TickedObject {
  public static final int MAX_OWNER_TASKS_PER_TICK = 64;
  public static final int MAX_OWNER_EXAMINATIONS_PER_TICK = 200;
  private static final long COORDINATOR_INTERVAL_MILLIS = 50L;
  private static final Object INSTANCE_LOCK = new Object();
  private static final ThreadLocal<RegistrationBatch> REGISTRATION_BATCH = new ThreadLocal<>();
  private static AdaptationOwnerPulse instance;

  private final CopyOnWriteArrayList<Participant> participants = new CopyOnWriteArrayList<>();
  private final Set<String> pendingPlayers = ConcurrentHashMap.newKeySet();
  private int playerCursor;

  private AdaptationOwnerPulse() {
    super("adaptations", "owner-maintenance-pulse", COORDINATOR_INTERVAL_MILLIS);
  }

  public static Registration register(
      SimpleAdaptation<?> adaptation,
      LongSupplier cadenceSupplier,
      OwnerMaintenance maintenance
  ) {
    return register(
        adaptation,
        cadenceSupplier,
        () -> false,
        playerId -> adaptation.getServer().hasOnlineLearner(playerId, adaptation.getName()),
        maintenance
    );
  }

  public static Registration register(
      SimpleAdaptation<?> adaptation,
      LongSupplier cadenceSupplier,
      BooleanSupplier supplementalDemand,
      OwnerInterest interest,
      OwnerMaintenance maintenance
  ) {
    Objects.requireNonNull(adaptation);
    Objects.requireNonNull(cadenceSupplier);
    Objects.requireNonNull(supplementalDemand);
    Objects.requireNonNull(interest);
    Objects.requireNonNull(maintenance);

    synchronized (INSTANCE_LOCK) {
      AdaptationOwnerPulse coordinator = instance;
      if (coordinator == null) {
        coordinator = new AdaptationOwnerPulse();
        instance = coordinator;
      }

      ParticipantSpec spec = new ParticipantSpec(
          adaptation,
          cadenceSupplier,
          supplementalDemand,
          interest,
          maintenance
      );
      Participant participant = new Participant(spec);
      Registration registration = new Registration(coordinator, participant);
      RegistrationBatch batch = REGISTRATION_BATCH.get();
      if (batch == null) {
        registration.commit();
      } else {
        batch.capture(registration);
      }
      return registration;
    }
  }

  public static RegistrationBatch beginRegistrationBatch() {
    if (REGISTRATION_BATCH.get() != null) {
      throw new IllegalStateException("Adaptation owner-pulse registration batch already active");
    }
    RegistrationBatch batch = new RegistrationBatch(Thread.currentThread());
    REGISTRATION_BATCH.set(batch);
    return batch;
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
    for (Participant participant : participants) {
      if (participant.hasDemand()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void onTick() {
    if (participants.isEmpty()) {
      return;
    }

    List<AdaptPlayer> players = getServer().getOnlineAdaptPlayerSnapshot();
    int playerCount = players.size();
    if (playerCount == 0) {
      playerCursor = 0;
      return;
    }

    int start = Math.floorMod(playerCursor, playerCount);
    int taskBudget = boundedBatchSize(playerCount);
    int examinationBudget = boundedExaminationSize(playerCount);
    int examined = 0;
    int attemptedTasks = 0;
    long now = System.currentTimeMillis();
    while (examined < examinationBudget && attemptedTasks < taskBudget) {
      AdaptPlayer adaptPlayer = players.get((start + examined) % playerCount);
      if (scheduleIfDue(adaptPlayer, now)) {
        attemptedTasks++;
      }
      examined++;
    }
    playerCursor = advanceCursor(start, examined, playerCount);
  }

  @EventHandler
  public void on(PlayerQuitEvent event) {
    clearPlayer(event.getPlayer().getUniqueId().toString());
  }

  @Override
  public void unregister() {
    synchronized (INSTANCE_LOCK) {
      if (instance == this) {
        instance = null;
      }
    }
    for (Participant participant : participants) {
      participant.deactivate();
    }
    participants.clear();
    pendingPlayers.clear();
    super.unregister();
  }

  static int boundedBatchSize(int playerCount) {
    return Math.max(0, Math.min(playerCount, MAX_OWNER_TASKS_PER_TICK));
  }

  static int boundedExaminationSize(int playerCount) {
    return Math.max(0, Math.min(playerCount, MAX_OWNER_EXAMINATIONS_PER_TICK));
  }

  static int advanceCursor(int start, int visited, int playerCount) {
    if (playerCount <= 0) {
      return 0;
    }
    return Math.floorMod(start + Math.max(0, visited), playerCount);
  }

  static boolean cadenceDue(Long lastPulse, long now, long cadence) {
    if (lastPulse == null) {
      return true;
    }
    long safeCadence = Math.max(MIN_INTERVAL_MILLIS, cadence);
    return now >= lastPulse && now - lastPulse >= safeCadence;
  }

  static int registrationCount() {
    synchronized (INSTANCE_LOCK) {
      return instance == null ? 0 : instance.participants.size();
    }
  }

  private boolean scheduleIfDue(AdaptPlayer adaptPlayer, long now) {
    if (adaptPlayer == null || !adaptPlayer.isRuntimeReady()) {
      return false;
    }

    String playerKey = adaptPlayer.getId();
    if (playerKey == null || pendingPlayers.contains(playerKey)) {
      return false;
    }

    Player player = adaptPlayer.getPlayer();
    if (player == null) {
      return false;
    }
    UUID playerId = player.getUniqueId();
    if (!hasDueParticipant(playerId, playerKey, now) || !pendingPlayers.add(playerKey)) {
      return false;
    }

    boolean scheduled = J.runEntity(player, () -> pulsePlayer(adaptPlayer, player, playerId, playerKey));
    if (!scheduled) {
      pendingPlayers.remove(playerKey);
    }
    return true;
  }

  private boolean hasDueParticipant(UUID playerId, String playerKey, long now) {
    for (Participant participant : participants) {
      if (participant.isDue(playerId, playerKey, now)) {
        return true;
      }
    }
    return false;
  }

  private void pulsePlayer(AdaptPlayer adaptPlayer, Player player, UUID playerId, String playerKey) {
    try {
      if (!isRuntimeRegistered() || !adaptPlayer.isRuntimeReady() || !player.isOnline()) {
        return;
      }

      long now = System.currentTimeMillis();
      for (Participant participant : participants) {
        participant.pulse(player, playerId, playerKey, now);
      }
    } finally {
      pendingPlayers.remove(playerKey);
    }
  }

  private void clearPlayer(String playerKey) {
    pendingPlayers.remove(playerKey);
    for (Participant participant : participants) {
      participant.clear(playerKey);
    }
  }

  private void removeExistingRegistration(String adaptationName) {
    for (Participant participant : participants) {
      if (participant.hasAdaptationName(adaptationName)) {
        participant.deactivate();
        participants.remove(participant);
      }
    }
  }

  private void unregister(Participant participant) {
    participant.deactivate();
    participants.remove(participant);
    unregisterIfEmpty();
  }

  private void unregisterIfEmpty() {
    if (!participants.isEmpty()) {
      return;
    }

    synchronized (INSTANCE_LOCK) {
      if (participants.isEmpty() && instance == this) {
        unregister();
      }
    }
  }

  @FunctionalInterface
  public interface OwnerInterest {
    boolean isInterested(UUID playerId);
  }

  @FunctionalInterface
  public interface OwnerMaintenance {
    void maintain(Player player);
  }

  public static final class Registration {
    private final AdaptationOwnerPulse coordinator;
    private final Participant participant;
    private boolean registered = true;
    private boolean committed;

    private Registration(AdaptationOwnerPulse coordinator, Participant participant) {
      this.coordinator = coordinator;
      this.participant = participant;
    }

    public void unregister() {
      synchronized (INSTANCE_LOCK) {
        if (!registered) {
          return;
        }
        registered = false;
        participant.deactivate();
        if (committed) {
          coordinator.unregister(participant);
        }
      }
    }

    private void commit() {
      synchronized (INSTANCE_LOCK) {
        if (!registered || committed) {
          return;
        }
        coordinator.removeExistingRegistration(adaptationName());
        coordinator.participants.add(participant);
        committed = true;
      }
    }

    private void rollback() {
      synchronized (INSTANCE_LOCK) {
        if (!registered) {
          return;
        }
        registered = false;
        participant.deactivate();
        if (committed) {
          coordinator.unregister(participant);
        }
      }
    }

    private boolean hasAdaptationName(String adaptationName) {
      return participant.hasAdaptationName(adaptationName);
    }

    private String adaptationName() {
      return participant.adaptation.getName();
    }
  }

  public static final class RegistrationBatch {
    private final Thread owner;
    private final List<Registration> registrations = new ArrayList<>();
    private boolean captureOpen = true;
    private boolean resolved;

    private RegistrationBatch(Thread owner) {
      this.owner = owner;
    }

    public void endCapture() {
      requireOwner();
      if (!captureOpen) {
        return;
      }
      if (REGISTRATION_BATCH.get() != this) {
        throw new IllegalStateException("Adaptation owner-pulse registration batch is not current");
      }
      REGISTRATION_BATCH.remove();
      captureOpen = false;
    }

    public void commit() {
      endCapture();
      if (resolved) {
        return;
      }
      resolved = true;
      for (Registration registration : registrations) {
        registration.commit();
      }
      releaseEmptyCoordinator();
    }

    public void rollback() {
      endCapture();
      if (resolved) {
        return;
      }
      resolved = true;
      for (Registration registration : registrations) {
        registration.rollback();
      }
      releaseEmptyCoordinator();
    }

    private void capture(Registration registration) {
      requireOwner();
      if (!captureOpen || resolved || REGISTRATION_BATCH.get() != this) {
        throw new IllegalStateException("Adaptation owner-pulse registration batch is closed");
      }
      for (int index = registrations.size() - 1; index >= 0; index--) {
        Registration existing = registrations.get(index);
        if (existing.hasAdaptationName(registration.adaptationName())) {
          registrations.remove(index);
          existing.rollback();
        }
      }
      registrations.add(registration);
    }

    private void releaseEmptyCoordinator() {
      if (!registrations.isEmpty()) {
        registrations.get(0).coordinator.unregisterIfEmpty();
      }
    }

    private void requireOwner() {
      if (Thread.currentThread() != owner) {
        throw new IllegalStateException("Adaptation owner-pulse registration batch changed threads");
      }
    }
  }

  private record ParticipantSpec(
      SimpleAdaptation<?> adaptation,
      LongSupplier cadenceSupplier,
      BooleanSupplier supplementalDemand,
      OwnerInterest interest,
      OwnerMaintenance maintenance
  ) {
  }

  private static final class Participant {
    private final SimpleAdaptation<?> adaptation;
    private final LongSupplier cadenceSupplier;
    private final BooleanSupplier supplementalDemand;
    private final OwnerInterest interest;
    private final OwnerMaintenance maintenance;
    private final Map<String, Long> lastPulseByPlayer = new ConcurrentHashMap<>();
    private final AtomicBoolean active = new AtomicBoolean(true);

    private Participant(ParticipantSpec spec) {
      adaptation = spec.adaptation();
      cadenceSupplier = spec.cadenceSupplier();
      supplementalDemand = spec.supplementalDemand();
      interest = spec.interest();
      maintenance = spec.maintenance();
    }

    private boolean isDue(UUID playerId, String playerKey, long now) {
      if (!isAvailable() || !interest.isInterested(playerId)) {
        lastPulseByPlayer.remove(playerKey);
        return false;
      }

      Long lastPulse = lastPulseByPlayer.get(playerKey);
      return cadenceDue(lastPulse, now, cadence());
    }

    private boolean hasDemand() {
      return isAvailable()
          && (supplementalDemand.getAsBoolean()
          || adaptation.getServer().hasOnlineLearner(adaptation.getName())
          || !lastPulseByPlayer.isEmpty());
    }

    private void pulse(Player player, UUID playerId, String playerKey, long now) {
      if (!isAvailable() || !interest.isInterested(playerId)) {
        lastPulseByPlayer.remove(playerKey);
        return;
      }

      long cadence = cadence();
      Long lastPulse = lastPulseByPlayer.putIfAbsent(playerKey, now);
      if (lastPulse == null) {
        runMaintenance(player, playerKey);
        return;
      }

      long elapsed = now - lastPulse;
      if (elapsed < cadence || !lastPulseByPlayer.replace(playerKey, lastPulse, now)) {
        return;
      }
      runMaintenance(player, playerKey);
    }

    private boolean isAvailable() {
      return active.get() && adaptation.isRuntimeRegistered() && adaptation.isEnabled();
    }

    private void runMaintenance(Player player, String playerKey) {
      try {
        maintenance.maintain(player);
      } catch (Throwable error) {
        Adapt.error("Exception maintaining adaptation " + adaptation.getName() + " for " + playerKey);
        Adapt.error(error);
      }
    }

    private long cadence() {
      return Math.max(MIN_INTERVAL_MILLIS, cadenceSupplier.getAsLong());
    }

    private void clear(String playerKey) {
      lastPulseByPlayer.remove(playerKey);
    }

    private boolean hasAdaptationName(String adaptationName) {
      return adaptation.getName().equals(adaptationName);
    }

    private void deactivate() {
      active.set(false);
      lastPulseByPlayer.clear();
    }
  }
}
