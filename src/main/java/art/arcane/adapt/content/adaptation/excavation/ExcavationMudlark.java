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

package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.PotionEffectTypes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.concurrent.ThreadLocalRandom;

public class ExcavationMudlark extends SimpleAdaptation<ExcavationMudlark.Config> {
  public ExcavationMudlark() {
    super("excavation-mudlark");
    registerConfiguration(Config.class);
    setDescription(Localizer.dLocalize("excavation.mudlark.description"));
    setDisplayName(Localizer.dLocalize("excavation.mudlark.name"));
    setIcon(Material.MUD);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    setInterval(4530);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.MUD)
        .key("challenge_excavation_mudlark_1k")
        .title(Localizer.dLocalize("advancement.challenge_excavation_mudlark_1k.title"))
        .description(Localizer.dLocalize("advancement.challenge_excavation_mudlark_1k.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_excavation_mudlark_1k", "excavation.mudlark.bonus-drops", 1000, 500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.pc(getBonusChance(level), 1) + C.GRAY + " " + Localizer.dLocalize("excavation.mudlark.lore1"));
    v.addLore(C.GREEN + "+ " + (getHasteAmplifier(level) + 1) + C.GRAY + " " + Localizer.dLocalize("excavation.mudlark.lore2"));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockDamageEvent e) {
    Player p = e.getPlayer();
    if (!isShovel(p.getInventory().getItemInMainHand())) {
      return;
    }

    if (!isWet(p)) {
      return;
    }

    art.arcane.adapt.api.adaptation.Adaptation.BlockActionContext context = resolveInteractContext(p, e.getBlock().getLocation());
    if (context == null) {
      return;
    }

    p.addPotionEffect(new PotionEffect(PotionEffectTypes.FAST_DIGGING, getConfig().hasteDurationTicks, getHasteAmplifier(context.level()), false, false, true));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    Material type = e.getBlock().getType();
    if (!isMudlarkBlock(type)) {
      return;
    }

    Player p = e.getPlayer();
    if (!isShovel(p.getInventory().getItemInMainHand())) {
      return;
    }

    art.arcane.adapt.api.adaptation.Adaptation.BlockActionContext context = resolveBlockBreakContext(p, e.getBlock().getLocation());
    if (context == null) {
      return;
    }

    int level = context.level();
    if (ThreadLocalRandom.current().nextDouble() > getBonusChance(level)) {
      return;
    }

    Material bonus = getBonusDrop(type);
    Location drop = e.getBlock().getLocation().add(0.5, 0.5, 0.5);
    e.getBlock().getWorld().dropItemNaturally(drop, new ItemStack(bonus, 1));
    if (areParticlesEnabled()) {
      p.spawnParticle(Particle.SPLASH, drop, 8, 0.25, 0.2, 0.25, 0.01);
    }

    SoundPlayer.of(p.getWorld()).play(drop, Sound.BLOCK_ROOTED_DIRT_BREAK, 0.6f, 1.2f);
    getPlayer(p).getData().addStat("excavation.mudlark.bonus-drops", 1);
    xp(p, getConfig().xpPerBonusDrop);
  }

  private boolean isWet(Player p) {
    if (p.isInWater()) {
      return true;
    }

    if (!p.getWorld().hasStorm()) {
      return false;
    }

    return p.getLocation().getBlock().getLightFromSky() >= 14;
  }

  private boolean isMudlarkBlock(Material type) {
    return switch (type) {
      case CLAY, MUD, MUDDY_MANGROVE_ROOTS, SOUL_SAND, SOUL_SOIL -> true;
      default -> false;
    };
  }

  private Material getBonusDrop(Material type) {
    return switch (type) {
      case CLAY -> Material.CLAY_BALL;
      case MUD, MUDDY_MANGROVE_ROOTS -> Material.MUD;
      case SOUL_SAND -> Material.SOUL_SAND;
      default -> Material.SOUL_SOIL;
    };
  }

  private double getBonusChance(int level) {
    return Math.min(getConfig().maxBonusChance, getConfig().bonusChanceBase + (getLevelPercent(level) * getConfig().bonusChanceFactor));
  }

  private int getHasteAmplifier(int level) {
    return Math.max(0, (int) Math.round(getLevelPercent(level) * (getConfig().maxHasteLevel - 1)));
  }

  @Override
  public void onTick() {

  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }

  @NoArgsConstructor
  @ConfigDescription("Bonus drops from muddy blocks, plus haste while digging in water or rain.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Chance Base for the Excavation Mudlark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusChanceBase = 0.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Chance Factor for the Excavation Mudlark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusChanceFactor = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Bonus Chance for the Excavation Mudlark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxBonusChance = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Haste Level for the Excavation Mudlark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxHasteLevel = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Haste Duration Ticks for the Excavation Mudlark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int hasteDurationTicks = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Bonus Drop for the Excavation Mudlark adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBonusDrop = 3;
  }
}
