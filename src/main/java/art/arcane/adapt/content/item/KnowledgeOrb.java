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

package art.arcane.adapt.content.item;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.SnippetsMessages;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.item.DataItem;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.util.common.format.C;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

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
      lore.add(C.WHITE + AdaptLanguage.text(
          SnippetsMessages.KNOWLEDGE_ORB_CONTENTS,
          trusted("knowledge", C.UNDERLINE + "" + C.WHITE + knowledge),
          trusted("skill", Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(skill).getDisplayName())
      ));
    }
    lore.add(C.LIGHT_PURPLE + AdaptLanguage.text(SnippetsMessages.KNOWLEDGE_ORB_USE));
  }

  @Override
  public void applyMeta(Data data, ItemMeta meta) {
    meta.addEnchant(Enchantment.BINDING_CURSE, 10, true);
    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
    meta.setDisplayName(AdaptLanguage.text(SnippetsMessages.KNOWLEDGE_ORB_KNOWLEDGE_ORB));
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

    public boolean apply(Player p) {
      if (p == null || knowledgeMap == null || knowledgeMap.isEmpty()
          || Adapt.instance == null) {
        return false;
      }
      AdaptServer adaptServer = Adapt.instance.getAdaptServer();
      AdaptPlayer adaptPlayer = adaptServer == null
          ? null
          : adaptServer.getOnlineAdaptPlayer(p.getUniqueId());
      if (adaptPlayer == null || !adaptPlayer.isRuntimeReady()
          || adaptPlayer.getPlayer() != p) {
        return false;
      }

      Map<PlayerSkillLine, Integer> awards = new LinkedHashMap<>();
      for (Map.Entry<String, Integer> entry : knowledgeMap.entrySet()) {
        Integer knowledge = entry.getValue();
        PlayerSkillLine skillLine = adaptPlayer.getSkillLine(entry.getKey());
        if (knowledge == null || skillLine == null) {
          return false;
        }
        awards.merge(skillLine, knowledge, Integer::sum);
      }
      for (Map.Entry<PlayerSkillLine, Integer> award : awards.entrySet()) {
        award.getKey().giveKnowledge(award.getValue());
      }
      return true;
    }
  }
}
