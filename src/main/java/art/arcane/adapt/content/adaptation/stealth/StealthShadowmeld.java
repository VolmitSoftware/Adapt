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
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class StealthShadowmeld extends SimpleAdaptation<StealthShadowmeld.Config> {
  private static final Set<UUID> MELDED = ConcurrentHashMap.newKeySet();
  private static final int INVISIBILITY_REFRESH_TICKS = 40;
  private static final int HARD_MAX_ACTIVE_SESSIONS = 2_048;
  private static final int MAX_SESSION_VISITS_PER_TICK = 64;

  private final StealthSessionCoordinator<MeldSession> coordinator =
      new StealthSessionCoordinator<>(HARD_MAX_ACTIVE_SESSIONS);
  private final StealthSmokePellet smokePellet;
  private final StealthCore stealth;

  public StealthShadowmeld(StealthCore stealth, StealthSmokePellet smokePellet) {
    super("stealth-shadowmeld");
    this.stealth = Objects.requireNonNull(stealth);
    this.smokePellet = Objects.requireNonNull(smokePellet);
    registerConfiguration(Config.class);
    setIcon(Material.SCULK);
    setInterval(250);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BLACK_WOOL)
        .key("challenge_stealth_shadowmeld_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.SCULK_SHRIEKER)
            .key("challenge_stealth_shadowmeld_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_stealth_shadowmeld_100", "stealth.shadowmeld.melds", 100, 350);
    registerMilestone("challenge_stealth_shadowmeld_1k", "stealth.shadowmeld.melds", 1000, 1500);
  }

  public static boolean isMelded(UUID playerId) {
    return playerId != null && MELDED.contains(playerId);
  }

  public static boolean isMelded(Player player) {
    return player != null && MELDED.contains(player.getUniqueId());
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

  @Override
  public void addStats(int level, Element v) {
    statLore(v, C.YELLOW, "* ", Form.duration(getMeldDelay(level), 2), 1);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    Player player = e.getPlayer();
    if (!e.isSneaking()) {
      endSession(coordinator.get(player.getUniqueId()), true);
      return;
    }
    startSessionIfEligible(player);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(PlayerQuitEvent e) {
    endSession(coordinator.get(e.getPlayer().getUniqueId()), false);
    MELDED.remove(e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(EntityTargetLivingEntityEvent e) {
    if (!(e.getTarget() instanceof Player player) || !MELDED.contains(player.getUniqueId())) {
      return;
    }
    MeldSession session = coordinator.get(player.getUniqueId());
    if (session != null && session.melded) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onAct(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Player player)) {
      return;
    }
    resetSession(player, true);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onHurt(EntityDamageEvent e) {
    if (e.getEntity() instanceof Player player && e.getFinalDamage() > 0D) {
      resetSession(player, true);
    }
  }

  @EventHandler
  public void onInteract(PlayerInteractEvent e) {
    resetSession(e.getPlayer(), true);
  }

  @Override
  public void onTick() {
    for (AdaptPlayer adaptPlayer : learnedCandidates(System.currentTimeMillis())) {
      Player player = adaptPlayer.getPlayer();
      if (player == null || !player.isOnline() || !player.isSneaking()
          || coordinator.contains(player.getUniqueId())) {
        continue;
      }
      J.runEntity(player, () -> startSessionIfEligible(player));
    }

    for (MeldSession session : coordinator.takeRefreshes(MAX_SESSION_VISITS_PER_TICK)) {
      if (!session.processing.compareAndSet(false, true)) {
        continue;
      }
      if (!J.runEntity(session.player, () -> evaluateSafely(session))) {
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
    Player player = session.player;
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

  private void startSessionIfEligible(Player player) {
    UUID playerId = player.getUniqueId();
    if (!player.isOnline() || !player.isSneaking() || getActiveLevel(player) <= 0
        || coordinator.contains(playerId)) {
      return;
    }
    coordinator.admit(playerId, new MeldSession(player, playerId));
  }

  private void enterMeld(MeldSession session) {
    Player player = session.player;
    session.melded = true;
    MELDED.add(session.id);
    applyInvisibility(session);
    addStat(player, "stealth.shadowmeld.melds", 1);
    xp(player, getConfig().xpOnMeld);
    fx(player.getLocation().add(0, 1.0D, 0), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 10, 0.3D)
        .ring(Particle.SQUID_INK, 0.6D, 8, 0.05D)
        .chord(Sound.BLOCK_SCULK_SENSOR_CLICKING_STOP, 0.5F, 0.6F, Sound.PARTICLE_SOUL_ESCAPE, 0.4F, 0.7F);
  }

  private void resetSession(Player player, boolean feedback) {
    MeldSession session = coordinator.get(player.getUniqueId());
    if (session != null) {
      J.runEntity(player, () -> resetMeld(session, feedback));
    }
  }

  private void resetMeld(MeldSession session, boolean feedback) {
    session.eligibleSince = 0L;
    if (!session.melded) {
      return;
    }
    session.melded = false;
    MELDED.remove(session.id);
    removeInvisibility(session);
    Player player = session.player;
    if (feedback && player.isOnline()) {
      fx(player.getLocation().add(0, 1.0D, 0), FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 6, 0.25D)
          .sound(Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.4F, 1.2F);
    }
  }

  private void applyInvisibility(MeldSession session) {
    Player player = session.player;
    PotionEffect current = player.getPotionEffect(PotionEffectType.INVISIBILITY);
    if (current != null && current.getDuration() > INVISIBILITY_REFRESH_TICKS + 5) {
      return;
    }
    player.addPotionEffect(new PotionEffect(
        PotionEffectType.INVISIBILITY,
        INVISIBILITY_REFRESH_TICKS,
        0,
        false,
        false,
        false
    ), true);
    session.appliedInvisibility = true;
  }

  private void removeInvisibility(MeldSession session) {
    if (!session.appliedInvisibility) {
      return;
    }
    session.appliedInvisibility = false;
    Player player = session.player;
    if (!player.isOnline() || smokePellet.isConcealed(player)) {
      return;
    }
    PotionEffect current = player.getPotionEffect(PotionEffectType.INVISIBILITY);
    if (current != null && current.getAmplifier() == 0
        && current.getDuration() <= INVISIBILITY_REFRESH_TICKS + 5) {
      player.removePotionEffect(PotionEffectType.INVISIBILITY);
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
      J.runEntity(session.player, () -> removeInvisibility(session));
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

  private static final class MeldSession {
    private final Player player;
    private final UUID id;
    private final AtomicBoolean processing = new AtomicBoolean();
    private volatile long eligibleSince;
    private volatile boolean melded;
    private volatile boolean appliedInvisibility;

    private MeldSession(Player player, UUID id) {
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
      costFactor = 0.4;
      maxLevel = 4;
      initialCost = 4;
    }
  }
}
