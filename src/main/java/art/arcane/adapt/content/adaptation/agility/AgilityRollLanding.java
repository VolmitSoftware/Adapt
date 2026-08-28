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

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
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
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.Map;
import java.util.UUID;

public class AgilityRollLanding extends SimpleAdaptation<AgilityRollLanding.Config> {
  private final Cooldowns rollInputs = cooldowns();
  private final Map<UUID, Long> proneUntilMillis = playerState();

  public AgilityRollLanding() {
    super("agility-roll-landing");
    registerConfiguration(Config.class);
    setIcon(Material.HAY_BLOCK);
    setInterval(1200);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.HAY_BLOCK)
        .key("challenge_agility_roll_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.SLIME_BLOCK)
            .key("challenge_agility_roll_1000")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ELYTRA)
        .key("challenge_agility_fearless")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.HIDDEN)
        .build());
    registerMilestone("challenge_agility_roll_100", "agility.roll-landing.damage-prevented", 100, 300);
    registerMilestone("challenge_agility_roll_1000", "agility.roll-landing.damage-prevented", 1000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getFallReduction(level), 0), 1);
    statLore(v, Form.duration(getInputWindowMillis(level), 1), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTicks(level) * 50D, 1), 3);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    if (!e.isSneaking()) {
      return;
    }

    Player p = e.getPlayer();
    withAdaptedPlayer(p, e, () -> recordRollInput(p, true, null, null));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    Player p = e.getPlayer();
    if (!p.isSneaking() || p.isOnGround()) {
      return;
    }

    withAdaptedPlayer(p, e, () -> recordRollInput(p, true, e.getFrom().getY(), e.getTo() == null ? null : e.getTo().getY()));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p) || e.getCause() != EntityDamageEvent.DamageCause.FALL) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      if (p.hasCooldown(Material.HAY_BLOCK)) {
        return;
      }

      int level = getActiveLevel(p);
      if (rollInputs.remaining(p.getUniqueId(), getInputWindowMillis(level)) <= 0) {
        return;
      }

      RollAbsorption absorption = computeRollAbsorption(e.getDamage(), getFallReduction(level), getHungerPerDamage(level), p.getFoodLevel());
      double absorbed = absorption.absorbed();
      if (absorbed <= 0) {
        return;
      }

      p.setFoodLevel(Math.max(0, p.getFoodLevel() - absorption.hungerCost()));
      e.setDamage(Math.max(0, e.getDamage() - absorbed));
      if (e.getDamage() <= 0.01) {
        e.setCancelled(true);
      }

      p.setCooldown(Material.HAY_BLOCK, getCooldownTicks(level));
      triggerRollPose(p, level);

      int dust = (int) Math.max(4, Math.min(12, 4 + Math.round(absorbed)));
      fx(p.getLocation(), FxPriority.COMBAT)
          .particle(Particles.BLOCK_CRACK, dust, 0, 0.1D, 0, 0.3D, 0.05D, p.getLocation().getBlock().getRelative(BlockFace.DOWN).getBlockData())
          .chord(Sound.ENTITY_PLAYER_SMALL_FALL, 0.8F, 0.7F, Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0F, 0.89F, Sound.BLOCK_WOOL_BREAK, 0.55F, 0.9F);
      addStat(p, "agility.roll-landing.damage-prevented", absorbed);
      if (p.getFallDistance() >= 30) {
        fx(p.getLocation(), FxPriority.COMBAT)
            .ring(Particle.CRIT, 1.0D, 10, 0.1D)
            .sound(Sound.BLOCK_NOTE_BLOCK_PLING, 0.4F, 1.5F);
        if (AdaptConfig.get().isAdvancements() && !getPlayer(p).getData().isGranted("challenge_agility_fearless")) {
          getPlayer(p).getAdvancementHandler().grant("challenge_agility_fearless");
        }
      }
      xp(p, absorbed * getConfig().xpPerDamagePrevented);
    });
  }

  private void recordRollInput(Player p, boolean sneaking, Double fromY, Double toY) {
    boolean descending = isDescending(
        p.getVelocity().getY(),
        getConfig().maxVerticalVelocityForRollInput,
        fromY,
        toY,
        p.getFallDistance()
    );
    if (!shouldRecordRollInput(sneaking, p.isOnGround(), p.isFlying(), p.isGliding(), descending)) {
      return;
    }

    UUID id = p.getUniqueId();
    if (rollInputs.remaining(id, 800L) <= 0) {
      fx(p.getLocation(), FxPriority.TRANSITION).sound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.15F, 1.8F);
    }
    rollInputs.mark(id);
  }

  private void triggerRollPose(Player p, int level) {
    int proneTicks = getProneTicks(level);
    long until = System.currentTimeMillis() + (proneTicks * 50L);
    UUID id = p.getUniqueId();
    proneUntilMillis.put(id, until);
    p.setSwimming(true);

    timeline(p)
        .duration(Math.max(2, proneTicks))
        .priority(FxPriority.TRAIL)
        .frame((fx, tick, progress) -> fx.particle(Particle.CLOUD, 1, 0, 0.15D, 0, 0.05D, 0))
        .start();

    J.runEntity(p, () -> {
      if (!p.isOnline() || p.isDead()) {
        proneUntilMillis.remove(id);
        return;
      }

      long expectedUntil = proneUntilMillis.getOrDefault(id, 0L);
      if (expectedUntil > System.currentTimeMillis()) {
        return;
      }

      proneUntilMillis.remove(id);
      if (!p.isInWater()) {
        p.setSwimming(false);
      }
    }, proneTicks);
  }

  private double getFallReduction(int level) {
    return Math.min(getConfig().maxReduction, getConfig().reductionBase + (getLevelPercent(level) * getConfig().reductionFactor));
  }

  private long getInputWindowMillis(int level) {
    return inputWindowMillis(getConfig().inputWindowMillisBase, getConfig().inputWindowMillisFactor, getLevelPercent(level));
  }

  static long inputWindowMillis(double base, double factor, double levelPercent) {
    return Math.max(60L, Math.round(base + (levelPercent * factor)));
  }

  static boolean shouldRecordRollInput(boolean sneaking, boolean onGround, boolean flying, boolean gliding, boolean descending) {
    return sneaking && !onGround && !flying && !gliding && descending;
  }

  static boolean isDescending(double velocityY, double descentThreshold, Double fromY, Double toY, double fallDistance) {
    if (velocityY <= descentThreshold) {
      return true;
    }

    if (fallDistance > 0) {
      return true;
    }

    return fromY != null && toY != null && toY < fromY;
  }

  private double getHungerPerDamage(int level) {
    return Math.max(0.1, getConfig().hungerPerDamageBase - (getLevelPercent(level) * getConfig().hungerPerDamageReduction));
  }

  private int getCooldownTicks(int level) {
    return Math.max(4, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksFactor)));
  }

  private int getProneTicks(int level) {
    return Math.max(2, (int) Math.round(getConfig().proneTicksBase + (getLevelPercent(level) * getConfig().proneTicksFactor)));
  }

  static RollAbsorption computeRollAbsorption(double damage, double reduction, double hungerPerDamage, int foodLevel) {
    double absorbCap = damage * reduction;
    int hungerNeeded = (int) Math.ceil(absorbCap * hungerPerDamage);
    if (hungerNeeded <= 0 || foodLevel <= 0) {
      return new RollAbsorption(0, 0D);
    }

    int usableFood = Math.min(foodLevel, hungerNeeded);
    double absorbed = Math.min(absorbCap, usableFood / hungerPerDamage);
    return new RollAbsorption(usableFood, absorbed);
  }

  record RollAbsorption(int hungerCost, double absorbed) {
  }


  @ConfigDescription("Timed sneak before landing converts part of fall damage into hunger cost.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reduction Base for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reductionBase = 0.22;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reduction Factor for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reductionFactor = 0.43;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Reduction for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxReduction = 0.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "How long a crouch input stays armed before landing, in milliseconds, before level scaling.", impact = "Higher values make the roll timing more forgiving at every level.")
    double inputWindowMillisBase = 450;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra crouch-input window granted at max level, in milliseconds.", impact = "Higher values make leveling widen the roll timing more.")
    double inputWindowMillisFactor = 350;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Hunger Per Damage Base for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hungerPerDamageBase = 1.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Hunger Per Damage Reduction for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hungerPerDamageReduction = 0.75;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 22;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Factor for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksFactor = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Vertical velocity at or below which a crouch counts as a descent input.", impact = "Only a fallback signal; fall distance and downward movement also arm the roll.")
    double maxVerticalVelocityForRollInput = -0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Prone Ticks Base for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double proneTicksBase = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Prone Ticks Factor for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double proneTicksFactor = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Damage Prevented for the Agility Roll Landing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerDamagePrevented = 4.2;

    public Config() {
      baseCost = 3;
      costFactor = 0.62;
      initialCost = 3;
    }
  }
}
