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

package com.volmit.adapt.content.adaptation.seaborrne;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class SeaborneSpeed extends SimpleAdaptation<SeaborneSpeed.Config> {

    public SeaborneSpeed() {
        super("seaborne-speed");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("seaborn.dolphin_grace.description"));
        setDisplayName(Localizer.dLocalize("seaborn.dolphin_grace.name"));
        setIcon(Material.PRISMARINE_CRYSTALS);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(1020);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.HEART_OF_THE_SEA)
                .key("challenge_seaborne_speed_10k")
                .title(Localizer.dLocalize("advancement.challenge_seaborne_speed_10k.title"))
                .description(Localizer.dLocalize("advancement.challenge_seaborne_speed_10k.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.TRIDENT)
                        .key("challenge_seaborne_speed_100k")
                        .title(Localizer.dLocalize("advancement.challenge_seaborne_speed_100k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_seaborne_speed_100k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_seaborne_speed_10k", "seaborne.speed.blocks-swum", 10000, 300);
        registerMilestone("challenge_seaborne_speed_100k", "seaborne.speed.blocks-swum", 100000, 1500);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + Localizer.dLocalize("seaborn.dolphin_grace.lore1") + C.GREEN + (level) + C.GRAY + Localizer.dLocalize("seaborn.dolphin_grace.lore2"));
        v.addLore(C.ITALIC + Localizer.dLocalize("seaborn.dolphin_grace.lore3"));
    }

    @Override
    public void onTick() {
        for (com.volmit.adapt.api.world.AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
            Player player = adaptPlayer.getPlayer();
            if (player.isInWater() && hasAdaptation(player)) {
                if (player.getLocation().getBlock().isLiquid()) {
                    if (player.getInventory().getBoots() != null && player.getInventory().getBoots().containsEnchantment(Enchantment.DEPTH_STRIDER)) {
                        continue;
                    } else {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 62, getLevel(player)));
                        getPlayer(player).getData().addStat("seaborne.speed.blocks-swum", 1);
                    }
                }
            }
        }
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
    @ConfigDescription("Swim faster with dolphin-like grace.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.525;
    }
}
