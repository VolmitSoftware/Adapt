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

package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PiglinBarterEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class NetherPiglinBroker extends SimpleAdaptation<NetherPiglinBroker.Config> {
  public NetherPiglinBroker() {
    super("nether-piglin-broker");
    registerConfiguration(Config.class);
    setIcon(Material.GOLD_INGOT);
    setInterval(2300);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GOLD_INGOT)
        .key("challenge_nether_piglin_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.GOLD_BLOCK)
            .key("challenge_nether_piglin_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_nether_piglin_100", "nether.piglin-broker.improved-barters", 100, 300);
    registerMilestone("challenge_nether_piglin_2500", "nether.piglin-broker.improved-barters", 2500, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getExtraRollChance(level), 0), 1);
    statLore(v, Form.pc(getRareBonusChance(level), 0), 2);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PiglinBarterEvent e) {
    Piglin piglin = e.getEntity();
    Player broker = findBroker(piglin, getConfig().brokerRange);
    if (broker == null) {
      return;
    }

    int level = getActiveLevel(broker);
    if (level <= 0) {
      return;
    }

    List<ItemStack> outcome = e.getOutcome();
    if (outcome.isEmpty()) {
      return;
    }

    ThreadLocalRandom random = ThreadLocalRandom.current();
    boolean extraRoll = false;
    boolean rareBonus = false;
    if (random.nextDouble() <= getExtraRollChance(level)) {
      ItemStack bonus = outcome.get(random.nextInt(outcome.size())).clone();
      int amount = Math.max(1, (int) Math.round(bonus.getAmount() * getAmountMultiplier(level)));
      bonus.setAmount(Math.min(bonus.getMaxStackSize(), amount));
      outcome.add(bonus);
      extraRoll = true;
    }

    if (random.nextDouble() <= getRareBonusChance(level)) {
      outcome.add(getRareBonusRoll());
      rareBonus = true;
    }

    if (!extraRoll && !rareBonus) {
      return;
    }

    org.bukkit.Location at = broker.getLocation().add(0D, 1.0D, 0D);
    FxEmitter emit = fx(at, FxPriority.TRANSITION)
        .particle(Particle.HAPPY_VILLAGER, 10, 0D, 0.5D, 0D, 0.4D, 0.1D)
        .particle(Particle.WAX_ON, 6, 0D, 0.5D, 0D, 0.3D, 0.05D)
        .burst(Particle.CRIT, 4, 0.3D)
        .chord(Sound.ENTITY_PIGLIN_ADMIRING_ITEM, 0.9F, 1.25F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.5F);
    emit.sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3F, 1.2F);
    if (rareBonus) {
      emit.column(Particles.END_ROD, 3, 1.2D)
          .particle(Particle.FLASH, 1, 0D, 0.5D, 0D, 0D, 0.0D)
          .sound(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5F, 1.3F);
    }
    xp(broker, getConfig().xpOnBoostedBarter);
    addStat(broker, "nether.piglin-broker.improved-barters", 1);
  }

  private Player findBroker(Piglin piglin, double range) {
    Player best = null;
    double bestDist = Double.MAX_VALUE;
    double rangeSquared = range * range;
    org.bukkit.Location piglinLocation = piglin.getLocation();
    for (Entity nearby : piglin.getNearbyEntities(range, range, range)) {
      if (!(nearby instanceof Player p)) {
        continue;
      }
      if (!hasActiveAdaptation(p)) {
        continue;
      }

      double d = p.getLocation().distanceSquared(piglinLocation);
      if (d > rangeSquared) {
        continue;
      }
      if (d < bestDist) {
        best = p;
        bestDist = d;
      }
    }

    return best;
  }

  private ItemStack getRareBonusRoll() {
    return switch (ThreadLocalRandom.current().nextInt(5)) {
      case 0 -> new ItemStack(Material.ENDER_PEARL, 1);
      case 1 -> new ItemStack(Material.OBSIDIAN, 2);
      case 2 -> new ItemStack(Material.STRING, 4);
      case 3 -> new ItemStack(Material.IRON_NUGGET, 6);
      default -> new ItemStack(Material.SPECTRAL_ARROW, 2);
    };
  }

  private double getExtraRollChance(int level) {
    return Math.min(getConfig().maxExtraRollChance, getConfig().extraRollChanceBase + (getLevelPercent(level) * getConfig().extraRollChanceFactor));
  }

  private double getRareBonusChance(int level) {
    return Math.min(getConfig().maxRareBonusChance, getConfig().rareBonusChanceBase + (getLevelPercent(level) * getConfig().rareBonusChanceFactor));
  }

  private double getAmountMultiplier(int level) {
    return Math.max(1.0, getConfig().amountMultiplierBase + (getLevelPercent(level) * getConfig().amountMultiplierFactor));
  }


  @ConfigDescription("Nearby piglin bartering can yield extra or improved rolls.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Broker Range for the Nether Piglin Broker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double brokerRange = 18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Extra Roll Chance Base for the Nether Piglin Broker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double extraRollChanceBase = 0.1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Extra Roll Chance Factor for the Nether Piglin Broker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double extraRollChanceFactor = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Extra Roll Chance for the Nether Piglin Broker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxExtraRollChance = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Rare Bonus Chance Base for the Nether Piglin Broker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rareBonusChanceBase = 0.03;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Rare Bonus Chance Factor for the Nether Piglin Broker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rareBonusChanceFactor = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Rare Bonus Chance for the Nether Piglin Broker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxRareBonusChance = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Amount Multiplier Base for the Nether Piglin Broker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double amountMultiplierBase = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Amount Multiplier Factor for the Nether Piglin Broker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double amountMultiplierFactor = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp On Boosted Barter for the Nether Piglin Broker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnBoostedBarter = 12;

    public Config() {
      costFactor = 0.7;
      initialCost = 4;
    }
  }
}
