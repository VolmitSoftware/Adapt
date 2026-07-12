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

package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

public class AgilitySlipstreamSlide extends SimpleAdaptation<AgilitySlipstreamSlide.Config> {
  private final Cooldowns slideCooldown = cooldowns();
  private final Cooldowns lastSprint = cooldowns();
  private final Map<UUID, Long> proneUntilMillis = playerState();

  public AgilitySlipstreamSlide() {
    super("agility-slipstream-slide");
    registerConfiguration(Config.class);
    setIcon(Material.ICE);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.PACKED_ICE)
        .key("challenge_agility_slipstream_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.BLUE_ICE)
            .key("challenge_agility_slipstream_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_agility_slipstream_500", "agility.slipstream-slide.slides", 500, 400);
    registerMilestone("challenge_agility_slipstream_5k", "agility.slipstream-slide.slides", 5000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getSlideForce(level) * 20D, 1), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 2);
    v.addLore(C.LIGHT_PURPLE + " " + Localizer.dLocalize("agility.slipstream_slide.lore3"));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    Player p = e.getPlayer();
    if (p.isSprinting()) {
      lastSprint.mark(p.getUniqueId());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    if (!e.isSneaking()) {
      return;
    }

    Player p = e.getPlayer();
    withAdaptedPlayer(p, e, () -> attemptSlide(p));
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    UUID id = e.getPlayer().getUniqueId();
    proneUntilMillis.remove(id);
    lastSprint.clear(id);
    slideCooldown.clear(id);
  }

  @EventHandler
  public void on(PlayerDeathEvent e) {
    proneUntilMillis.remove(e.getEntity().getUniqueId());
  }

  private void attemptSlide(Player p) {
    if (!isSlideEligible(p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    UUID id = p.getUniqueId();
    if (!slideCooldown.isReady(id, getCooldownMillis(level))) {
      return;
    }

    if (p.getFoodLevel() <= 0) {
      return;
    }

    Vector direction = p.getLocation().getDirection().clone().setY(0);
    if (direction.lengthSquared() <= 1.0E-4D) {
      return;
    }

    direction.normalize();
    slideCooldown.mark(id);
    applyHungerCost(p, getConfig().hungerCost);

    double force = getSlideForce(level);
    Vector velocity = direction.multiply(force);
    velocity.setY(Math.min(p.getVelocity().getY(), -0.04D));
    p.setVelocity(velocity);
    startProne(p, getSlideTicks(level));

    fx(p.getLocation(), FxPriority.GAMEPLAY)
        .trail(Particle.CLOUD, -direction.getX(), 0.05D, -direction.getZ(), 1.0D, 6)
        .particle(Particles.BLOCK_CRACK, 6, 0, 0.1D, 0, 0.3D, 0.05D, p.getLocation().getBlock().getRelative(BlockFace.DOWN).getBlockData())
        .chord(Sound.BLOCK_LADDER_STEP, 0.6F, 0.6F, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.4F, 1.5F);

    addStat(p, "agility.slipstream-slide.slides", 1);
    xp(p, getConfig().xpPerSlide);

    if (level >= getMaxLevel()) {
      runSlideSlow(p, getSlideTicks(level), getSlowDurationTicks(), Math.max(0, getConfig().slowAmplifier));
    }
  }

  private void startProne(Player p, int proneTicks) {
    UUID id = p.getUniqueId();
    long until = System.currentTimeMillis() + (proneTicks * 50L);
    proneUntilMillis.put(id, until);
    p.setSwimming(true);

    J.runEntity(p, () -> {
      if (!p.isOnline() || p.isDead()) {
        proneUntilMillis.remove(id);
        return;
      }

      long expected = proneUntilMillis.getOrDefault(id, 0L);
      if (expected > System.currentTimeMillis()) {
        return;
      }

      proneUntilMillis.remove(id);
      if (!p.isInWater()) {
        p.setSwimming(false);
      }
    }, proneTicks);
  }

  private void runSlideSlow(Player p, int ticksLeft, int slowDurationTicks, int slowAmplifier) {
    if (ticksLeft <= 0 || !p.isOnline() || p.isDead()) {
      return;
    }

    for (Entity nearby : p.getNearbyEntities(1.3D, 1.0D, 1.3D)) {
      if (nearby instanceof LivingEntity target && !(nearby instanceof Player) && canDamageTarget(p, nearby)) {
        J.runEntity(target, () -> target.addPotionEffect(
            new PotionEffect(PotionEffectType.SLOWNESS, slowDurationTicks, slowAmplifier, false, true)));
      }
    }

    J.runEntity(p, () -> runSlideSlow(p, ticksLeft - 1, slowDurationTicks, slowAmplifier), 1);
  }

  private void applyHungerCost(Player p, double cost) {
    double saturation = p.getSaturation();
    if (saturation >= cost) {
      p.setSaturation((float) (saturation - cost));
      return;
    }

    p.setSaturation(0F);
    int foodCost = (int) Math.ceil(cost - saturation);
    p.setFoodLevel(Math.max(0, p.getFoodLevel() - foodCost));
  }

  private boolean isSlideEligible(Player p) {
    GameMode mode = p.getGameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      return false;
    }

    boolean sprinting = p.isSprinting() || lastSprint.remaining(p.getUniqueId(), 350L) > 0;
    return sprinting
        && p.isOnGround()
        && !p.isSwimming()
        && !p.isFlying()
        && !p.isGliding()
        && p.getVehicle() == null;
  }

  private double getSlideForce(int level) {
    return getConfig().slideForceBase + (getLevelPercent(level) * getConfig().slideForceFactor);
  }

  private long getCooldownMillis(int level) {
    return Math.max(getConfig().cooldownMillisFloor,
        Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisReduction)));
  }

  private int getSlideTicks(int level) {
    return Math.max(3, (int) Math.round(getConfig().slideTicksBase + (getLevelPercent(level) * getConfig().slideTicksFactor)));
  }

  private int getSlowDurationTicks() {
    return Math.max(5, getConfig().slowDurationTicks);
  }

  @ConfigDescription("Tap sneak while sprinting to slide in a low pose, keeping momentum and fitting under 1-block gaps.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base horizontal slide velocity in blocks per tick before level scaling.", impact = "Higher values launch a longer slide; lower values keep it short.")
    double slideForceBase = 0.5;
    @ConfigDoc(value = "Additional slide velocity in blocks per tick granted at max level.", impact = "Higher values widen the slide-distance gain from leveling.")
    double slideForceFactor = 0.45;
    @ConfigDoc(value = "Cooldown between slides in milliseconds at level 0 before scaling.", impact = "Higher values slow slide frequency across all levels.")
    double cooldownMillisBase = 4000;
    @ConfigDoc(value = "Cooldown reduction in milliseconds applied at max level.", impact = "Higher values make leveling shorten the slide cooldown more.")
    double cooldownMillisReduction = 2500;
    @ConfigDoc(value = "Minimum slide cooldown in milliseconds after all reductions.", impact = "Higher values keep a hard floor under slide spam.")
    long cooldownMillisFloor = 1300;
    @ConfigDoc(value = "Base slide prone-pose duration in ticks before level scaling.", impact = "Higher values keep the crawl pose longer per slide.")
    double slideTicksBase = 7;
    @ConfigDoc(value = "Additional slide prone-pose duration in ticks granted at max level.", impact = "Higher values extend the low-pose window when leveled.")
    double slideTicksFactor = 5;
    @ConfigDoc(value = "Saturation/hunger cost paid per slide.", impact = "Higher values make sliding drain stamina faster.")
    double hungerCost = 1.8;
    @ConfigDoc(value = "Slowness amplifier applied to mobs slid through at max level.", impact = "Higher values slow struck mobs harder; only active at max level.")
    int slowAmplifier = 1;
    @ConfigDoc(value = "Duration in ticks of the max-level slow applied to mobs slid through.", impact = "Higher values keep slid mobs slowed longer.")
    int slowDurationTicks = 40;
    @ConfigDoc(value = "Experience granted per successful slide.", impact = "Higher values reward sliding with more skill xp.")
    double xpPerSlide = 3;

    public Config() {
      baseCost = 3;
      costFactor = 0.55;
      maxLevel = 4;
      initialCost = 4;
    }
  }
}
