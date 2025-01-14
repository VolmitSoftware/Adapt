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

package com.volmit.adapt.content.item;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.item.DataItem;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Data
public class ExperienceOrb implements DataItem<ExperienceOrb.Data> {
    public static ExperienceOrb io = new ExperienceOrb();

    public static Data get(ItemStack is) {
        return io.getData(is);
    }

    public static void set(ItemStack item, String skill, double xp) {
        io.setData(item, new Data(skill, xp));
    }

    public static ItemStack with(String skill, double xp) {
        return io.withData(new Data(skill, xp));
    }

    public static ItemStack with(Map<String, Double> experienceMap) {
        return io.withData(new Data(experienceMap));
    }

    @Override
    public Material getMaterial() {
        return Material.SNOWBALL;
    }

    @Override
    public Class<Data> getType() {
        return ExperienceOrb.Data.class;
    }

    @Override
    public void applyLore(Data data, List<String> lore) {
        for (Map.Entry<String, Double> entry : data.getExperienceMap().entrySet()) {
            String skill = entry.getKey();
            double experience = entry.getValue();
            lore.add(C.WHITE + Form.capitalize(Localizer.dLocalize("snippets", "experienceorb", "contains")) + " " + C.UNDERLINE + C.WHITE + Form.f(experience, 0) + " " + Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(skill).getDisplayName() + C.GRAY + " " + Localizer.dLocalize("snippets", "experienceorb", "xp"));
        }
        lore.add(C.LIGHT_PURPLE + Localizer.dLocalize("snippets", "experienceorb", "rightclick") + " " + C.GRAY + Localizer.dLocalize("snippets", "experienceorb", "togainxp"));
    }

    @Override
    public void applyMeta(Data data, ItemMeta meta) {
        meta.addEnchant(Enchantment.BINDING_CURSE, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.setDisplayName(Localizer.dLocalize("snippets", "experienceorb", "xporb"));
    }

    @AllArgsConstructor
    @lombok.Data
    public static class Data {
        private Map<String, Double> experienceMap;

        public Data(String skill, double experience) {
            this.experienceMap = new HashMap<>();
            this.experienceMap.put(skill, experience);
        }

        public String getSkill() {
            return experienceMap.keySet().iterator().next();
        }

        public double getExperience() {
            return experienceMap.values().iterator().next();
        }

        public void apply(Player p) {
            for (Map.Entry<String, Double> entry : experienceMap.entrySet()) {
                String skill = entry.getKey();
                double experience = entry.getValue();
                Adapt.instance.getAdaptServer().getPlayer(p).getSkillLine(skill).giveXPFresh(Adapt.instance.getAdaptServer().getPlayer(p).getNot(), experience);
            }
        }
    }
}
