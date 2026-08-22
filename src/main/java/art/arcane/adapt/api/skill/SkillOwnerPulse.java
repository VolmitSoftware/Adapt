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

package art.arcane.adapt.api.skill;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

public final class SkillOwnerPulse extends TickedObject {
  public static final int MAX_OWNER_TASKS_PER_TICK = 64;
  public static final int MAX_REWARD_INTERVALS = 4;
  private static final long COORDINATOR_INTERVAL_MILLIS = 50L;
  private static final Object INSTANCE_LOCK = new Object();
  private static SkillOwnerPulse instance;

  private final CopyOnWriteArrayList<Participant> participants = new CopyOnWriteArrayList<>();
  private final Set<String> pendingPlayers = ConcurrentHashMap.newKeySet();
  private int playerCursor;

  private SkillOwnerPulse() {
    super("skills", "owner-pulse", COORDINATOR_INTERVAL_MILLIS);
  }

  public static Registration register(SimpleSkill<?> skill, LongSupplier cadenceSupplier, PlayerPulse pulse) {
    Objects.requireNonNull(skill);
    Objects.requireNonNull(cadenceSupplier);
    Objects.requireNonNull(pulse);

    synchronized (INSTANCE_LOCK) {
      SkillOwnerPulse coordinator = instance;
      if (coordinator == null) {
        coordinator = new SkillOwnerPulse();
        instance = coordinator;
      }

      coordinator.removeExistingRegistration(skill.getName());
      Participant participant = new Participant(skill, cadenceSupplier, pulse);
      coordinator.participants.add(participant);
      return new Registration(coordinator, participant);
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
    int batchSize = boundedBatchSize(playerCount);
    long now = System.currentTimeMillis();
    for (int offset = 0; offset < batchSize; offset++) {
      AdaptPlayer adaptPlayer = players.get((start + offset) % playerCount);
      scheduleIfDue(adaptPlayer, now);
    }
    playerCursor = advanceCursor(start, batchSize, playerCount);
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

  static int advanceCursor(int start, int visited, int playerCount) {
    if (playerCount <= 0) {
      return 0;
    }
    return Math.floorMod(start + Math.max(0, visited), playerCount);
  }

  static long boundedElapsed(long elapsed, long cadence) {
    long safeCadence = Math.max(MIN_INTERVAL_MILLIS, cadence);
    long maximum = safeCadence > Long.MAX_VALUE / MAX_REWARD_INTERVALS
        ? Long.MAX_VALUE
        : safeCadence * MAX_REWARD_INTERVALS;
    return Math.max(0L, Math.min(elapsed, maximum));
  }

  static boolean cadenceDue(Long lastPulse, long now, long cadence) {
    if (lastPulse == null) {
      return true;
    }
    long safeCadence = Math.max(MIN_INTERVAL_MILLIS, cadence);
    return now >= lastPulse && now - lastPulse >= safeCadence;
  }

  private void scheduleIfDue(AdaptPlayer adaptPlayer, long now) {
    if (adaptPlayer == null || !adaptPlayer.isRuntimeReady()) {
      return;
    }

    String playerKey = adaptPlayer.getId();
    if (playerKey == null || pendingPlayers.contains(playerKey) || !hasDueParticipant(playerKey, now)) {
      return;
    }

    Player player = adaptPlayer.getPlayer();
    if (player == null || !pendingPlayers.add(playerKey)) {
      return;
    }

    boolean scheduled = J.runEntity(player, () -> pulsePlayer(adaptPlayer, player, playerKey));
    if (!scheduled) {
      pendingPlayers.remove(playerKey);
    }
  }

  private boolean hasDueParticipant(String playerKey, long now) {
    for (Participant participant : participants) {
      if (participant.isDue(playerKey, now)) {
        return true;
      }
    }
    return false;
  }

  private void pulsePlayer(AdaptPlayer adaptPlayer, Player player, String playerKey) {
    try {
      if (!isRuntimeRegistered() || !adaptPlayer.isRuntimeReady() || !player.isOnline()) {
        return;
      }

      long now = System.currentTimeMillis();
      for (Participant participant : participants) {
        participant.pulse(adaptPlayer, player, playerKey, now);
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

  private void removeExistingRegistration(String skillName) {
    for (Participant participant : participants) {
      if (participant.hasSkillName(skillName)) {
        participant.deactivate();
        participants.remove(participant);
      }
    }
  }

  private void unregister(Participant participant) {
    participant.deactivate();
    participants.remove(participant);
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
  public interface PlayerPulse {
    void pulse(AdaptPlayer adaptPlayer, Player player, long elapsedMillis, long cadenceMillis);
  }

  public static final class Registration {
    private final SkillOwnerPulse coordinator;
    private final Participant participant;
    private final AtomicBoolean registered = new AtomicBoolean(true);

    private Registration(SkillOwnerPulse coordinator, Participant participant) {
      this.coordinator = coordinator;
      this.participant = participant;
    }

    public void unregister() {
      if (registered.compareAndSet(true, false)) {
        coordinator.unregister(participant);
      }
    }
  }

  private static final class Participant {
    private final SimpleSkill<?> skill;
    private final LongSupplier cadenceSupplier;
    private final PlayerPulse pulse;
    private final Map<String, Long> lastPulseByPlayer = new ConcurrentHashMap<>();
    private final AtomicBoolean active = new AtomicBoolean(true);

    private Participant(SimpleSkill<?> skill, LongSupplier cadenceSupplier, PlayerPulse pulse) {
      this.skill = skill;
      this.cadenceSupplier = cadenceSupplier;
      this.pulse = pulse;
    }

    private boolean isDue(String playerKey, long now) {
      if (!active.get() || !skill.isRuntimeRegistered()) {
        return false;
      }
      if (!skill.isEnabled()) {
        lastPulseByPlayer.put(playerKey, now);
        return false;
      }

      Long lastPulse = lastPulseByPlayer.get(playerKey);
      return cadenceDue(lastPulse, now, cadence());
    }

    private void pulse(AdaptPlayer adaptPlayer, Player player, String playerKey, long now) {
      if (!active.get() || !skill.isRuntimeRegistered() || !skill.isEnabled()) {
        lastPulseByPlayer.put(playerKey, now);
        return;
      }

      long cadence = cadence();
      Long lastPulse = lastPulseByPlayer.putIfAbsent(playerKey, now);
      if (lastPulse == null) {
        return;
      }

      long elapsed = now - lastPulse;
      if (elapsed < cadence || !lastPulseByPlayer.replace(playerKey, lastPulse, now)) {
        return;
      }

      try {
        pulse.pulse(adaptPlayer, player, boundedElapsed(elapsed, cadence), cadence);
      } catch (Throwable error) {
        Adapt.error("Exception pulsing skill " + skill.getName() + " for " + playerKey);
        error.printStackTrace();
      }
    }

    private long cadence() {
      return Math.max(MIN_INTERVAL_MILLIS, cadenceSupplier.getAsLong());
    }

    private void clear(String playerKey) {
      lastPulseByPlayer.remove(playerKey);
    }

    private boolean hasSkillName(String skillName) {
      return skill.getName().equals(skillName);
    }

    private void deactivate() {
      active.set(false);
      lastPulseByPlayer.clear();
    }
  }
}
