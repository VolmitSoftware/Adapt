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

package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class HerbalismBeeShepherd extends SimpleAdaptation<HerbalismBeeShepherd.Config> {
  private final Cooldowns pulseCooldown = cooldowns();
  private final Cooldowns auraHint = cooldowns();

  public HerbalismBeeShepherd() {
    super("herbalism-bee-shepherd");
    registerConfiguration(Config.class);
    setIcon(Material.BEE_NEST);
    setInterval(10);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.HONEYCOMB)
        .key("challenge_herbalism_bee_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_herbalism_bee_100", "herbalism.bee-shepherd.bees-attracted", 100, 300);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRadius(level)), 1);
    statLore(v, getGrowthAttempts(level), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getPulseMillis(level), 1), 3);
  }

  @Override
  public void onTick() {
    for (art.arcane.adapt.api.world.AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
      Player p = adaptPlayer.getPlayer();
      if (!hasActiveAdaptation(p) || !isHoldingFlower(p)) {
        continue;
      }

      int level = getActiveLevel(p);
      if (!pulseCooldown.isReady(p.getUniqueId(), getPulseMillis(level))) {
        continue;
      }

      int foodCost = getFoodCost(level);
      if (p.getFoodLevel() < foodCost) {
        continue;
      }

      int grown = pulseGrowth(p, level);
      int attracted = pullNearbyBees(p, level);
      pulseCooldown.mark(p.getUniqueId());
      if (attracted > 0) {
        addStat(p, "herbalism.bee-shepherd.bees-attracted", attracted);
      }

      if (auraHint.isReady(p.getUniqueId(), 8000L)) {
        auraHint.mark(p.getUniqueId());
        fx(p.getLocation(), FxPriority.AMBIENT)
            .dustRing(Color.LIME, getRadius(level), 20, 1.0F)
            .sound(Sound.BLOCK_ROOTED_DIRT_PLACE, 0.4F, 1.1F);
      }

      if (grown <= 0) {
        continue;
      }

      p.setFoodLevel(Math.max(0, p.getFoodLevel() - foodCost));
      fx(p.getLocation().add(0, 1, 0), FxPriority.AMBIENT)
          .ring(Particle.HAPPY_VILLAGER, 0.9D, 8, 0.2D)
          .particle(Particle.COMPOSTER, 2, 0, 0.3D, 0, 0.2D, 0.02D)
          .chord(Sound.ENTITY_BEE_POLLINATE, 0.85F, 1.25F, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.25F, 1.6F);
      xp(p, grown * getConfig().xpPerGrowth);
    }
  }

  private int pulseGrowth(Player p, int level) {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    int radius = Math.max(1, (int) Math.round(getRadius(level)));
    int grown = 0;
    int emitted = 0;
    int attempts = getGrowthAttempts(level);
    boolean showParticles = getConfig().showGrowthParticles;
    for (int i = 0; i < attempts; i++) {
      int dx = random.nextInt(-radius, radius + 1);
      int dz = random.nextInt(-radius, radius + 1);
      int dy = random.nextInt(-1, 2);
      Block block = p.getLocation().getBlock().getRelative(dx, dy, dz);
      if (!(block.getBlockData() instanceof Ageable ageable) || ageable.getAge() >= ageable.getMaximumAge()) {
        continue;
      }

      int increase = Math.max(1, getGrowthStep(level));
      ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + increase));
      block.setBlockData(ageable, true);
      grown++;

      if (showParticles && emitted < 6 && ageable.getAge() >= ageable.getMaximumAge()) {
        fx(block.getLocation().add(0.5, 1.0, 0.5), FxPriority.AMBIENT)
            .particle(Particle.HAPPY_VILLAGER, 2, 0, 0, 0, 0.1D, 0.01D)
            .particle(Particle.COMPOSTER, 1, 0, 0.1D, 0, 0.1D, 0.01D);
        emitted++;
      }
    }

    return grown;
  }

  private int pullNearbyBees(Player p, int level) {
    double radius = getRadius(level);
    int count = 0;
    int trails = 0;
    for (Entity entity : p.getWorld().getNearbyEntities(p.getLocation(), radius, radius, radius)) {
      if (!(entity instanceof Bee bee)) {
        continue;
      }

      Vector toward = p.getLocation().add(0, 0.75, 0).toVector().subtract(bee.getLocation().toVector());
      if (toward.lengthSquared() <= 0.001) {
        continue;
      }

      if (trails < 4) {
        fx(bee.getLocation(), FxPriority.TRAIL)
            .trail(Particle.HAPPY_VILLAGER, toward.getX(), toward.getY(), toward.getZ(), Math.min(3.0D, toward.length()), 3);
        trails++;
      }

      toward.normalize().multiply(getBeePullStrength(level));
      bee.setVelocity(bee.getVelocity().multiply(0.6).add(toward));
      bee.setTarget(null);
      count++;
    }
    return count;
  }

  private boolean isHoldingFlower(Player p) {
    return isFlower(p.getInventory().getItemInMainHand()) || isFlower(p.getInventory().getItemInOffHand());
  }

  private boolean isFlower(ItemStack item) {
    if (!isItem(item)) {
      return false;
    }

    Material type = item.getType();
    return type.name().endsWith("_TULIP")
        || type == Material.DANDELION
        || type == Material.POPPY
        || type == Material.BLUE_ORCHID
        || type == Material.ALLIUM
        || type == Material.AZURE_BLUET
        || type == Material.OXEYE_DAISY
        || type == Material.CORNFLOWER
        || type == Material.LILY_OF_THE_VALLEY
        || type == Material.WITHER_ROSE
        || type == Material.SUNFLOWER
        || type == Material.LILAC
        || type == Material.ROSE_BUSH
        || type == Material.PEONY
        || type == Material.TORCHFLOWER
        || type == Material.PINK_PETALS;
  }

  private double getRadius(int level) {
    return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
  }

  private int getGrowthAttempts(int level) {
    return Math.max(1, (int) Math.round(getConfig().growthAttemptsBase + (getLevelPercent(level) * getConfig().growthAttemptsFactor)));
  }

  private int getGrowthStep(int level) {
    return Math.max(1, (int) Math.round(getConfig().growthStepBase + (getLevelPercent(level) * getConfig().growthStepFactor)));
  }

  private int getFoodCost(int level) {
    return Math.max(1, (int) Math.round(getConfig().foodCostBase - (getLevelPercent(level) * getConfig().foodCostFactor)));
  }

  private long getPulseMillis(int level) {
    return Math.max(250L, (long) Math.round(getConfig().pulseMillisBase - (getLevelPercent(level) * getConfig().pulseMillisFactor)));
  }

  private double getBeePullStrength(int level) {
    return Math.max(0.01, getConfig().beePullStrengthBase + (getLevelPercent(level) * getConfig().beePullStrengthFactor));
  }

  @ConfigDescription("Holding flowers near crops emits growth pulses and draws nearby bees toward you.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Show Growth Particles for the Herbalism Bee Shepherd adaptation.", impact = "True enables this behavior and false disables it.")
    boolean showGrowthParticles = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Growth Attempts Base for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double growthAttemptsBase = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Growth Attempts Factor for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double growthAttemptsFactor = 18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Growth Step Base for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double growthStepBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Growth Step Factor for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double growthStepFactor = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Food Cost Base for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double foodCostBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Food Cost Factor for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double foodCostFactor = 1.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Pulse Millis Base for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double pulseMillisBase = 900;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Pulse Millis Factor for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double pulseMillisFactor = 650;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bee Pull Strength Base for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double beePullStrengthBase = 0.07;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bee Pull Strength Factor for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double beePullStrengthFactor = 0.14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Growth for the Herbalism Bee Shepherd adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerGrowth = 0.9;

    public Config() {
      baseCost = 3;
      costFactor = 0.64;
      initialCost = 3;
    }
  }
}
