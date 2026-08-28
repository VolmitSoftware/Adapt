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

package art.arcane.adapt.content.adaptation.enchanting;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;

public class EnchantingBookshelfAttunement extends SimpleAdaptation<EnchantingBookshelfAttunement.Config> {
  private final Cooldowns shimmerCooldown = cooldowns();

  public EnchantingBookshelfAttunement() {
    super("enchanting-bookshelf-attunement");
    registerConfiguration(Config.class);
    setIcon(Material.BOOKSHELF);
    setInterval(1400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BOOKSHELF)
        .key("challenge_enchanting_bookshelf_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_enchanting_bookshelf_100", "enchanting.bookshelf-attunement.enchants-boosted", 100, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getVirtualPower(level), 1);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EnchantItemEvent e) {
    Player p = e.getEnchanter();
    if (!hasActiveAdaptation(p)) {
      return;
    }

    Block enchantBlock = e.getEnchantBlock();
    if (enchantBlock == null) {
      return;
    }

    int count = Math.min(24, 6 + (getVirtualPower(getLevel(p)) * 2));
    fx(enchantBlock.getLocation().add(0.5D, 1.0D, 0.5D), FxPriority.TRANSITION)
        .dome(Particles.ENCHANTMENT_TABLE, 1.4D, count)
        .sound(Sound.BLOCK_BEACON_ACTIVATE, 0.5F, 1.3F);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PrepareItemEnchantEvent e) {
    Player p = e.getEnchanter();
    if (!hasActiveAdaptation(p)) {
      return;
    }

    int power = getVirtualPower(getLevel(p));
    EnchantmentOffer[] offers = e.getOffers();
    if (offers == null) {
      return;
    }

    boolean boosted = false;
    for (EnchantmentOffer offer : offers) {
      if (offer == null) {
        continue;
      }

      int newCost = Math.min(30, offer.getCost() + power);
      int newLevel = Math.min(offer.getEnchantment().getMaxLevel(), offer.getEnchantmentLevel() + Math.max(0, power / 3));
      offer.setCost(newCost);
      offer.setEnchantmentLevel(Math.max(1, newLevel));
      boosted = true;
    }
    if (boosted) {
      addStat(p, "enchanting.bookshelf-attunement.enchants-boosted", 1);
      if (shimmerCooldown.isReady(p.getUniqueId(), 1000L)) {
        shimmerCooldown.mark(p.getUniqueId());
        Block enchantBlock = e.getEnchantBlock();
        if (enchantBlock != null) {
          fx(enchantBlock.getLocation().add(0.5D, 1.0D, 0.5D), FxPriority.AMBIENT)
              .particle(Particles.ENCHANTMENT_TABLE, 6, 0, 0, 0, 0.4D, 0.05D)
              .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.5F);
        }
      }
    }
  }

  private int getVirtualPower(int level) {
    return Math.max(1, (int) Math.round(getConfig().powerBase + (getLevelPercent(level) * getConfig().powerFactor)));
  }


  @ConfigDescription("Gain virtual bookshelf power to improve enchanting table offer quality.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Power Base for the Enchanting Bookshelf Attunement adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double powerBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Power Factor for the Enchanting Bookshelf Attunement adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double powerFactor = 5;

    public Config() {
      costFactor = 0.6;
      maxLevel = 4;
      initialCost = 3;
    }
  }
}
