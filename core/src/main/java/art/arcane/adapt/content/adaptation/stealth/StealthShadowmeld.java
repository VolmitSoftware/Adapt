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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.localization.catalog.StealthMessages;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class StealthShadowmeld extends SimpleAdaptation<StealthShadowmeld.Config> {
  public static final PlayerPreference<Gesture> GESTURE = new PlayerPreference<>(Gesture.class,
      new PlayerPreference.Definition<>("gesture", StealthMessages.STEALTHSHADOWMELD_PREFERENCE_GESTURE, Gesture.AUTOMATIC, List.of(
          new PlayerPreference.Choice<>(Gesture.AUTOMATIC, StealthMessages.STEALTHSHADOWMELD_PREFERENCE_GESTURE_AUTOMATIC, Id.minecraft("leather_boots"), 1),
          new PlayerPreference.Choice<>(Gesture.DOUBLE, StealthMessages.STEALTHSHADOWMELD_PREFERENCE_GESTURE_DOUBLE, Id.minecraft("rabbit_foot"), 1))));

  private final Map<UUID, Long> lastArmPress = playerState();
  private static final Set<UUID> MELDED = ConcurrentHashMap.newKeySet();
  private static final int INVISIBILITY_REFRESH_TICKS = 40;
  private static final Id INVISIBILITY = Id.minecraft("invisibility");
  private static final int HARD_MAX_ACTIVE_SESSIONS = 2_048;
  static final long CANDIDATE_SCAN_INTERVAL_MILLIS = 250L;
  static final int MAX_CANDIDATE_VISITS_PER_TICK = 256;
  private static final int MAX_SESSION_VISITS_PER_TICK = 64;

  private final StealthSessionCoordinator<MeldSession> coordinator =
      new StealthSessionCoordinator<>(HARD_MAX_ACTIVE_SESSIONS);
  private final StealthSmokePellet smokePellet;
  private final StealthCore stealth;
  private int candidateCursor;

  public StealthShadowmeld(StealthCore stealth, StealthSmokePellet smokePellet) {
    super("stealth-shadowmeld");
    this.stealth = Objects.requireNonNull(stealth);
    this.smokePellet = Objects.requireNonNull(smokePellet);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("sculk"));
    setInterval(CANDIDATE_SCAN_INTERVAL_MILLIS);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("black_wool"))
        .key("challenge_stealth_shadowmeld_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("sculk_shrieker"))
            .key("challenge_stealth_shadowmeld_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_stealth_shadowmeld_100", "stealth.shadowmeld.melds", 100, 350);
    registerMilestone("challenge_stealth_shadowmeld_1k", "stealth.shadowmeld.melds", 1000, 1500);
  }

  public static boolean isMelded(UUID playerId) {
    return playerId != null && MELDED.contains(playerId);
  }

  public static boolean isMelded(PlayerRef player) {
    return player != null && MELDED.contains(player.uuid());
  }

  static long computeMeldDelay(long startMillis, long endMillis, int level, int maxLevel) {
    long start = Math.max(0L, startMillis);
    long end = Math.max(0L, endMillis);
    if (maxLevel <= 1) {
      return end;
    }
    double progress = Math.max(0D, Math.min(1D, (level - 1D) / (maxLevel - 1D)));
    return Math.max(0L, Math.round(start + ((end - start) * progress)));
  }

  static boolean canRemainEligible(boolean sneaking, boolean undetected) {
    return sneaking && undetected;
  }

  static CandidateBatch candidateBatch(int candidateCount, int cursor) {
    int safeCount = Math.max(0, candidateCount);
    if (safeCount == 0) {
      return new CandidateBatch(0, 0, 0);
    }

    int start = cursor < 0 || cursor >= safeCount ? 0 : cursor;
    int end = Math.min(safeCount, start + MAX_CANDIDATE_VISITS_PER_TICK);
    int nextCursor = end >= safeCount ? 0 : end;
    return new CandidateBatch(start, end, nextCursor);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, GESTURE);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    endSession(coordinator.get(player.getPlayer().uuid()), true);
    lastArmPress.remove(player.getPlayer().uuid());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, C.YELLOW, "* ", Form.duration(getMeldDelay(level), 2), 1);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef player = e.player();
    if (!e.isSneaking()) {
      endSession(coordinator.get(player.uuid()), true);
      return;
    }
    if (preference(player, GESTURE) == Gesture.DOUBLE) {
      long now = System.currentTimeMillis();
      Long last = lastArmPress.put(player.uuid(), now);
      if (last == null || now - last > 350L) {
        return;
      }
      lastArmPress.remove(player.uuid());
    }
    startSessionIfEligible(player);
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(PlayerQuitEvent e) {
    endSession(coordinator.get(e.player().uuid()), false);
    MELDED.remove(e.player().uuid());
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(EntityTargetEvent e) {
    if (!(e.target() instanceof PlayerRef player) || !MELDED.contains(player.uuid())) {
      return;
    }
    MeldSession session = coordinator.get(player.uuid());
    if (session != null && session.melded) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void onAct(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof PlayerRef player)) {
      return;
    }
    resetSession(player, true);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void onHurt(EntityDamageEvent e) {
    if (e.entity() instanceof PlayerRef player && e.finalDamage() > 0D) {
      resetSession(player, true);
    }
  }

  @Handle
  public void onInteract(PlayerInteractEvent e) {
    resetSession(e.player(), true);
  }

  @Override
  public void onTick() {
    List<AdaptPlayer> candidates = learnedCandidates(System.currentTimeMillis());
    CandidateBatch batch = candidateBatch(candidates.size(), candidateCursor);
    candidateCursor = batch.nextCursor;
    for (int index = batch.start; index < batch.end; index++) {
      AdaptPlayer adaptPlayer = candidates.get(index);
      PlayerRef player = adaptPlayer.getPlayer();
      if (player == null) {
        continue;
      }
      Tasks.runEntity(player, () -> startSessionIfEligible(player));
    }

    for (MeldSession session : coordinator.takeRefreshes(MAX_SESSION_VISITS_PER_TICK)) {
      if (!session.processing.compareAndSet(false, true)) {
        continue;
      }
      if (!Tasks.runEntity(session.player, () -> evaluateSafely(session))) {
        session.processing.set(false);
        coordinator.remove(session.id, session);
        MELDED.remove(session.id);
      }
    }
  }

  private void evaluateSafely(MeldSession session) {
    try {
      evaluate(session);
    } finally {
      session.processing.set(false);
    }
  }

  private void evaluate(MeldSession session) {
    PlayerRef player = session.player;
    if (!coordinator.isCurrent(session.id, session) || !player.isOnline() || getActiveLevel(player) <= 0
        || !player.isSneaking()) {
      endSession(session, true);
      return;
    }

    boolean eligible = canRemainEligible(
        player.isSneaking(),
        smokePellet.isConcealed(player) || stealth.isCurrentlyUndetected(player)
    );
    if (!eligible) {
      resetMeld(session, session.melded);
      return;
    }

    long now = System.currentTimeMillis();
    if (session.eligibleSince == 0L) {
      session.eligibleSince = now;
    }
    if (!session.melded) {
      if (now - session.eligibleSince >= getMeldDelay(getActiveLevel(player))) {
        enterMeld(session);
      }
      return;
    }
    applyInvisibility(session);
  }

  private void startSessionIfEligible(PlayerRef player) {
    UUID playerId = player.uuid();
    if (!player.isOnline() || !player.isSneaking() || getActiveLevel(player) <= 0
        || coordinator.contains(playerId)) {
      return;
    }
    coordinator.admit(playerId, new MeldSession(player, playerId));
  }

  private void enterMeld(MeldSession session) {
    PlayerRef player = session.player;
    session.melded = true;
    MELDED.add(session.id);
    AbilityGlyphs.toggle(session.id, this, true);
    applyInvisibility(session);
    addStat(player, "stealth.shadowmeld.melds", 1);
    xp(player, getConfig().xpOnMeld);
    fx(player.location().add(0, 1.0D, 0), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 10, 0.3D)
        .ring(Particles.SQUID_INK, 0.6D, 8, 0.05D)
        .chord(Sounds.BLOCK_SCULK_SENSOR_CLICKING_STOP, 0.5F, 0.6F, Sounds.PARTICLE_SOUL_ESCAPE, 0.4F, 0.7F);
  }

  private void resetSession(PlayerRef player, boolean feedback) {
    MeldSession session = coordinator.get(player.uuid());
    if (session != null) {
      Tasks.runEntity(player, () -> resetMeld(session, feedback));
    }
  }

  private void resetMeld(MeldSession session, boolean feedback) {
    session.eligibleSince = 0L;
    if (!session.melded) {
      return;
    }
    session.melded = false;
    MELDED.remove(session.id);
    AbilityGlyphs.clear(session.id, this, HudGlyph.TOGGLE);
    removeInvisibility(session);
    PlayerRef player = session.player;
    if (feedback && player.isOnline()) {
      fx(player.location().add(0, 1.0D, 0), FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 6, 0.25D)
          .sound(Sounds.BLOCK_SCULK_SENSOR_CLICKING, 0.4F, 1.2F);
    }
  }

  private void applyInvisibility(MeldSession session) {
    PlayerRef player = session.player;
    EffectInstance current = player.effect(INVISIBILITY);
    if (current != null && current.durationTicks() > INVISIBILITY_REFRESH_TICKS + 5) {
      return;
    }
    player.addEffect(new EffectInstance(
        INVISIBILITY,
        INVISIBILITY_REFRESH_TICKS,
        0,
        false,
        false,
        false
    ));
    session.appliedInvisibility = true;
  }

  private void removeInvisibility(MeldSession session) {
    if (!session.appliedInvisibility) {
      return;
    }
    session.appliedInvisibility = false;
    PlayerRef player = session.player;
    if (!player.isOnline() || smokePellet.isConcealed(player)) {
      return;
    }
    EffectInstance current = player.effect(INVISIBILITY);
    if (current != null && current.amplifier() == 0
        && current.durationTicks() <= INVISIBILITY_REFRESH_TICKS + 5) {
      player.removeEffect(INVISIBILITY);
    }
  }

  private void endSession(MeldSession session, boolean feedback) {
    if (session == null || !coordinator.remove(session.id, session)) {
      return;
    }
    resetMeld(session, feedback);
  }

  @Override
  public void unregister() {
    List<MeldSession> sessions = coordinator.clear();
    MELDED.clear();
    for (MeldSession session : sessions) {
      Tasks.runEntity(session.player, () -> removeInvisibility(session));
    }
    super.unregister();
  }

  private long getMeldDelay(int level) {
    return computeMeldDelay(
        getConfig().meldDelayStartMillis,
        getConfig().meldDelayEndMillis,
        level,
        getMaxLevel()
    );
  }

  record CandidateBatch(int start, int end, int nextCursor) {
    int size() {
      return end - start;
    }
  }

  private static final class MeldSession {
    private final PlayerRef player;
    private final UUID id;
    private final AtomicBoolean processing = new AtomicBoolean();
    private volatile long eligibleSince;
    private volatile boolean melded;
    private volatile boolean appliedInvisibility;

    private MeldSession(PlayerRef player, UUID id) {
      this.player = player;
      this.id = id;
    }
  }

  @ConfigDescription("Remain sneaking and undetected to become invisible; detection, acting, taking damage, or standing ends the meld.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Delay before melding at level one, in milliseconds.", impact = "Higher values make the first level take longer to become invisible.")
    long meldDelayStartMillis = 3000L;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Delay before melding at maximum level, in milliseconds.", impact = "Lower values make maximum-level Shadowmeld activate faster.")
    long meldDelayEndMillis = 250L;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Experience granted the moment you meld into shadow.", impact = "Higher values level the adaptation faster.")
    double xpOnMeld = 6;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      maxLevel = 4;
      initialCost = 3;
    }
  }

  public enum Gesture { AUTOMATIC, DOUBLE }
}
