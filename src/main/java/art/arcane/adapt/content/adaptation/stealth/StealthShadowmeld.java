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
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class StealthShadowmeld extends SimpleAdaptation<StealthShadowmeld.Config> {
  private static final java.util.Set<UUID> MELDED = ConcurrentHashMap.newKeySet();
  private static final double MOVE_EPSILON_SQ = 0.01D;
  private static final int INVISIBILITY_REFRESH_TICKS = 40;
  private static final int MAX_SESSION_VISITS_PER_TICK = 32;
  private static final int MAX_TARGET_CLEARS = 24;

  private final Map<UUID, MeldSession> sessions;

  public StealthShadowmeld() {
    super("stealth-shadowmeld");
    registerConfiguration(Config.class);
    setIcon(Material.SCULK);
    setInterval(250);
    sessions = playerState();
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

  static double computeLightThreshold(double base, double factor, double percent) {
    return base + (percent * factor);
  }

  static long computeMeldDelay(double base, double factor, double percent) {
    return Math.max(400L, Math.round(base - (percent * factor)));
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getLightThreshold(level), 0), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getMeldDelay(level), 1), 2);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    UUID id = p.getUniqueId();
    if (!e.isSneaking()) {
      MeldSession existing = sessions.get(id);
      if (existing != null) {
        endSession(existing, true);
      }
      return;
    }

    if (getActiveLevel(p) <= 0) {
      return;
    }

    Location loc = p.getLocation();
    MeldSession session = new MeldSession(p, id, loc);
    sessions.put(id, session);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    MeldSession session = sessions.get(e.getPlayer().getUniqueId());
    if (session != null) {
      endSession(session, false);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(EntityTargetLivingEntityEvent e) {
    if (e.getTarget() instanceof Player player && MELDED.contains(player.getUniqueId())) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onAct(EntityDamageByEntityEvent e) {
    if (e.getDamager() instanceof Player p && MELDED.contains(p.getUniqueId())) {
      MeldSession session = sessions.get(p.getUniqueId());
      if (session != null) {
        J.runEntity(p, () -> breakMeld(session, true));
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onHurt(EntityDamageEvent e) {
    if (e.getEntity() instanceof Player p && MELDED.contains(p.getUniqueId())) {
      MeldSession session = sessions.get(p.getUniqueId());
      if (session != null) {
        J.runEntity(p, () -> breakMeld(session, true));
      }
    }
  }

  @EventHandler
  public void onInteract(PlayerInteractEvent e) {
    Player p = e.getPlayer();
    if (!MELDED.contains(p.getUniqueId())) {
      return;
    }
    MeldSession session = sessions.get(p.getUniqueId());
    if (session != null) {
      J.runEntity(p, () -> breakMeld(session, true));
    }
  }

  @Override
  public void onTick() {
    if (sessions.isEmpty()) {
      return;
    }

    int visited = 0;
    for (MeldSession session : sessions.values()) {
      if (visited++ >= MAX_SESSION_VISITS_PER_TICK) {
        break;
      }
      if (!session.processing.compareAndSet(false, true)) {
        continue;
      }
      if (!J.runEntity(session.player, () -> {
        try {
          evaluate(session);
        } finally {
          session.processing.set(false);
        }
      })) {
        session.processing.set(false);
        endSession(session, false);
      }
    }
  }

  private void evaluate(MeldSession session) {
    Player p = session.player;
    UUID id = session.id;
    if (sessions.get(id) != session || !p.isOnline() || !p.isSneaking() || getActiveLevel(p) <= 0) {
      endSession(session, true);
      return;
    }

    Location loc = p.getLocation();
    double dx = loc.getX() - session.lastX;
    double dy = loc.getY() - session.lastY;
    double dz = loc.getZ() - session.lastZ;
    long now = System.currentTimeMillis();
    if ((dx * dx) + (dy * dy) + (dz * dz) > MOVE_EPSILON_SQ) {
      session.lastX = loc.getX();
      session.lastY = loc.getY();
      session.lastZ = loc.getZ();
      session.stillSince = now;
      if (session.melded) {
        breakMeld(session, true);
      }
      return;
    }

    int level = getActiveLevel(p);
    double percent = getLevelPercent(level);
    double light = p.getEyeLocation().getBlock().getLightLevel();
    if (light > computeLightThreshold(getConfig().lightThresholdBase, getConfig().lightThresholdFactor, percent)) {
      if (session.melded) {
        breakMeld(session, true);
      }
      return;
    }

    if (!session.melded) {
      if (now - session.stillSince >= computeMeldDelay(getConfig().meldDelayBase, getConfig().meldDelayFactor, percent)) {
        enterMeld(session);
      }
      return;
    }

    applyInvisibility(p);
  }

  private void enterMeld(MeldSession session) {
    Player p = session.player;
    session.melded = true;
    MELDED.add(session.id);
    applyInvisibility(p);
    clearNearbyTargets(p);
    addStat(p, "stealth.shadowmeld.melds", 1);
    xp(p, getConfig().xpOnMeld);
    fx(p.getLocation().add(0, 1.0D, 0), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 10, 0.3D)
        .ring(Particle.SQUID_INK, 0.6D, 8, 0.05D)
        .chord(Sound.BLOCK_SCULK_SENSOR_CLICKING_STOP, 0.5F, 0.6F, Sound.PARTICLE_SOUL_ESCAPE, 0.4F, 0.7F);
  }

  private void breakMeld(MeldSession session, boolean feedback) {
    if (!session.melded) {
      return;
    }
    session.melded = false;
    MELDED.remove(session.id);
    session.stillSince = System.currentTimeMillis();
    Player p = session.player;
    removeInvisibility(p);
    if (feedback && p.isOnline()) {
      fx(p.getLocation().add(0, 1.0D, 0), FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 6, 0.25D)
          .sound(Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.4F, 1.2F);
    }
  }

  private void applyInvisibility(Player p) {
    PotionEffect current = p.getPotionEffect(PotionEffectType.INVISIBILITY);
    if (current != null && current.getDuration() > INVISIBILITY_REFRESH_TICKS + 5) {
      return;
    }
    p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, INVISIBILITY_REFRESH_TICKS, 0, false, false, false), true);
  }

  private void removeInvisibility(Player p) {
    if (!p.isOnline()) {
      return;
    }
    PotionEffect current = p.getPotionEffect(PotionEffectType.INVISIBILITY);
    if (current != null && current.getAmplifier() == 0 && current.getDuration() <= INVISIBILITY_REFRESH_TICKS + 5) {
      p.removePotionEffect(PotionEffectType.INVISIBILITY);
    }
  }

  private void clearNearbyTargets(Player p) {
    double radius = Math.max(4D, getConfig().meldClearRadius);
    int budget = MAX_TARGET_CLEARS;
    for (Entity entity : p.getNearbyEntities(radius, radius, radius)) {
      if (!(entity instanceof Mob mob)) {
        continue;
      }
      if (mob.getTarget() != p) {
        continue;
      }
      J.runEntity(mob, () -> {
        if (mob.getTarget() == p) {
          mob.setTarget(null);
        }
      });
      if (--budget <= 0) {
        break;
      }
    }
  }

  private void endSession(MeldSession session, boolean feedback) {
    sessions.remove(session.id, session);
    breakMeld(session, feedback);
  }

  @Override
  public void unregister() {
    sessions.clear();
    MELDED.clear();
    super.unregister();
  }

  private double getLightThreshold(int level) {
    return computeLightThreshold(getConfig().lightThresholdBase, getConfig().lightThresholdFactor, getLevelPercent(level));
  }

  private double getMeldDelay(int level) {
    return computeMeldDelay(getConfig().meldDelayBase, getConfig().meldDelayFactor, getLevelPercent(level));
  }

  private static final class MeldSession {
    private final Player player;
    private final UUID id;
    private final AtomicBoolean processing = new AtomicBoolean();
    private volatile double lastX;
    private volatile double lastY;
    private volatile double lastZ;
    private volatile long stillSince;
    private volatile boolean melded;

    private MeldSession(Player player, UUID id, Location location) {
      this.player = player;
      this.id = id;
      lastX = location.getX();
      lastY = location.getY();
      lastZ = location.getZ();
      stillSince = System.currentTimeMillis();
    }
  }

  @ConfigDescription("Standing still while sneaking in low light melds you into the shadows, hiding you from mobs until you move or act.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base maximum light level that still allows melding.", impact = "Higher values let you meld in brighter areas.")
    double lightThresholdBase = 7.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional light tolerance gained across levels.", impact = "Higher values raise the light ceiling more at higher levels.")
    double lightThresholdFactor = 5.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base delay before melding after standing still, in milliseconds.", impact = "Higher values make melding take longer to trigger.")
    double meldDelayBase = 2200;
    @art.arcane.adapt.util.config.ConfigDoc(value = "How much the meld delay is reduced by leveling, in milliseconds.", impact = "Higher values make higher levels meld faster.")
    double meldDelayFactor = 1400;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Radius scanned to drop existing mob aggro when you meld.", impact = "Higher values pull attention off you from farther away when melding.")
    double meldClearRadius = 12.0;
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
