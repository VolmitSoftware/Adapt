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

package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.adaptation.ItemCooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.content.adaptation.rift.RiftBlink;
import art.arcane.adapt.content.item.ChronoTimeBombItem;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ChronosMessages;
import art.arcane.adapt.platform.EngineFeature;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.ArmorStandStyle;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerChangedWorldEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.fx.ParticleSpec;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.input.DoubleJumpGesture;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.data.StackExclusion;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class ChronosInstantRecall extends SimpleAdaptation<ChronosInstantRecallConfig> {
  public static final PlayerPreference<CommonPreferences.Toggle> CLOCK_CLICK = CommonPreferences.toggle("clock-click", ChronosMessages.PREFERENCE_CHRONOSINSTANTRECALL_CLOCK_CLICK, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> SPRINT_CLICK = CommonPreferences.toggle("sprint-click", ChronosMessages.PREFERENCE_CHRONOSINSTANTRECALL_SPRINT_CLICK, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK = CommonPreferences.toggle("sneak-trigger", ChronosMessages.PREFERENCE_CHRONOSINSTANTRECALL_SNEAK, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> DOUBLE_JUMP = CommonPreferences.toggle("double-jump", ChronosMessages.PREFERENCE_CHRONOSINSTANTRECALL_DOUBLE_JUMP, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> LEFT = CommonPreferences.toggle("left-click", ChronosMessages.PREFERENCE_CHRONOSINSTANTRECALL_LEFT, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> RIGHT = CommonPreferences.toggle("right-click", ChronosMessages.PREFERENCE_CHRONOSINSTANTRECALL_RIGHT, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> AIR = CommonPreferences.toggle("air-clicks", ChronosMessages.PREFERENCE_CHRONOSINSTANTRECALL_AIR, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> BLOCK = CommonPreferences.toggle("block-clicks", ChronosMessages.PREFERENCE_CHRONOSINSTANTRECALL_BLOCK, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> CLOCK_SOUNDS = CommonPreferences.toggle("clock-sounds", ChronosMessages.PREFERENCE_CHRONOSINSTANTRECALL_CLOCK_SOUNDS, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK_SPRINT = CommonPreferences.toggle("sneak-sprint", ChronosMessages.PREFERENCE_CHRONOSINSTANTRECALL_SNEAK_SPRINT, CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK_CLOCK = CommonPreferences.toggle("sneak-clock", ChronosMessages.PREFERENCE_CHRONOSINSTANTRECALL_SNEAK_CLOCK, CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Toggle> JUMP_SPRINT = CommonPreferences.toggle("jump-sprint", ChronosMessages.PREFERENCE_CHRONOSINSTANTRECALL_JUMP_SPRINT, CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Toggle> JUMP_CLOCK = CommonPreferences.toggle("jump-clock", ChronosMessages.PREFERENCE_CHRONOSINSTANTRECALL_JUMP_CLOCK, CommonPreferences.Toggle.OFF);

  private static final EnumSet<InteractAction> RECALL_ACTIONS = EnumSet.of(
      InteractAction.RIGHT_CLICK_AIR,
      InteractAction.RIGHT_CLICK_BLOCK,
      InteractAction.LEFT_CLICK_AIR,
      InteractAction.LEFT_CLICK_BLOCK
  );
  private static final Map<UUID, Long> TELEPORT_XP_SUPPRESS_UNTIL = new ConcurrentHashMap<>();

  private final Map<UUID, Deque<Snapshot>> snapshots = playerState();
  private final Map<UUID, Long> lastSnapshot = playerState();
  private final Map<UUID, Long> cooldowns = playerState();
  private final Map<UUID, Boolean> cooldownReadyNotify = playerState();
  private final Map<UUID, Long> rewindProtection = playerState();
  private final Map<UUID, UUID> rewinding = playerState();
  private final Map<UUID, RecallXPFarmStamp> recallXpStamps = playerState();
  private final Map<UUID, RewindVisualState> rewindCleanups = new ConcurrentHashMap<>();
  private final DoubleJumpGesture doubleJump = new DoubleJumpGesture();
  private final AtomicBoolean acceptingRewinds = new AtomicBoolean(true);
  private static final DataKey<String> REWIND_STAMP_KEY = DataKey.adapt("chronos_instant_recall_state", DataCodecs.STRING);
  private static final Id CLOCK = Id.minecraft("clock");
  private static final Id INVISIBILITY = Id.minecraft("invisibility");
  private static final ArmorStandStyle CAMERA_ANCHOR_STYLE = new ArmorStandStyle(true, false, false, false, false);

  public ChronosInstantRecall() {
    super("chronos-instant-recall");
    registerConfiguration(ChronosInstantRecallConfig.class);
    needs(PlatformNeed.emulatedFeature(EngineFeature.PLAYER_INPUT));
    setIcon(Id.minecraft("recovery_compass"));
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("clock"))
        .key("challenge_chronos_recall_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("recovery_compass"))
            .key("challenge_chronos_recall_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("recovery_compass"))
        .key("challenge_chronos_recall_cheat_death")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_chronos_recall_50", "chronos.instant-recall.recalls", 50, 300);
    registerMilestone("challenge_chronos_recall_1k", "chronos.instant-recall.recalls", 1000, 1500);
  }

  @Override
  protected void onRuntimeActivated() {
    acceptingRewinds.set(true);
    for (PlayerRef player : AdaptCore.platform().host().onlinePlayers()) {
      Tasks.runEntity(player, () -> restoreStampedRewindState(player));
    }
  }

  @Override
  public void unregister() {
    acceptingRewinds.set(false);
    rewinding.clear();

    List<RewindVisualState> visualStates = new ArrayList<>(rewindCleanups.values());
    rewindCleanups.clear();
    for (RewindVisualState visualState : visualStates) {
      PlayerRef player = visualState.player();
      boolean scheduled = Tasks.runEntity(player, visualState.cleanup());
      if (!scheduled && Tasks.isOwnedByCurrentRegion(player)) {
        visualState.cleanup().run();
      }
    }

    snapshots.clear();
    lastSnapshot.clear();
    cooldowns.clear();
    cooldownReadyNotify.clear();
    rewindProtection.clear();
    recallXpStamps.clear();
    TELEPORT_XP_SUPPRESS_UNTIL.clear();
    super.unregister();
  }

  private static void markRecallTeleportSuppressed(UUID id, long suppressUntilMillis) {
    long current = TELEPORT_XP_SUPPRESS_UNTIL.getOrDefault(id, 0L);
    if (suppressUntilMillis > current) {
      TELEPORT_XP_SUPPRESS_UNTIL.put(id, suppressUntilMillis);
    }
  }

  public static boolean isRecallTeleportSuppressed(PlayerRef p) {
    if (p == null) {
      return false;
    }

    UUID id = p.uuid();
    long until = TELEPORT_XP_SUPPRESS_UNTIL.getOrDefault(id, 0L);
    if (until <= M.ms()) {
      TELEPORT_XP_SUPPRESS_UNTIL.remove(id);
      return false;
    }

    return true;
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CLOCK_CLICK, SPRINT_CLICK, SNEAK, DOUBLE_JUMP, LEFT, RIGHT, AIR, BLOCK, CLOCK_SOUNDS, SNEAK_SPRINT, SNEAK_CLOCK, JUMP_SPRINT, JUMP_CLOCK);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.duration(getRewindDurationMillis(level), 1), 1);
    statLore(v, C.RED, "* ", Form.duration(getCooldownMillis(level), 1), 2);
    v.addLore(C.GRAY + "* " + AdaptLanguage.text(ChronosMessages.INSTANT_RECALL_LORE3));
    v.addLore(C.GRAY + "* " + AdaptLanguage.text(ChronosMessages.INSTANT_RECALL_BLINK_PRIORITY));
    if (getConfig().consumeClock) {
      v.addLore(C.RED + "* " + AdaptLanguage.text(ChronosMessages.INSTANT_RECALL_LORE_COST_CLOCK));
    }
    if (getConfig().healthCostFraction > 0) {
      statLore(v, C.RED, "* ", Form.pc(getConfig().healthCostFraction, 0), ChronosMessages.INSTANT_RECALL_LORE_COST_HEALTH);
    }
    List<String> combos = getTriggerCombos();
    if (combos.isEmpty()) {
      v.addLore(C.AQUA + "* " + C.GRAY + AdaptLanguage.text(ChronosMessages.INSTANT_RECALL_TRIGGER_NONE));
      return;
    }

    for (String combo : combos) {
      v.addLore(C.AQUA + "* " + C.GRAY + AdaptLanguage.text(
          ChronosMessages.INSTANT_RECALL_TRIGGER,
          trusted("combo", C.WHITE + combo)
      ));
    }
  }

  private long getRewindDurationMillis(int level) {
    return (long) (getRewindDurationSeconds(level) * 1000D);
  }

  private double getRewindDurationSeconds(int level) {
    double raw = getConfig().baseRewindSeconds + (level * getConfig().rewindSecondsPerLevel);
    return Math.max(0.25D, Math.min(getConfig().maxRewindSeconds, raw));
  }

  private long getCooldownMillis(int level) {
    return getRewindDurationMillis(level) + (getConfig().cooldownPaddingSeconds * 1000L);
  }

  private long getMaximumHistoryMillis() {
    return (long) ((getRewindDurationSeconds(getConfig().maxLevel) + getConfig().historyPaddingSeconds) * 1000D);
  }

  private int getRewindAnimationTicks() {
    int durationMillis = Math.max(100, getConfig().rewindAnimationDurationMillis);
    int ticks = (int) Math.ceil(durationMillis / 50D);
    if (ticks <= 1) {
      ticks = Math.max(2, getConfig().rewindAnimationTicks);
    }

    return Math.max(2, ticks);
  }

  private boolean isSingleWorldPath(List<Snapshot> path) {
    if (path.isEmpty()) {
      return false;
    }

    String worldKey = path.get(0).worldKey();
    for (Snapshot snapshot : path) {
      if (!Objects.equals(worldKey, snapshot.worldKey())) {
        return false;
      }
    }

    return true;
  }

  private EntityRef spawnRecallCameraAnchor(PlayerRef p) {
    Loc location = p.location();
    if (location.world() == null) {
      return null;
    }

    return location.world().spawnArmorStand(location, CAMERA_ANCHOR_STYLE, stand -> {
      StackExclusion.exclude(stand);
      if (stand instanceof LivingRef living) {
        living.setInvisible(true);
      }
      stand.setGravity(false);
      stand.setSilent(true);
      stand.setInvulnerable(true);
      stand.setPersistent(false);
    });
  }

  private List<String> getTriggerCombos() {
    List<String> triggers = new ArrayList<>();
    String clickSurface = getClickSurfaceLabel();
    String clock = AdaptLanguage.text(ChronosMessages.INSTANT_RECALL_TRIGGER_CLOCK);
    String sprint = AdaptLanguage.text(ChronosMessages.INSTANT_RECALL_TRIGGER_SPRINT);
    String sneak = AdaptLanguage.text(ChronosMessages.INSTANT_RECALL_TRIGGER_SNEAK);
    if (getConfig().enableClockClickTrigger) {
      appendClickCombos(triggers, clock, getConfig().clockClickLeftClick, getConfig().clockClickRightClick, clickSurface);
    }

    if (getConfig().enableSprintClickTrigger) {
      appendClickCombos(
          triggers,
          joinTrigger(sprint, clock),
          getConfig().sprintClickLeftClick,
          getConfig().sprintClickRightClick,
          clickSurface
      );
    }

    if (getConfig().enableSingleSneakTrigger) {
      String combo = getConfig().singleSneakRequiresSprint ? joinTrigger(sprint, sneak) : sneak;
      if (getConfig().singleSneakRequiresClockInHand) {
        combo = joinTrigger(combo, clock);
      }
      triggers.add(combo);
    }

    if (getConfig().enableDoubleJumpTrigger) {
      String combo = AdaptLanguage.text(ChronosMessages.INSTANT_RECALL_TRIGGER_DOUBLE_JUMP);
      if (getConfig().doubleJumpRequiresSprint) {
        combo = joinTrigger(combo, sprint);
      }
      if (getConfig().doubleJumpRequiresClockInHand) {
        combo = joinTrigger(combo, clock);
      }
      triggers.add(combo);
    }

    return triggers;
  }

  private void appendClickCombos(List<String> triggers, String prefix, boolean allowLeft, boolean allowRight, String clickSurface) {
    if (clickSurface.isBlank()) {
      return;
    }

    if (allowLeft) {
      triggers.add(withClickSurface(
          joinTrigger(prefix, AdaptLanguage.text(ChronosMessages.INSTANT_RECALL_TRIGGER_LEFT_CLICK)),
          clickSurface
      ));
    }

    if (allowRight) {
      triggers.add(withClickSurface(
          joinTrigger(prefix, AdaptLanguage.text(ChronosMessages.INSTANT_RECALL_TRIGGER_RIGHT_CLICK)),
          clickSurface
      ));
    }
  }

  private String getClickSurfaceLabel() {
    if (getConfig().allowAirClicks && getConfig().allowBlockClicks) {
      return AdaptLanguage.text(ChronosMessages.INSTANT_RECALL_SURFACE_AIR_BLOCK);
    }

    if (getConfig().allowAirClicks) {
      return AdaptLanguage.text(ChronosMessages.INSTANT_RECALL_SURFACE_AIR);
    }

    if (getConfig().allowBlockClicks) {
      return AdaptLanguage.text(ChronosMessages.INSTANT_RECALL_SURFACE_BLOCK);
    }

    return "";
  }

  private String joinTrigger(String first, String second) {
    return AdaptLanguage.text(
        ChronosMessages.INSTANT_RECALL_TRIGGER_JOIN,
        trusted("first", first),
        trusted("second", second)
    );
  }

  private String withClickSurface(String trigger, String surface) {
    if (surface.isBlank()) {
      return trigger;
    }
    return AdaptLanguage.text(
        ChronosMessages.INSTANT_RECALL_TRIGGER_SURFACE,
        trusted("trigger", trigger),
        trusted("surface", surface)
    );
  }

  private RecallXPContext buildRecallXPContext(Snapshot from, Snapshot to) {
    double distance;
    if (Objects.equals(from.worldKey(), to.worldKey())) {
      double dx = from.x() - to.x();
      double dy = from.y() - to.y();
      double dz = from.z() - to.z();
      distance = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
    } else {
      distance = getConfig().xpCrossWorldDistanceCredit;
    }

    double healthRecovered = Math.max(0D, to.health() - from.health());
    double hungerRecovered = Math.max(0D, to.foodLevel() - from.foodLevel());
    double saturationRecovered = Math.max(0D, to.saturation() - from.saturation());

    return new RecallXPContext(
        from.worldKey(),
        from.x(),
        from.y(),
        from.z(),
        to.worldKey(),
        to.x(),
        to.y(),
        to.z(),
        distance,
        healthRecovered,
        hungerRecovered,
        saturationRecovered);
  }

  private boolean pointsAreSimilar(String worldA, double ax, double ay, double az, String worldB, double bx, double by, double bz, double radius) {
    if (!Objects.equals(worldA, worldB)) {
      return false;
    }

    double dx = ax - bx;
    double dy = ay - by;
    double dz = az - bz;
    return (dx * dx) + (dy * dy) + (dz * dz) <= (radius * radius);
  }

  private boolean isRepeatRecall(RecallXPFarmStamp stamp, RecallXPContext context) {
    return pointsAreSimilar(stamp.fromWorldKey(), stamp.fromX(), stamp.fromY(), stamp.fromZ(),
        context.fromWorldKey(), context.fromX(), context.fromY(), context.fromZ(),
        getConfig().xpRepeatSourceRadius)
        && pointsAreSimilar(stamp.toWorldKey(), stamp.toX(), stamp.toY(), stamp.toZ(),
        context.toWorldKey(), context.toX(), context.toY(), context.toZ(),
        getConfig().xpRepeatTargetRadius);
  }

  private double computeRecallXPGain(UUID playerId, int level, RecallXPContext context, long now) {
    double raw = (context.distance() * getConfig().xpPerDistanceBlock)
        + (context.healthRecovered() * getConfig().xpPerHealthPoint)
        + (context.hungerRecovered() * getConfig().xpPerHungerPoint)
        + (context.saturationRecovered() * getConfig().xpPerSaturationPoint);

    if (raw < getConfig().xpMinRawReward) {
      return 0D;
    }

    double leveled = raw * (1D + ((Math.max(1, level) - 1) * getConfig().xpLevelMultiplierPerLevel));
    double multiplier = 1D;

    RecallXPFarmStamp previous = recallXpStamps.get(playerId);
    if (previous != null) {
      long elapsed = now - previous.awardedAt();
      if (elapsed < getConfig().xpDiminishWindowMillis) {
        double t = Math.max(0D, Math.min(1D, elapsed / (double) Math.max(1L, getConfig().xpDiminishWindowMillis)));
        multiplier *= getConfig().xpDiminishMinMultiplier + ((1D - getConfig().xpDiminishMinMultiplier) * t);
      }

      if (elapsed < getConfig().xpRepeatWindowMillis && isRepeatRecall(previous, context)) {
        multiplier *= getConfig().xpRepeatPenaltyMultiplier;
      }
    }

    double reward = Math.min(getConfig().xpMaxAward, leveled * multiplier);
    if (reward < getConfig().xpMinAward) {
      return 0D;
    }

    return reward;
  }

  private Snapshot snapshotFromPlayer(PlayerRef p, long now) {
    return new Snapshot(now,
        worldKey(p.world()),
        p.location().x(),
        p.location().y(),
        p.location().z(),
        p.location().yaw(),
        p.location().pitch(),
        p.health(),
        p.foodLevel(),
        p.saturation(),
        p.exhaustion(),
        p.fireTicks());
  }

  private Snapshot snapshotFromLocation(PlayerRef p, Loc location, long now) {
    WorldRef world = location.world();
    if (world == null) {
      world = p.world();
    }

    return new Snapshot(now,
        worldKey(world),
        location.x(),
        location.y(),
        location.z(),
        location.yaw(),
        location.pitch(),
        p.health(),
        p.foodLevel(),
        p.saturation(),
        p.exhaustion(),
        p.fireTicks());
  }

  private void resetSnapshotHistory(PlayerRef p, Loc location) {
    if (location == null) {
      return;
    }

    long now = M.ms();
    UUID id = p.uuid();
    Deque<Snapshot> queue = snapshots.computeIfAbsent(id, unused -> new ArrayDeque<>());
    queue.clear();
    queue.addLast(snapshotFromLocation(p, location, now));
    lastSnapshot.put(id, now);
  }

  private void captureSnapshot(PlayerRef p) {
    long now = M.ms();
    UUID id = p.uuid();
    long last = lastSnapshot.getOrDefault(id, 0L);
    if (now - last < getConfig().snapshotIntervalMillis) {
      return;
    }

    lastSnapshot.put(id, now);
    Deque<Snapshot> queue = snapshots.computeIfAbsent(id, k -> new ArrayDeque<>());
    queue.addLast(snapshotFromPlayer(p, now));

    long maxAge = getMaximumHistoryMillis();
    while (!queue.isEmpty() && now - queue.getFirst().timestamp() > maxAge) {
      queue.removeFirst();
    }
  }

  private Snapshot findSnapshot(PlayerRef p, long rewindMillis) {
    Deque<Snapshot> queue = snapshots.get(p.uuid());
    if (queue == null || queue.isEmpty()) {
      return null;
    }

    long target = M.ms() - rewindMillis;
    Snapshot fallback = queue.getFirst();

    for (Snapshot s : queue) {
      if (s.timestamp() <= target) {
        fallback = s;
      } else {
        break;
      }
    }

    return fallback;
  }

  private List<Snapshot> buildRewindPath(PlayerRef p, long rewindMillis, Snapshot anchor) {
    List<Snapshot> path = new ArrayList<>();
    long now = M.ms();
    path.add(snapshotFromPlayer(p, now));

    Deque<Snapshot> queue = snapshots.get(p.uuid());
    if (queue == null || queue.isEmpty()) {
      path.add(anchor);
      return path;
    }

    Iterator<Snapshot> reverse = queue.descendingIterator();
    while (reverse.hasNext()) {
      Snapshot snap = reverse.next();
      if (snap.timestamp() < anchor.timestamp()) {
        break;
      }
      if (snap.timestamp() <= now) {
        path.add(snap);
      }
    }

    Snapshot last = path.get(path.size() - 1);
    if (last.timestamp() != anchor.timestamp()) {
      path.add(anchor);
    }

    if (path.size() < 2) {
      path.add(anchor);
    }

    return path;
  }

  private List<Snapshot> buildAnimationPath(List<Snapshot> rawPath, int animationTicks) {
    List<Snapshot> animationPath = new ArrayList<>();
    if (rawPath.isEmpty()) {
      return animationPath;
    }

    if (animationTicks <= 1 || rawPath.size() == 1) {
      animationPath.add(rawPath.get(rawPath.size() - 1));
      return animationPath;
    }

    for (int step = 0; step < animationTicks; step++) {
      double progress = step / (double) (animationTicks - 1);
      double scaled = progress * (rawPath.size() - 1);
      int lower = (int) Math.floor(scaled);
      int upper = Math.min(rawPath.size() - 1, lower + 1);
      double alpha = scaled - lower;
      Snapshot a = rawPath.get(lower);
      Snapshot b = rawPath.get(upper);
      animationPath.add(interpolateSnapshot(a, b, alpha));
    }

    Snapshot anchor = rawPath.get(rawPath.size() - 1);
    animationPath.set(animationPath.size() - 1, anchor);
    return animationPath;
  }

  private Snapshot interpolateSnapshot(Snapshot a, Snapshot b, double alpha) {
    if (alpha <= 0D) {
      return a;
    }
    if (alpha >= 1D) {
      return b;
    }

    long timestamp = (long) Math.round(lerp(a.timestamp(), b.timestamp(), alpha));
    String worldKey = alpha < 0.5D ? a.worldKey() : b.worldKey();
    double x = lerp(a.x(), b.x(), alpha);
    double y = lerp(a.y(), b.y(), alpha);
    double z = lerp(a.z(), b.z(), alpha);
    float yaw = lerpAngle(a.yaw(), b.yaw(), alpha);
    float pitch = (float) lerp(a.pitch(), b.pitch(), alpha);
    double health = lerp(a.health(), b.health(), alpha);
    int foodLevel = (int) Math.round(lerp(a.foodLevel(), b.foodLevel(), alpha));
    float saturation = (float) lerp(a.saturation(), b.saturation(), alpha);
    float exhaustion = (float) lerp(a.exhaustion(), b.exhaustion(), alpha);
    int fireTicks = (int) Math.round(lerp(a.fireTicks(), b.fireTicks(), alpha));

    return new Snapshot(timestamp, worldKey, x, y, z, yaw, pitch, health, foodLevel, saturation, exhaustion, fireTicks);
  }

  private double lerp(double a, double b, double alpha) {
    return a + ((b - a) * alpha);
  }

  private float lerpAngle(float from, float to, double alpha) {
    float delta = to - from;
    while (delta > 180F) {
      delta -= 360F;
    }
    while (delta < -180F) {
      delta += 360F;
    }

    return from + (float) (delta * alpha);
  }

  private Loc toLocation(Snapshot snapshot, WorldRef fallback) {
    WorldRef resolved = AdaptCore.platform().host().world(Id.parse(snapshot.worldKey()));
    WorldRef world = resolved == null ? fallback : resolved;
    return new Loc(world, snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(), snapshot.pitch());
  }

  private static String worldKey(WorldRef world) {
    return world.key().toString();
  }

  private void applySnapshotState(PlayerRef p, Snapshot snapshot) {
    double maxHealth = p.maxHealth();
    p.setHealth(Math.max(1, Math.min(maxHealth, snapshot.health())));
    p.setFoodLevel(Math.max(0, Math.min(20, snapshot.foodLevel())));
    p.setSaturation(Math.max(0, snapshot.saturation()));
    p.setExhaustion(Math.max(0, snapshot.exhaustion()));
    p.setFireTicks(Math.max(0, snapshot.fireTicks()));
    p.setFallDistance(0);
    p.setVelocity(Vec3.ZERO);
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    UUID id = e.player().uuid();
    finishRewindVisualState(id);
    restoreStampedRewindState(e.player());
    clearPlayerState(id);
  }

  @Handle
  public void on(PlayerJoinEvent e) {
    restoreStampedRewindState(e.player());
  }

  private void finishRewindVisualState(UUID id) {
    RewindVisualState visualState = rewindCleanups.remove(id);
    if (visualState != null) {
      visualState.cleanup().run();
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerTeleportEvent e) {
    PlayerRef p = e.player();
    UUID id = p.uuid();
    if (!isRecallEligible(p) || rewinding.containsKey(id)) {
      return;
    }

    Loc destination = e.to();
    if (destination == null) {
      return;
    }

    resetSnapshotHistory(p, destination);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerChangedWorldEvent e) {
    PlayerRef p = e.player();
    UUID id = p.uuid();
    if (!isRecallEligible(p) || rewinding.containsKey(id)) {
      return;
    }

    resetSnapshotHistory(p, p.location());
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    PlayerRef p = e.player();
    if (!isRecallEligible(p)) {
      return;
    }

    if (shouldTriggerSprintClockClick(e)) {
      e.setCancelled(true);
      attemptRecall(p);
      return;
    }

    if (shouldTriggerClockClick(e)) {
      e.setCancelled(true);
      attemptRecall(p);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    if (!e.isSneaking() || !isRecallEligible(p) || (!getConfig().enableSingleSneakTrigger || !preferenceEnabled(p, SNEAK))) {
      return;
    }

    if ((getConfig().singleSneakRequiresSprint || preferenceEnabled(p, SNEAK_SPRINT)) && !p.isSprinting()) {
      return;
    }

    if ((getConfig().singleSneakRequiresClockInHand || preferenceEnabled(p, SNEAK_CLOCK)) && !hasRecallClockInEitherHand(p)) {
      return;
    }

    attemptRecall(p);
  }

  private Hand resolveRecallHand(PlayerRef p, Hand eventHand) {
    ItemRef main = p.inventory().mainHand();
    ItemRef off = p.inventory().offHand();

    if (eventHand == null) {
      if (isRecallClock(main)) {
        return Hand.MAIN_HAND;
      }

      if (isRecallClock(off)) {
        return Hand.OFF_HAND;
      }

      return null;
    }

    if (eventHand == Hand.MAIN_HAND) {
      return isRecallClock(main) ? Hand.MAIN_HAND : null;
    }

    if (eventHand == Hand.OFF_HAND) {
      if (isRecallClock(main)) {
        return null;
      }

      return isRecallClock(off) ? Hand.OFF_HAND : null;
    }

    return null;
  }

  private boolean isRecallEligible(PlayerRef p) {
    return p.gameMode() == GameMode.SURVIVAL && hasActiveAdaptation(p);
  }

  private boolean isLeftClick(InteractAction action) {
    return action == InteractAction.LEFT_CLICK_AIR || action == InteractAction.LEFT_CLICK_BLOCK;
  }

  private boolean isRightClick(InteractAction action) {
    return action == InteractAction.RIGHT_CLICK_AIR || action == InteractAction.RIGHT_CLICK_BLOCK;
  }

  private boolean isBlockClick(InteractAction action) {
    return action == InteractAction.LEFT_CLICK_BLOCK || action == InteractAction.RIGHT_CLICK_BLOCK;
  }

  private boolean isActionAllowed(PlayerRef player, InteractAction action) {
    if (!RECALL_ACTIONS.contains(action)) {
      return false;
    }

    if ((!getConfig().allowAirClicks || !preferenceEnabled(player, AIR)) && !isBlockClick(action)) {
      return false;
    }

    if ((!getConfig().allowBlockClicks || !preferenceEnabled(player, BLOCK)) && isBlockClick(action)) {
      return false;
    }

    return true;
  }

  private boolean shouldTriggerClockClick(PlayerInteractEvent e) {
    if ((!getConfig().enableClockClickTrigger || !preferenceEnabled(e.player(), CLOCK_CLICK))) {
      return false;
    }

    InteractAction action = e.action();
    if (!isActionAllowed(e.player(), action)) {
      return false;
    }

    if (isLeftClick(action) && (!getConfig().clockClickLeftClick || !preferenceEnabled(e.player(), LEFT))) {
      return false;
    }

    if (isRightClick(action) && (!getConfig().clockClickRightClick || !preferenceEnabled(e.player(), RIGHT))) {
      return false;
    }

    return resolveRecallHand(e.player(), e.hand()) != null;
  }

  private boolean shouldTriggerSprintClockClick(PlayerInteractEvent e) {
    if ((!getConfig().enableSprintClickTrigger || !preferenceEnabled(e.player(), SPRINT_CLICK)) || !e.player().isSprinting()) {
      return false;
    }

    InteractAction action = e.action();
    if (!isActionAllowed(e.player(), action)) {
      return false;
    }

    if (isLeftClick(action) && (!getConfig().sprintClickLeftClick || !preferenceEnabled(e.player(), LEFT))) {
      return false;
    }

    if (isRightClick(action) && (!getConfig().sprintClickRightClick || !preferenceEnabled(e.player(), RIGHT))) {
      return false;
    }

    return resolveRecallHand(e.player(), e.hand()) != null;
  }

  private boolean hasRecallClockInEitherHand(PlayerRef p) {
    return isRecallClock(p.inventory().mainHand())
        || isRecallClock(p.inventory().offHand());
  }

  private boolean canTriggerDoubleJump(PlayerRef p) {
    if (RiftBlink.ownsManualTrigger(p)) {
      return false;
    }
    if (rewinding.containsKey(p.uuid())) {
      return false;
    }

    if ((getConfig().doubleJumpRequiresSprint || preferenceEnabled(p, JUMP_SPRINT)) && !p.isSprinting()) {
      return false;
    }

    return holdsRequiredClock(p);
  }

  private boolean holdsRequiredClock(PlayerRef p) {
    return !(getConfig().doubleJumpRequiresClockInHand || preferenceEnabled(p, JUMP_CLOCK)) || hasRecallClockInEitherHand(p);
  }

  private void clearPlayerState(UUID id) {
    snapshots.remove(id);
    lastSnapshot.remove(id);
    cooldowns.remove(id);
    cooldownReadyNotify.remove(id);
    rewindProtection.remove(id);
    rewinding.remove(id);
    recallXpStamps.remove(id);
    rewindCleanups.remove(id);
    doubleJump.reset(id);
    TELEPORT_XP_SUPPRESS_UNTIL.remove(id);
  }

  private boolean isRecallClock(ItemRef stack) {
    return stack != null
        && stack.type().equals(CLOCK)
        && !ChronoTimeBombItem.isBindableItem(stack);
  }

  boolean consumeRecallClock(PlayerRef p) {
    if (!getConfig().consumeClock) {
      return true;
    }

    ItemRef main = p.inventory().mainHand();
    if (isRecallClock(main)) {
      return payItemCost(p, "clock", AdaptCore.platform().host().items().create(CLOCK), 1, () -> {
        main.setAmount(main.amount() - 1);
        p.inventory().setMainHand(main);
        return true;
      });
    }

    ItemRef off = p.inventory().offHand();
    if (isRecallClock(off)) {
      return payItemCost(p, "clock", AdaptCore.platform().host().items().create(CLOCK), 1, () -> {
        off.setAmount(off.amount() - 1);
        p.inventory().setOffHand(off);
        return true;
      });
    }

    return false;
  }

  void applyRecallHealthCost(PlayerRef p) {
    double fraction = Math.max(0D, Math.min(1D, getConfig().healthCostFraction));
    if (fraction <= 0D || p.isDead()) {
      return;
    }

    double healthAfter = Math.max(1.0D, p.health() * (1D - fraction));
    double amount = p.health() - healthAfter;
    if (amount <= 0D) {
      return;
    }
    payHealthCost(p, "health", (int) Math.ceil(amount), () -> {
      p.setHealth(Math.max(1D, p.health() - amount));
      return true;
    });
  }

  private void attemptRecall(PlayerRef p) {
    UUID id = p.uuid();
    if (!acceptingRewinds.get() || !isRecallEligible(p)) {
      return;
    }

    if (rewinding.containsKey(id)) {
      return;
    }

    long now = M.ms();
    long cooldown = cooldowns.getOrDefault(id, 0L);
    if (cooldown > now) {
      // A plain clock has no vanilla right-click use, and time bombs carry
      // their own cooldown group, so the whole material can carry the sweep.
      ItemCooldowns.pushMaterial(p, CLOCK, cooldown - now);
      if ((getConfig().playClockSounds && preferenceEnabled(p, CLOCK_SOUNDS))) {
        ChronosSoundFX.playClockReject(p);
      }
      return;
    }

    int level = getActiveLevel(p);
    long rewindMillis = getRewindDurationMillis(level);
    Snapshot anchor = findSnapshot(p, rewindMillis);
    if (anchor == null) {
      if ((getConfig().playClockSounds && preferenceEnabled(p, CLOCK_SOUNDS))) {
        ChronosSoundFX.playClockReject(p);
      }
      return;
    }

    int animationTicks = getRewindAnimationTicks();
    List<Snapshot> path = buildRewindPath(p, rewindMillis, anchor);
    List<Snapshot> animationPath = buildAnimationPath(path, animationTicks);
    if (animationPath.isEmpty()) {
      if ((getConfig().playClockSounds && preferenceEnabled(p, CLOCK_SOUNDS))) {
        ChronosSoundFX.playClockReject(p);
      }
      return;
    }

    if (!consumeRecallClock(p)) {
      return;
    }

    Snapshot finalSnapshot = animationPath.get(animationPath.size() - 1);
    RecallXPContext xpContext = buildRecallXPContext(animationPath.get(0), finalSnapshot);
    double healthBeforeRecall = p.health();
    double healthAfterRecall = finalSnapshot.health();
    long castAt = M.ms();
    GameMode originalGameMode = p.gameMode();
    TemporalFreezeRegistry.Flight originalFlight = TemporalFreezeRegistry.flightBeforeFreeze(p);
    boolean originalAllowFlight = originalFlight.allowFlight();
    boolean originalFlying = originalFlight.flying();
    boolean temporarySpectator = getConfig().rewindUseTemporarySpectator && originalGameMode == GameMode.SURVIVAL;
    boolean allowClientCamera = temporarySpectator
        && getConfig().rewindUseClientCamera
        && isSingleWorldPath(animationPath);
    EntityRef cameraAnchor = null;
    if (temporarySpectator) {
      stampRewindState(p, originalGameMode, originalAllowFlight, originalFlying, null);
      p.setGameMode(GameMode.SPECTATOR);
      p.setAllowFlight(true);
      p.setFlying(true);
      if (allowClientCamera) {
        cameraAnchor = spawnRecallCameraAnchor(p);
        if (cameraAnchor != null && cameraAnchor.isValid()) {
          stampRewindState(
              p,
              originalGameMode,
              originalAllowFlight,
              originalFlying,
              cameraAnchor.uuid()
          );
          p.setSpectatorTarget(cameraAnchor);
        } else {
          allowClientCamera = false;
        }
      }
    }

    UUID operationId = UUID.randomUUID();
    long recallCooldownMillis = getCooldownMillis(level);
    cooldowns.put(id, castAt + recallCooldownMillis);
    AbilityGlyphs.cooldown(id, this, castAt, recallCooldownMillis);
    ItemCooldowns.pushMaterial(p, CLOCK, recallCooldownMillis);
    cooldownReadyNotify.put(id, true);
    rewinding.put(id, operationId);
    long protectionUntil = castAt + ((long) (animationTicks + getConfig().rewindProtectionTicks) * 50L);
    rewindProtection.put(id, protectionUntil);
    markRecallTeleportSuppressed(id, protectionUntil + ((long) Math.max(0, getConfig().rewindTeleportXpSuppressExtraTicks) * 50L));
    p.addEffect(new EffectInstance(INVISIBILITY, animationTicks + getConfig().rewindProtectionTicks, 0, true, false, false));

    FxPresets.chargeRing(this, p.location(), 3);
    if ((getConfig().playClockSounds && preferenceEnabled(p, CLOCK_SOUNDS))) {
      ChronosSoundFX.playRewindStart(p);
    }

    final boolean initialClientCamera = allowClientCamera;
    final EntityRef initialCameraAnchor = cameraAnchor;
    int[] step = {0};
    Loc[] lastLoc = {p.location()};
    boolean[] clientCameraActive = {initialClientCamera};
    EntityRef[] cameraRef = {initialCameraAnchor};

    Runnable cleanupVisualState = () -> {
      EntityRef anchorEntity = null;
      if (cameraRef[0] != null) {
        anchorEntity = cameraRef[0];
        cameraRef[0] = null;
      }

      if (temporarySpectator) {
        if (p.gameMode() == GameMode.SPECTATOR) {
          p.setSpectatorTarget(null);
        }
        restoreOwnedPlayerState(p, originalGameMode, originalAllowFlight, originalFlying);
        finishStampedRewindCleanup(p, anchorEntity);
      }
    };
    rewindCleanups.put(id, new RewindVisualState(p, cleanupVisualState));

    Runnable[] rewindTask = new Runnable[1];
    rewindTask[0] = () -> {
      if (!acceptingRewinds.get() || !operationId.equals(rewinding.get(id))
          || !p.isOnline() || p.isDead()) {
        if (rewinding.remove(id, operationId)) {
          finishRewindVisualState(id);
        }
        return;
      }

      float progress = animationTicks <= 1 ? 1f : (float) step[0] / (float) (animationTicks - 1);
      int index = Math.min(animationPath.size() - 1, step[0]);
      Snapshot snapshot = animationPath.get(index);
      Loc destination = toLocation(snapshot, p.world());

      if (getConfig().showRewindTraceParticles && lastLoc[0].world() != null && lastLoc[0].sameWorld(destination)) {
        Loc traceFrom = lastLoc[0].add(0, 1, 0);
        Loc traceTo = destination.add(0, 1, 0);
        fx(traceFrom, FxPriority.TRAIL)
            .line(Particles.REVERSE_PORTAL, traceTo.x(), traceTo.y(), traceTo.z(), Math.max(4, getConfig().rewindTracePoints))
            .particle(Particles.END_ROD, 1, traceTo.x() - traceFrom.x(), traceTo.y() - traceFrom.y(), traceTo.z() - traceFrom.z(), 0, 0);
      }

      boolean movedClient = false;
      if (clientCameraActive[0] && cameraRef[0] != null && cameraRef[0].isValid()) {
        EntityRef target = p.spectatorTarget();
        if (target == null || !target.uuid().equals(cameraRef[0].uuid())) {
          p.setSpectatorTarget(cameraRef[0]);
          target = p.spectatorTarget();
        }

        if (target != null
            && target.uuid().equals(cameraRef[0].uuid())
            && destination.world() != null
            && destination.world().equals(cameraRef[0].world())) {
          cameraRef[0].teleport(destination, TeleportCause.PLUGIN);
          movedClient = true;
        } else {
          clientCameraActive[0] = false;
        }
      }

      if (!movedClient) {
        p.teleport(destination, TeleportCause.PLUGIN);
      }

      if (!temporarySpectator || step[0] >= animationPath.size() - 1) {
        applySnapshotState(p, snapshot);
      }

      if ((getConfig().playClockSounds && preferenceEnabled(p, CLOCK_SOUNDS))) {
        ChronosSoundFX.playRewindStep(p, progress);
      }

      lastLoc[0] = destination;
      step[0]++;

      if (step[0] >= animationTicks) {
        beginFinalRecallTeleport(p, new FinalRecall(
            id,
            operationId,
            finalSnapshot,
            healthBeforeRecall,
            healthAfterRecall,
            level,
            xpContext
        ));
        return;
      }

      if (Tasks.isFoliaThreading()) {
        Tasks.runEntity(p, rewindTask[0], 1);
      } else {
        Tasks.s(rewindTask[0], 1);
      }
    };
    if (Tasks.isFoliaThreading()) {
      Tasks.runEntity(p, rewindTask[0]);
    } else {
      Tasks.s(rewindTask[0]);
    }
  }

  private void beginFinalRecallTeleport(PlayerRef p, FinalRecall recall) {
    Loc destination = toLocation(recall.snapshot(), p.world());
    if (destination.world() == null) {
      abortFinalRecall(recall);
      return;
    }

    CompletableFuture<Boolean> teleport;
    try {
      teleport = p.teleport(destination, TeleportCause.PLUGIN);
    } catch (RuntimeException error) {
      AdaptLog.error("Instant Recall could not start its final teleport for "
          + recall.playerId() + ".");
      AdaptLog.error(error);
      abortFinalRecall(recall);
      return;
    }

    if (teleport == null) {
      abortFinalRecall(recall);
      return;
    }
    teleport.whenComplete((success, failure) ->
        finishFinalRecall(p, recall, success, failure));
  }

  private void finishFinalRecall(
      PlayerRef p,
      FinalRecall recall,
      Boolean success,
      Throwable failure
  ) {
    if (failure != null) {
      AdaptLog.error("Instant Recall final teleport failed for " + recall.playerId() + ".");
      AdaptLog.error(failure);
    }

    Runnable completion = () -> {
      boolean operationOwned = rewinding.remove(recall.playerId(), recall.operationId());
      if (operationOwned) {
        finishRewindVisualState(recall.playerId());
      }
      if (!shouldCommitFinalRecall(
          success,
          failure,
          operationOwned,
          p.isOnline(),
          p.isDead()
      )) {
        return;
      }
      AdaptPlayer adaptPlayer = getPlayer(p);
      if (adaptPlayer == null) {
        return;
      }
      completeSuccessfulRecall(p, recall, adaptPlayer);
    };
    boolean scheduled = Tasks.runEntity(p, completion);
    if (!scheduled && Tasks.isOwnedByCurrentRegion(p)) {
      completion.run();
    }
  }

  private void abortFinalRecall(FinalRecall recall) {
    if (rewinding.remove(recall.playerId(), recall.operationId())) {
      finishRewindVisualState(recall.playerId());
    }
  }

  private void completeSuccessfulRecall(PlayerRef p, FinalRecall recall, AdaptPlayer adaptPlayer) {
    applySnapshotState(p, recall.snapshot());
    applyRecallHealthCost(p);

    Loc bloomAt = p.location().add(0, 1, 0);
    fx(bloomAt, FxPriority.TRANSITION)
        .particle(Particles.FLASH, 1, 0, 0, 0, 0, 0)
        .particle(Particles.TOTEM, 26, 0, 0, 0, 0.3D, 0.02D);
    ParticleSpec clockCrack = Particles.itemCrack(AdaptCore.platform().host().items().create(CLOCK));
    timeline(p)
        .duration(3)
        .priority(FxPriority.TRANSITION)
        .cullRadius(32)
        .frame((f, tick, progress) -> {
          if (tick >= 1) {
            f.particle(clockCrack, 9, 0, 1.0D, 0, 0.3D, 0.02D);
          }
        })
        .start();
    if ((getConfig().playClockSounds && preferenceEnabled(p, CLOCK_SOUNDS))) {
      ChronosSoundFX.playRewindFinish(p);
    }

    addStat(p, "chronos.instant-recall.recalls", 1);
    if (recall.healthBefore() <= 4 && recall.healthAfter() >= 16
        && AdaptConfig.get().isAdvancements()
        && !adaptPlayer.getData().isGranted("challenge_chronos_recall_cheat_death")) {
      adaptPlayer.getAdvancementHandler().grant("challenge_chronos_recall_cheat_death");
    }

    long awardAt = M.ms();
    RecallXPContext xpContext = recall.xpContext();
    double xpGain = computeRecallXPGain(
        recall.playerId(),
        recall.level(),
        xpContext,
        awardAt
    );
    if (xpGain <= 0D) {
      return;
    }

    xp(p, p.location(), xpGain);
    recallXpStamps.put(recall.playerId(), new RecallXPFarmStamp(
        awardAt,
        xpContext.fromWorldKey(),
        xpContext.fromX(),
        xpContext.fromY(),
        xpContext.fromZ(),
        xpContext.toWorldKey(),
        xpContext.toX(),
        xpContext.toY(),
        xpContext.toZ()));
  }

  static boolean shouldCommitFinalRecall(
      Boolean success,
      Throwable failure,
      boolean operationOwned,
      boolean online,
      boolean dead
  ) {
    return operationOwned && online && !dead
        && failure == null && Boolean.TRUE.equals(success);
  }

  private void stampRewindState(
      PlayerRef player,
      GameMode gameMode,
      boolean allowFlight,
      boolean flying,
      UUID cameraAnchorId
  ) {
    String encoded = gameMode.name()
        + ":" + (allowFlight ? "1" : "0")
        + ":" + (flying ? "1" : "0")
        + (cameraAnchorId == null ? "" : ":" + cameraAnchorId);
    player.data().set(REWIND_STAMP_KEY, encoded);
  }

  private void restoreStampedRewindState(PlayerRef player) {
    DataHolder data = player.data();
    String encoded = data.get(REWIND_STAMP_KEY);
    if (encoded == null) {
      return;
    }

    try {
      String[] parts = encoded.split(":", 4);
      if (parts.length < 3) {
        throw new IllegalArgumentException("Invalid rewind recovery state");
      }
      GameMode gameMode = GameMode.valueOf(parts[0]);
      boolean allowFlight = "1".equals(parts[1]);
      boolean flying = "1".equals(parts[2]);
      UUID cameraAnchorId = parts.length == 4 ? UUID.fromString(parts[3]) : null;
      if (player.gameMode() == GameMode.SPECTATOR) {
        player.setSpectatorTarget(null);
      }
      restoreOwnedPlayerState(player, gameMode, allowFlight, flying);
      EntityRef cameraAnchor = cameraAnchorId == null ? null : AdaptCore.platform().host().entity(cameraAnchorId);
      finishStampedRewindCleanup(player, cameraAnchor);
    } catch (RuntimeException error) {
      AdaptLog.warn("Failed to restore durable Instant Recall state for " + player.name() + ".");
      AdaptLog.error(error);
    }
  }

  private void finishStampedRewindCleanup(PlayerRef player, EntityRef cameraAnchor) {
    if (cameraAnchor == null) {
      player.data().remove(REWIND_STAMP_KEY);
      return;
    }

    Tasks.runEntity(cameraAnchor, () -> {
      if (cameraAnchor.isValid()) {
        cameraAnchor.remove();
      }
      Runnable clearStamp = () -> player.data().remove(REWIND_STAMP_KEY);
      if (Tasks.isOwnedByCurrentRegion(player)) {
        clearStamp.run();
      } else {
        Tasks.runEntity(player, clearStamp);
      }
    });
  }

  static boolean restoreOwnedPlayerState(
      PlayerRef player,
      GameMode gameMode,
      boolean allowFlight,
      boolean flying
  ) {
    if (player.gameMode() != GameMode.SPECTATOR) {
      return false;
    }
    player.setGameMode(gameMode);
    player.setAllowFlight(allowFlight);
    player.setFlying(allowFlight && flying);
    return true;
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }

    UUID id = p.uuid();
    long protectedUntil = rewindProtection.getOrDefault(id, 0L);
    if (rewinding.containsKey(id) || protectedUntil > M.ms()) {
      e.setCancelled(true);
      p.setNoDamageTicks(Math.max(p.noDamageTicks(), getConfig().rewindProtectionTicks));
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    if (!isRecallEligible(p)) {
      return;
    }

    captureSnapshot(p);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void onDoubleJumpMove(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    if ((!getConfig().enableDoubleJumpTrigger || !preferenceEnabled(p, DOUBLE_JUMP)) || !isRecallEligible(p)
        || !holdsRequiredClock(p)) {
      doubleJump.reset(p);
      return;
    }

    if (!doubleJump.update(p)) {
      return;
    }

    UUID id = p.uuid();
    if (!canTriggerDoubleJump(p) || cooldowns.getOrDefault(id, 0L) > M.ms()) {
      return;
    }

    attemptRecall(p);
  }

  @Override
  public void onTick() {
    long now = M.ms();
    for (UUID id : cooldownReadyNotify.keySet()) {
      PlayerRef p = AdaptCore.platform().host().player(id);
      if (p == null) {
        cooldownReadyNotify.remove(id);
        continue;
      }

      long cooldown = cooldowns.getOrDefault(id, 0L);
      if (cooldown <= now && cooldownReadyNotify.remove(id) != null) {
        Tasks.runEntity(p, () -> {
          if (p.isOnline() && (getConfig().playClockSounds && preferenceEnabled(p, CLOCK_SOUNDS))) {
            ChronosSoundFX.playCooldownReady(p);
          }
        });
      }
    }

    cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
    rewindProtection.entrySet().removeIf(entry -> entry.getValue() <= now);
    TELEPORT_XP_SUPPRESS_UNTIL.entrySet().removeIf(entry -> entry.getValue() <= now);
  }

  record RewindVisualState(PlayerRef player, Runnable cleanup) {
  }

  private record FinalRecall(
      UUID playerId,
      UUID operationId,
      Snapshot snapshot,
      double healthBefore,
      double healthAfter,
      int level,
      RecallXPContext xpContext
  ) {
  }

}
