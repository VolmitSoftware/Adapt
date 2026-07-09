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

package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class DiscoveryInsight extends SimpleAdaptation<DiscoveryInsight.Config> {
  private static final String BAR_SEGMENT = "❚";

  private final Map<UUID, InsightHud> huds = new ConcurrentHashMap<>();
  private final Cooldowns xpCooldowns = cooldowns();

  public DiscoveryInsight() {
    super("discovery-insight");
    registerConfiguration(Config.class);
    setIcon(Material.SPYGLASS);
    setInterval(250);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SPYGLASS)
        .key("challenge_discovery_insight_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.WRITABLE_BOOK)
            .key("challenge_discovery_insight_1000")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_discovery_insight_100", "discovery.insight.entities-inspected", 100, 300);
    registerMilestone("challenge_discovery_insight_1000", "discovery.insight.entities-inspected", 1000, 1200);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRange(level), 0), 1);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!getConfig().showDamageNumbers || !(e.getEntity() instanceof LivingEntity victim) || victim instanceof ArmorStand) {
      return;
    }

    Player attacker;
    boolean crit;
    if (e.getDamager() instanceof Player pl) {
      attacker = pl;
      crit = pl.getFallDistance() > 0 && !pl.isOnGround();
    } else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player pl) {
      attacker = pl;
      crit = pr instanceof AbstractArrow arrow && arrow.isCritical();
    } else {
      return;
    }

    if (attacker == victim || !hasActiveAdaptation(attacker)) {
      return;
    }

    double damage = e.getFinalDamage();
    if (damage <= 0) {
      return;
    }

    spawnDamageNumber(attacker, victim, damage, crit);
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    clearHud(e.getPlayer().getUniqueId());
  }

  @Override
  public void onTick() {
    for (art.arcane.adapt.api.world.AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
      Player p = adaptPlayer.getPlayer();
      if (p == null || !p.isOnline()) {
        continue;
      }

      if (getActiveLevel(p) <= 0 && !huds.containsKey(p.getUniqueId())) {
        continue;
      }

      J.runEntity(p, () -> updateHud(p));
    }

    for (UUID id : huds.keySet()) {
      Player owner = Bukkit.getPlayer(id);
      if (owner == null || !owner.isOnline()) {
        clearHud(id);
      }
    }
  }

  private void updateHud(Player p) {
    UUID id = p.getUniqueId();
    int level = getActiveLevel(p);
    if (level <= 0) {
      clearHud(id);
      return;
    }

    LivingEntity target = findLookTarget(p, getRange(level));
    if (target == null) {
      clearHud(id);
      return;
    }

    InsightHud hud = huds.get(id);
    if (hud != null && hud.entityId == target.getEntityId()) {
      refreshHud(p, hud, target);
      return;
    }

    clearHud(id);
    spawnHud(p, target);
    if (xpCooldowns.isReady(id, getConfig().xpCooldownMs)) {
      xpCooldowns.mark(id);
      xp(p, getConfig().xpPerInspection);
      addStat(p, "discovery.insight.entities-inspected", 1);
    }
  }

  private LivingEntity findLookTarget(Player p, double range) {
    Location eye = p.getEyeLocation();
    RayTraceResult hit = p.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true, 0.3, en -> isValidTarget(p, en));
    if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) {
      return null;
    }

    return target;
  }

  private boolean isValidTarget(Player p, Entity entity) {
    if (!(entity instanceof LivingEntity target) || target == p || target instanceof ArmorStand) {
      return false;
    }

    if (!target.isValid() || target.isDead() || target.isInvisible()) {
      return false;
    }

    return !(target instanceof Player other) || p.canSee(other);
  }

  private void spawnHud(Player p, LivingEntity target) {
    InsightHud hud = new InsightHud(target.getEntityId());
    huds.put(p.getUniqueId(), hud);
    Runnable spawnTask = () -> {
      if (!target.isValid() || huds.get(p.getUniqueId()) != hud) {
        huds.remove(p.getUniqueId(), hud);
        return;
      }

      Location loc = hudLocation(target);
      float scale = displayScale(p, loc);
      TextDisplay display = target.getWorld().spawn(loc, TextDisplay.class, d -> {
        applyDisplayDefaults(d);
        d.setTeleportDuration(3);
        d.setLineWidth(220);
        d.setText(buildHudText(target));
        d.setTransformation(scaleTransformation(scale, 0f));
      });
      hud.display = display;
      if (huds.get(p.getUniqueId()) != hud) {
        removeDisplayEntity(display);
        return;
      }

      showToOwner(p, display);
    };

    if (J.isFoliaThreading()) {
      J.runEntity(target, spawnTask);
      return;
    }

    spawnTask.run();
  }

  private void refreshHud(Player p, InsightHud hud, LivingEntity target) {
    TextDisplay display = hud.display;
    if (display == null) {
      return;
    }

    if (!display.isValid()) {
      clearHud(p.getUniqueId());
      return;
    }

    Location loc = hudLocation(target);
    float scale = displayScale(p, loc);
    Runnable task = () -> {
      if (!display.isValid() || !target.isValid()) {
        return;
      }

      display.setText(buildHudText(target));
      display.setTransformation(scaleTransformation(scale, 0f));
      J.teleport(display, loc);
    };

    if (J.isFoliaThreading()) {
      J.runEntity(display, task);
      return;
    }

    task.run();
  }

  private void spawnDamageNumber(Player attacker, LivingEntity victim, double damage, boolean crit) {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    Location loc = victim.getLocation().add(random.nextDouble(-0.35, 0.35), (victim.getHeight() * 0.8) + 0.3, random.nextDouble(-0.35, 0.35));
    float scale = displayScale(attacker, loc) * (crit ? 1.25f : 1f);
    String text = (crit ? C.GOLD : C.WHITE) + formatDamage(damage);
    TextDisplay display = victim.getWorld().spawn(loc, TextDisplay.class, d -> {
      applyDisplayDefaults(d);
      d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
      d.setText(text);
      d.setTransformation(scaleTransformation(scale, 0f));
    });
    showToOwner(attacker, display);

    int life = Math.max(6, getConfig().damageNumberLifeTicks);
    float rise = (float) getConfig().damageNumberRise;
    J.runEntity(display, () -> {
      if (!display.isValid()) {
        return;
      }

      display.setInterpolationDelay(0);
      display.setInterpolationDuration(life);
      display.setTransformation(scaleTransformation(scale, rise));
    }, 1);
    J.runEntity(display, () -> {
      if (display.isValid()) {
        display.remove();
      }
    }, life + 2);
  }

  private void applyDisplayDefaults(TextDisplay d) {
    d.setPersistent(false);
    d.setInvulnerable(true);
    d.setGravity(false);
    d.setSilent(true);
    d.setVisibleByDefault(false);
    d.setBillboard(Display.Billboard.CENTER);
    d.setShadowed(true);
    d.setSeeThrough(false);
    d.setShadowRadius(0f);
    d.setShadowStrength(0f);
  }

  private String buildHudText(LivingEntity target) {
    double max = maxHealth(target);
    double hp = Math.max(0, Math.min(target.getHealth(), max));
    double fraction = max <= 0 ? 0 : hp / max;
    int segments = Math.max(4, getConfig().healthBarSegments);
    int filled = (int) Math.ceil(fraction * segments);
    if (hp > 0) {
      filled = Math.max(1, filled);
    }
    filled = Math.min(segments, filled);

    C barColor = fraction > 0.5 ? C.GREEN : (fraction > 0.25 ? C.YELLOW : C.RED);
    String name = target.getCustomName() == null ? target.getName() : target.getCustomName();
    return C.WHITE + name + "\n"
        + barColor + BAR_SEGMENT.repeat(filled)
        + C.DARK_GRAY + BAR_SEGMENT.repeat(segments - filled)
        + C.GRAY + " " + Form.f(hp, 1) + C.DARK_GRAY + "/" + C.GRAY + Form.f(max, 0);
  }

  private double maxHealth(LivingEntity target) {
    AttributeInstance attribute = target.getAttribute(Attribute.MAX_HEALTH);
    return attribute == null ? Math.max(1, target.getHealth()) : attribute.getValue();
  }

  private String formatDamage(double damage) {
    return damage >= 10 ? String.valueOf(Math.round(damage)) : Form.f(damage, 1);
  }

  private Location hudLocation(LivingEntity target) {
    return target.getLocation().add(0, target.getHeight() + 0.5, 0);
  }

  private float displayScale(Player viewer, Location loc) {
    double distance = viewer.getWorld().equals(loc.getWorld()) ? viewer.getEyeLocation().distance(loc) : getConfig().hudMaxScale / getConfig().hudScalePerBlock;
    return (float) Math.min(getConfig().hudMaxScale, Math.max(getConfig().hudMinScale, distance * getConfig().hudScalePerBlock));
  }

  private Transformation scaleTransformation(float scale, float riseY) {
    return new Transformation(new Vector3f(0f, riseY, 0f), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf());
  }

  private double getRange(int level) {
    return getConfig().rangeBase + (getLevelPercent(level) * getConfig().rangeFactor);
  }

  private void clearHud(UUID id) {
    InsightHud hud = huds.remove(id);
    if (hud == null) {
      return;
    }

    TextDisplay display = hud.display;
    if (display != null) {
      removeDisplayEntity(display);
    }
  }

  private void removeDisplayEntity(Entity entity) {
    if (J.isFoliaThreading()) {
      J.runEntity(entity, () -> {
        if (entity.isValid()) {
          entity.remove();
        }
      });
      return;
    }

    if (entity.isValid()) {
      entity.remove();
    }
  }

  private void showToOwner(Player owner, Entity entity) {
    if (J.isFoliaThreading()) {
      J.runEntity(owner, () -> {
        if (entity.isValid() && owner.isOnline()) {
          owner.showEntity(Adapt.instance, entity);
        }
      });
      return;
    }

    if (owner.isOnline()) {
      owner.showEntity(Adapt.instance, entity);
    }
  }

  private static final class InsightHud {
    private final int entityId;
    private volatile TextDisplay display;

    private InsightHud(int entityId) {
      this.entityId = entityId;
    }
  }

  @ConfigDescription("Study creatures at a glance with a floating name, health bar, and personal damage numbers.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Range Base for the Discovery Insight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangeBase = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Range Factor for the Discovery Insight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangeFactor = 18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Display scale gained per block of distance so the HUD keeps a constant on-screen size.", impact = "Higher values make the HUD larger at range.")
    double hudScalePerBlock = 0.22;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum HUD display scale.", impact = "Higher values keep the HUD larger up close.")
    double hudMinScale = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum HUD display scale.", impact = "Higher values let the HUD grow larger at long range.")
    double hudMaxScale = 4.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Health Bar Segments for the Discovery Insight adaptation.", impact = "Higher values render a finer-grained health bar.")
    int healthBarSegments = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Shows floating damage numbers when you hit creatures.", impact = "True enables this behavior and false disables it.")
    boolean showDamageNumbers = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Vertical drift of damage numbers over their lifetime.", impact = "Higher values make numbers float up further.")
    double damageNumberRise = 0.7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Lifetime of damage numbers in ticks.", impact = "Higher values keep numbers on screen longer.")
    int damageNumberLifeTicks = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Inspection for the Discovery Insight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerInspection = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown between inspection XP grants in milliseconds.", impact = "Higher values slow inspection XP gain.")
    long xpCooldownMs = 10000;

    public Config() {
      baseCost = 2;
      costFactor = 0.2;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
