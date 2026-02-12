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

package com.volmit.adapt.content.adaptation.enchanting;

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
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;

public class EnchantingXPReturn extends SimpleAdaptation<EnchantingXPReturn.Config> {
    private final Map<Player, Long> cooldown = new HashMap<>();

    public EnchantingXPReturn() {
        super("enchanting-xp-return");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("enchanting.return.description"));
        setDisplayName(Localizer.dLocalize("enchanting.return.name"));
        setIcon(Material.EXPERIENCE_BOTTLE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(13001);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.EXPERIENCE_BOTTLE)
                .key("challenge_enchanting_xp_100")
                .title(Localizer.dLocalize("advancement.challenge_enchanting_xp_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_enchanting_xp_100.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_enchanting_xp_100").goal(100).stat("enchanting.xp-return.levels-saved").reward(400).build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + Localizer.dLocalize("enchanting.return.lore1"));
        v.addLore(C.GREEN + "" + getConfig().xpReturn * (level * level) + Localizer.dLocalize("enchanting.return.lore2"));
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        cooldown.remove(p);
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EnchantItemEvent e) {
        if (e.isCancelled()) {
            return;
        }
        int level = getLevel(e.getEnchanter());
        Player p = e.getEnchanter();
        if (!hasAdaptation(p)) {
            return;
        }

        if (cooldown.containsKey(p) && cooldown.get(p) + 20000 < System.currentTimeMillis()) {
            cooldown.remove(p);
        } else if (cooldown.containsKey(p) && cooldown.get(p) + 20000 > System.currentTimeMillis()) {
            return;
        }
        cooldown.put(p, System.currentTimeMillis());
        int xpAmount = getConfig().xpReturn * (level * level);
        p.getWorld().spawn(p.getLocation(), org.bukkit.entity.ExperienceOrb.class).setExperience(xpAmount);
        getPlayer(p).getData().addStat("enchanting.xp-return.levels-saved", xpAmount);
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
    @ConfigDescription("Enchanting XP is partially refunded when you enchant an item.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Return for the Enchanting XPReturn adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public int xpReturn = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 1.97;
    }
}
