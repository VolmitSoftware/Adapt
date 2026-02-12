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
public class KnowledgeOrb implements DataItem<KnowledgeOrb.Data> {
    public static KnowledgeOrb io = new KnowledgeOrb();

    public static Data get(ItemStack is) {
        return io.getData(is);
    }

    public static String getSkill(ItemStack stack) {
        if (io.getData(stack) != null) {
            return io.getData(stack).getSkill();
        }

        return null;
    }

    public static long getKnowledge(ItemStack stack) {
        if (io.getData(stack) != null) {
            return io.getData(stack).getKnowledge();
        }

        return 0;
    }

    public static void set(ItemStack item, String skill, int knowledge) {
        io.setData(item, new Data(skill, knowledge));
    }

    public static ItemStack with(String skill, int knowledge) {
        return io.withData(new Data(skill, knowledge));
    }

    public static ItemStack with(Map<String, Integer> knowledgeMap) {
        return io.withData(new Data(knowledgeMap));
    }

    @Override
    public Material getMaterial() {
        return Material.SNOWBALL;
    }

    @Override
    public Class<Data> getType() {
        return KnowledgeOrb.Data.class;
    }

    @Override
    public void applyLore(Data data, List<String> lore) {
        for (Map.Entry<String, Integer> entry : data.getKnowledgeMap().entrySet()) {
            String skill = entry.getKey();
            int knowledge = entry.getValue();
            lore.add(C.WHITE + Localizer.dLocalize("snippets.knowledge_orb.contains") + " " + C.UNDERLINE + C.WHITE + "" + knowledge + " " + Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(skill).getDisplayName() + " " + Localizer.dLocalize("snippets.knowledge_orb.knowledge"));
        }
        lore.add(C.LIGHT_PURPLE + Localizer.dLocalize("snippets.knowledge_orb.rightclick") + " " + C.GRAY + Localizer.dLocalize("snippets.knowledge_orb.togainknowledge"));
    }

    @Override
    public void applyMeta(Data data, ItemMeta meta) {
        meta.addEnchant(Enchantment.BINDING_CURSE, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.setDisplayName(Localizer.dLocalize("snippets.knowledge_orb.knowledge_orb"));
    }

    @AllArgsConstructor
    @lombok.Data
    public static class Data {
        private Map<String, Integer> knowledgeMap;

        public Data(String skill, int knowledge) {
            this.knowledgeMap = new HashMap<>();
            this.knowledgeMap.put(skill, knowledge);
        }

        public String getSkill() {
            return knowledgeMap.keySet().iterator().next();
        }

        public int getKnowledge() {
            return knowledgeMap.values().iterator().next();
        }

        public void apply(Player p) {
            for (Map.Entry<String, Integer> entry : knowledgeMap.entrySet()) {
                String skill = entry.getKey();
                int knowledge = entry.getValue();
                Adapt.instance.getAdaptServer().getPlayer(p).getSkillLine(skill).giveKnowledge(knowledge);
            }
        }
    }
}
