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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;

import java.util.UUID;

public class EnchantingRuneSight extends SimpleAdaptation<EnchantingRuneSight.Config> {
  private final Cooldowns revealThrottle = cooldowns();

  public EnchantingRuneSight() {
    super("enchanting-rune-sight");
    registerConfiguration(Config.class);
    setIcon(Material.SPYGLASS);
    setInterval(1600);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SPYGLASS)
        .key("challenge_enchanting_rune_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENCHANTING_TABLE)
            .key("challenge_enchanting_rune_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_enchanting_rune_100", "enchanting.rune-sight.offers-revealed", 100, 300);
    registerMilestone("challenge_enchanting_rune_1k", "enchanting.rune-sight.offers-revealed", 1000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getRevealDepth(level), 1);
  }

  static int revealDepth(int maxReveal, double levelPercent) {
    return Math.max(1, Math.min(maxReveal, 1 + (int) Math.floor(levelPercent * (maxReveal - 1))));
  }

  private int getRevealDepth(int level) {
    return revealDepth(getConfig().maxRevealDepth, getLevelPercent(level));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PrepareItemEnchantEvent e) {
    Player p = e.getEnchanter();
    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    EnchantmentOffer[] offers = e.getOffers();
    if (offers == null || offers.length == 0) {
      return;
    }

    UUID id = p.getUniqueId();
    if (!revealThrottle.isReady(id, getConfig().revealThrottleMs)) {
      return;
    }

    int depth = getRevealDepth(level);
    StringBuilder message = new StringBuilder();
    int revealed = 0;
    for (int i = 0; i < offers.length && revealed < depth; i++) {
      EnchantmentOffer offer = offers[i];
      if (offer == null || offer.getEnchantment() == null) {
        continue;
      }

      if (message.length() > 0) {
        message.append(C.GRAY).append("  |  ");
      }
      message.append(C.LIGHT_PURPLE)
          .append(offer.getEnchantment().getKey().getKey().replace('_', ' '))
          .append(' ')
          .append(offer.getEnchantmentLevel())
          .append(C.DARK_GRAY)
          .append(" (")
          .append(offer.getCost())
          .append(')');
      revealed++;
    }

    if (revealed <= 0) {
      return;
    }

    revealThrottle.mark(id);
    Adapt.actionbar(p, message.toString());
    addStat(p, "enchanting.rune-sight.offers-revealed", revealed);
  }


  @ConfigDescription("Reveal the enchantments behind enchanting-table offers before you commit.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Reveal Depth for the Enchanting Rune Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxRevealDepth = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reveal Throttle Ms for the Enchanting Rune Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long revealThrottleMs = 400;

    public Config() {
      baseCost = 3;
      costFactor = 0.5;
      maxLevel = 3;
      initialCost = 3;
    }
  }
}
