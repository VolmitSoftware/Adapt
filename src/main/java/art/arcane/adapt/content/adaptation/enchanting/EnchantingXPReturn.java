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
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.enchantment.EnchantItemEvent;

import java.util.UUID;

public class EnchantingXPReturn extends SimpleAdaptation<EnchantingXPReturn.Config> {
  private final Cooldowns cooldown = cooldowns();

  public EnchantingXPReturn() {
    super("enchanting-xp-return");
    registerConfiguration(Config.class);
    setLocalizationKey("enchanting.return");
    setIcon(Material.EXPERIENCE_BOTTLE);
    setInterval(13001);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.EXPERIENCE_BOTTLE)
        .key("challenge_enchanting_xp_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_enchanting_xp_100", "enchanting.xp-return.levels-saved", 100, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GRAY + Localizer.dLocalize("enchanting.return.lore1"));
    v.addLore(C.GREEN + "" + getConfig().xpReturn * (level * level) + Localizer.dLocalize("enchanting.return.lore2"));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EnchantItemEvent e) {
    Player p = e.getEnchanter();
    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    UUID id = p.getUniqueId();
    if (!cooldown.isReady(id, 20000L)) {
      return;
    }

    cooldown.mark(id);
    int xpAmount = getConfig().xpReturn * (level * level);
    p.getWorld().spawn(p.getLocation(), org.bukkit.entity.ExperienceOrb.class).setExperience(xpAmount);
    addStat(p, "enchanting.xp-return.levels-saved", xpAmount);
    xpRefundFx(p, level);
  }

  private void xpRefundFx(Player p, int level) {
    int rods = Math.min(level, 5);
    timeline(p)
        .duration(5)
        .priority(FxPriority.TRANSITION)
        .cullRadius(16.0D)
        .frame((f, tick, progress) -> {
          f.helix(Particles.ENCHANTMENT_TABLE, (0.7D * (1.0D - progress)) + 0.1D, 1.4D, 2, progress * Math.PI * 2.0D);
          if (tick == 0) {
            f.particle(Particles.VILLAGER_HAPPY, 4, 0, 1.0D, 0, 0.3D, 0)
                .column(Particles.END_ROD, rods, 1.2D)
                .chord(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 1.6F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.4F);
          }
        })
        .start();
  }


  @ConfigDescription("Enchanting XP is partially refunded when you enchant an item.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Return for the Enchanting XPReturn adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public int xpReturn = 2;

    public Config() {
      baseCost = 1;
      costFactor = 0.9;
      maxLevel = 7;
    }
  }
}
