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

package com.volmit.adapt.content.skill;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.content.adaptation.enchanting.EnchantingLapisReturn;
import com.volmit.adapt.content.adaptation.enchanting.EnchantingQuickEnchant;
import com.volmit.adapt.content.adaptation.enchanting.EnchantingXPReturn;
import com.volmit.adapt.util.C;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.enchantment.EnchantItemEvent;

public class SkillEnchanting extends SimpleSkill<SkillEnchanting.Config> {
    public SkillEnchanting() {
        super("enchanting", Adapt.dLocalize("Skill", "Enchanting", "Icon"));
        registerConfiguration(Config.class);
        setColor(C.LIGHT_PURPLE);
        setDescription(Adapt.dLocalize("Skill", "Enchanting", "Description"));
        setDisplayName(Adapt.dLocalize("Skill", "Enchanting", "Name"));
        setInterval(3909);
        setIcon(Material.KNOWLEDGE_BOOK);
        registerAdaptation(new EnchantingQuickEnchant());
        registerAdaptation(new EnchantingLapisReturn());
        registerAdaptation(new EnchantingXPReturn()); //
    }

    @EventHandler (priority = EventPriority.HIGHEST)
    public void on(EnchantItemEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (!AdaptConfig.get().isXpInCreative() && e.getEnchanter().getGameMode().name().contains("CREATIVE")) {
            return;
        }
        xp(e.getEnchanter(), getConfig().enchantPowerXPMultiplier * e.getEnchantsToAdd().values().stream().mapToInt((i) -> i).sum());
        getPlayer(e.getEnchanter()).getData().addStat("enchanted.items", 1);
        getPlayer(e.getEnchanter()).getData().addStat("enchanted.power", e.getEnchantsToAdd().values().stream().mapToInt(i -> i).sum());
        getPlayer(e.getEnchanter()).getData().addStat("enchanted.levels.spent", e.getExpLevelCost());
    }

    @Override
    public void onTick() {

    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        double enchantPowerXPMultiplier = 250;
    }
}
