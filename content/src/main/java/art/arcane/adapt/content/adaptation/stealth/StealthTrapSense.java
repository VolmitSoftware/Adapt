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

package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.viewer.ViewerDisplayPort;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockReceiveGameEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.api.world.BlockScan;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class StealthTrapSense extends SimpleAdaptation<StealthTrapSense.Config> {
  public static final Id ID = Id.adapt("stealth/trap_sense");
  public static final PlayerPreference<CommonPreferences.Toggle> OUTLINES = CommonPreferences.toggle("outlines", Text.key("adapt.stealthtrapsense.preferences.preference_outlines"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> CHESTS = CommonPreferences.toggle("chests", Text.key("adapt.stealthtrapsense.preferences.preference_chests"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> TRIPWIRES = CommonPreferences.toggle("tripwires", Text.key("adapt.stealthtrapsense.preferences.preference_tripwires"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> PLATES = CommonPreferences.toggle("plates", Text.key("adapt.stealthtrapsense.preferences.preference_plates"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> SCULK = CommonPreferences.toggle("sculk", Text.key("adapt.stealthtrapsense.preferences.preference_sculk"), CommonPreferences.Toggle.ON);

  private static final int MAX_SESSION_VISITS_PER_TICK = 32;
  private static final int MAX_BLOCKS_PER_SCAN = 4096;
  private static final int MAX_MARKERS_PER_SCAN = 96;
  private static final long SCULK_STATE_LIFETIME_MILLIS = 1_500L;
  private static final Id TRAPPED_CHEST = Id.minecraft("trapped_chest");
  private static final Id TRIPWIRE = Id.minecraft("tripwire");
  private static final Id TRIPWIRE_HOOK = Id.minecraft("tripwire_hook");
  private static final Id SCULK_SENSOR = Id.minecraft("sculk_sensor");
  private static final Id CALIBRATED_SCULK_SENSOR = Id.minecraft("calibrated_sculk_sensor");
  private static final Id SCULK_SHRIEKER = Id.minecraft("sculk_shrieker");
  private static final Set<Id> MOVEMENT_GAME_EVENTS = Set.of(
      Id.minecraft("step"),
      Id.minecraft("swim"),
      Id.minecraft("flap"),
      Id.minecraft("hit_ground"),
      Id.minecraft("elytra_glide"),
      Id.minecraft("splash"),
      Id.minecraft("bounce"),
      Id.minecraft("teleport"),
      Id.minecraft("entity_mount"),
      Id.minecraft("entity_dismount"));

  private final Map<UUID, TrapSession> sessions;
  private final Map<UUID, SculkState> sculkStates = new ConcurrentHashMap<>();

  public StealthTrapSense() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(TRIPWIRE_HOOK);
    setInterval(400);
    sessions = playerState();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("sculk_sensor"))
        .key("challenge_stealth_trap_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("sculk_shrieker"))
            .key("challenge_stealth_trap_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_stealth_trap_500", "stealth.trap-sense.traps-revealed", 500, 400);
    registerMilestone("challenge_stealth_trap_5k", "stealth.trap-sense.traps-revealed", 5000, 1500);
  }

  static boolean matchesTrap(Id type, boolean chests, boolean tripwires, boolean plates, boolean sculk) {
    if (TRAPPED_CHEST.equals(type)) {
      return chests;
    }
    if (TRIPWIRE.equals(type) || TRIPWIRE_HOOK.equals(type)) {
      return tripwires;
    }
    if (isSculkTrap(type)) {
      return sculk;
    }
    return plates && isPressurePlate(type);
  }

  static boolean isTrapBlock(Id type) {
    return TRAPPED_CHEST.equals(type) || TRIPWIRE.equals(type) || TRIPWIRE_HOOK.equals(type) || isSculkTrap(type)
        || isPressurePlate(type);
  }

  static boolean isSculkTrap(Id type) {
    return SCULK_SENSOR.equals(type) || CALIBRATED_SCULK_SENSOR.equals(type) || SCULK_SHRIEKER.equals(type);
  }

  private static boolean isPressurePlate(Id type) {
    return type != null && type.path().endsWith("_pressure_plate");
  }

  static double computeRange(double base, double factor, double percent) {
    return Math.max(3D, Math.min(8D, base + (percent * factor)));
  }

  static double computeMercy(double maxChance, int level, int maxLevel) {
    if (maxLevel > 0 && level >= maxLevel) {
      return 1D;
    }
    double percent = maxLevel <= 0 ? 0D : Math.max(0D, Math.min(1D, level / (double) maxLevel));
    return Math.max(0D, Math.min(1D, maxChance * percent));
  }

  static boolean isMovementGameEvent(Id event) {
    return event != null && MOVEMENT_GAME_EVENTS.contains(event);
  }

  static boolean shouldSuppressMovement(int level, int maxLevel, boolean sneaking, double mercy, double roll) {
    if (level <= 0 || maxLevel <= 0) {
      return false;
    }
    if (level >= maxLevel) {
      return true;
    }
    return sneaking && Double.isFinite(roll) && roll >= 0D && roll < mercy;
  }

  static long blockKey(int x, int y, int z) {
    return (((long) x & 0x3FFFFFFL) << 38) | (((long) z & 0x3FFFFFFL) << 12) | ((long) y & 0xFFFL);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, OUTLINES, CHESTS, TRIPWIRES, PLATES, SCULK);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    PlayerRef p = player.player();
    UUID id = p.uuid();
    sessions.remove(id);
    BlockScan.cancel(this, id);
    displays().clearViewer(getId().toString(), id);
    cacheSculkState(p, p.isSneaking());
    if (isPlayerEnabled(p) && p.isSneaking()) {
      sessions.put(id, new TrapSession(p, id));
    }
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getRange(level), 1), 1);
    statLore(v, TextFormat.YELLOW, "* ", Format.pc(getMercy(level), 0), 2);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    UUID id = p.uuid();
    cacheSculkState(p, e.isSneaking());
    if (!e.isSneaking()) {
      sessions.remove(id);
      BlockScan.cancel(this, id);
      displays().clearViewer(getId().toString(), id);
      return;
    }

    if (getActiveLevel(p) <= 0) {
      return;
    }

    sessions.putIfAbsent(id, new TrapSession(p, id));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    sessions.remove(e.player().uuid());
    sculkStates.remove(e.player().uuid());
    BlockScan.cancel(this, e.player().uuid());
    displays().clearViewer(getId().toString(), e.player().uuid());
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockReceiveGameEvent e) {
    Id type = e.block().type();
    if (!isSculkTrap(type)) {
      return;
    }

    if (!(e.entity() instanceof PlayerRef p) || !isMovementGameEvent(e.gameEvent())) {
      return;
    }

    UUID playerId = p.uuid();
    SculkState state = sculkStates.get(playerId);
    long now = System.currentTimeMillis();
    if (state == null) {
      return;
    }
    if (now > state.expiresAt()) {
      sculkStates.remove(playerId, state);
      return;
    }

    if (shouldSuppressMovement(
        state.level(),
        state.maxLevel(),
        state.sneaking(),
        state.mercy(),
        ThreadLocalRandom.current().nextDouble()
    )) {
      e.setCancelled(true);
      showTrap(p, e.block().location(), getMarkerDurationTicks());
      AdaptApi.scheduler().runEntity(p, () -> fx(p, FxPriority.AMBIENT)
          .sound(Sounds.BLOCK_SCULK_SENSOR_CLICKING_STOP, 0.25F, 1.4F));
    }
  }

  @Override
  public void onTick() {
    refreshSculkStates();
    if (sessions.isEmpty()) {
      return;
    }

    long now = System.currentTimeMillis();
    int visited = 0;
    for (TrapSession session : sessions.values()) {
      if (visited++ >= MAX_SESSION_VISITS_PER_TICK) {
        break;
      }
      if (now < session.nextScanAt || !session.processing.compareAndSet(false, true)) {
        continue;
      }
      if (!AdaptApi.scheduler().runEntity(session.player, () -> scan(session))) {
        session.processing.set(false);
        sessions.remove(session.id, session);
      }
    }
  }

  private void scan(TrapSession session) {
    PlayerRef p = session.player;
    UUID id = session.id;
    session.nextScanAt = System.currentTimeMillis() + Math.max(200L, getConfig().scanIntervalMillis);
    if (sessions.get(id) != session || !p.isOnline() || !p.isSneaking() || getActiveLevel(p) <= 0) {
      sessions.remove(id, session);
      session.processing.set(false);
      return;
    }

    int level = getActiveLevel(p);
    int r = (int) Math.ceil(getRange(level));
    Loc base = p.location();
    boolean chests = preferenceEnabled(p, CHESTS);
    boolean tripwires = preferenceEnabled(p, TRIPWIRES);
    boolean plates = preferenceEnabled(p, PLATES);
    boolean sculk = preferenceEnabled(p, SCULK);
    BlockScan request = BlockScan.around(base)
        .radius(r)
        .denseRadius(r)
        .maxSamples(MAX_BLOCKS_PER_SCAN)
        .seed(0)
        .maxResults(MAX_MARKERS_PER_SCAN)
        .matchingBlocks((BlockRef block) -> matchesTrap(block.type(), chests, tripwires, plates, sculk))
        .onComplete((BlockScan.Result result) -> completeScan(session, result))
        .build();
    session.scanId = request.submit(this, id);
  }

  private void completeScan(TrapSession session, BlockScan.Result result) {
    if (!result.scan().equals(session.scanId)) {
      session.processing.set(false);
      return;
    }
    if (!AdaptApi.scheduler().runEntity(session.player, () -> completeScanOwned(session, result))) {
      session.processing.set(false);
      sessions.remove(session.id, session);
    }
  }

  private void completeScanOwned(TrapSession session, BlockScan.Result result) {
    try {
      PlayerRef player = session.player;
      if (sessions.get(session.id) != session || !player.isOnline() || !player.isSneaking()
          || getActiveLevel(player) <= 0) {
        sessions.remove(session.id, session);
        return;
      }

      int newlyRevealed = 0;
      Set<Long> inRange = new HashSet<>();
      List<BlockScan.Match> matches = result.matches();
      for (BlockScan.Match match : matches) {
        long key = blockKey(match.x(), match.y(), match.z());
        inRange.add(key);
        if (session.countedTraps.add(key)) {
          newlyRevealed++;
        }
        showTrap(
            player,
            new Loc(result.world(), match.x(), match.y(), match.z()),
            getMarkerDurationTicks()
        );
      }
      session.countedTraps.retainAll(inRange);
      finishScan(player, newlyRevealed);
    } finally {
      session.processing.set(false);
    }
  }

  private void finishScan(PlayerRef p, int revealed) {
    if (revealed > 0) {
      addStat(p, "stealth.trap-sense.traps-revealed", revealed);
    }
  }

  private void showTrap(PlayerRef player, Loc location, int durationTicks) {
    Loc anchor = location;
    AdaptApi.scheduler().runEntity(player, () -> {
      if (!isPlayerEnabled(player) || !preferenceEnabled(player, OUTLINES)) {
        return;
      }
      boolean chests = preferenceEnabled(player, CHESTS);
      boolean tripwires = preferenceEnabled(player, TRIPWIRES);
      boolean plates = preferenceEnabled(player, PLATES);
      boolean sculk = preferenceEnabled(player, SCULK);
      AdaptApi.scheduler().runAt(anchor, () -> {
      BlockRef block = anchor.world().blockAt(anchor);
      Id currentType = block.type();
      if (!matchesTrap(currentType, chests, tripwires, plates, sculk)) {
        return;
      }
      displays().showBlock(
          getId().toString(),
          "trap-" + anchor.blockX() + ":" + anchor.blockY() + ":" + anchor.blockZ(),
          player,
          anchor,
          block.state(),
          trapColor(currentType),
          durationTicks
      );
      });
    });
  }

  private Rgb trapColor(Id type) {
    if (isSculkTrap(type)) {
      return new Rgb(40, 220, 210);
    }
    if (TRIPWIRE.equals(type) || TRIPWIRE_HOOK.equals(type)) {
      return new Rgb(255, 220, 45);
    }
    return new Rgb(255, 70, 70);
  }

  @Override
  public void unregister() {
    sessions.clear();
    sculkStates.clear();
    BlockScan.cancelAll(this);
    displays().clearChannel(getId().toString());
    super.unregister();
  }

  private static ViewerDisplayPort displays() {
    return ViewerDisplayPort.current();
  }

  private int getMarkerDurationTicks() {
    long scanMillis = Math.max(200L, getConfig().scanIntervalMillis);
    return Math.max(20, (int) Math.min(200L, (scanMillis / 50L) + 10L));
  }

  private double getRange(int level) {
    return computeRange(getConfig().rangeBase, getConfig().rangeFactor, getLevelPercent(level));
  }

  private double getMercy(int level) {
    return computeMercy(getConfig().mercyMaxChance, level, getMaxLevel());
  }

  private void refreshSculkStates() {
    long now = System.currentTimeMillis();
    for (Map.Entry<UUID, SculkState> entry : sculkStates.entrySet()) {
      if (now > entry.getValue().expiresAt()) {
        sculkStates.remove(entry.getKey(), entry.getValue());
      }
    }
    for (PlayerProfile adaptPlayer : learnedCandidates(now)) {
      PlayerRef player = adaptPlayer.player();
      if (player != null && player.isOnline()) {
        AdaptApi.scheduler().runEntity(player, () -> cacheSculkState(player, player.isSneaking()));
      }
    }
  }

  private void cacheSculkState(PlayerRef player, boolean sneaking) {
    UUID playerId = player.uuid();
    int level = getActiveLevel(player);
    if (level <= 0) {
      sculkStates.remove(playerId);
      return;
    }
    sculkStates.put(playerId, new SculkState(
        level,
        getMaxLevel(),
        sneaking,
        getMercy(level),
        System.currentTimeMillis() + SCULK_STATE_LIFETIME_MILLIS
    ));
  }

  private static final class TrapSession {
    private final PlayerRef player;
    private final UUID id;
    private final AtomicBoolean processing = new AtomicBoolean();
    private final Set<Long> countedTraps = new HashSet<>();
    private volatile long nextScanAt;
    private volatile UUID scanId;

    private TrapSession(PlayerRef player, UUID id) {
      this.player = player;
      this.id = id;
    }
  }

  private record SculkState(int level, int maxLevel, boolean sneaking, double mercy, long expiresAt) {
  }

  @ConfigDescription("While sneaking, nearby trapped chests, tripwires, hooks, pressure plates, and sculk blocks glow; maximum level suppresses all movement vibrations.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base detection range for revealing traps, in blocks.", impact = "Higher values reveal traps from farther away.")
    double rangeBase = 4.0;
    @ConfigDoc(value = "Extra detection range gained across levels, in blocks.", impact = "Higher values extend detection range more per level; capped at 8 blocks.")
    double rangeFactor = 4.0;
    @ConfigDoc(value = "Suppression chance used below maximum level.", impact = "Higher values let lower levels avoid movement vibrations more often; maximum level is always fully suppressed.")
    double mercyMaxChance = 0.7;
    @ConfigDoc(value = "Milliseconds between trap scans while sneaking.", impact = "Lower values refresh glimmers faster; higher values reduce block scan cost.")
    long scanIntervalMillis = 500;

    public Config() {
      baseCost = 1;
      costFactor = 0.5;
      maxLevel = 4;
      initialCost = 1;
    }
  }
}
