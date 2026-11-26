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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.item.DataBoundItem;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.SnippetsMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.TooltipPart;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.misc.ItemLooks;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

@AllArgsConstructor
@Data
public class KnowledgeOrb implements DataBoundItem<KnowledgeOrb.Data> {
  public static KnowledgeOrb io = new KnowledgeOrb();
  private static final Id SNOWBALL = Id.minecraft("snowball");

  public static Data get(ItemRef item) {
    return io.getData(item);
  }

  public static String getSkill(ItemRef item) {
    Data data = io.getData(item);
    return data == null ? null : data.getSkill();
  }

  public static long getKnowledge(ItemRef item) {
    Data data = io.getData(item);
    return data == null ? 0 : data.getKnowledge();
  }

  public static void set(ItemRef item, String skill, int knowledge) {
    io.setData(item, new Data(skill, knowledge));
  }

  public static ItemRef with(String skill, int knowledge) {
    return io.withData(new Data(skill, knowledge));
  }

  public static ItemRef with(Map<String, Integer> knowledgeMap) {
    return io.withData(new Data(new HashMap<>(knowledgeMap)));
  }

  private static CustomModel model() {
    return CustomModel.get(SNOWBALL, "items", "knowledge-orb");
  }

  @Override
  public Id getMaterial() {
    return model().material();
  }

  @Override
  public ItemRef blank() {
    CustomModel model = model();
    ItemRef item = model.toItemStack();
    ItemLooks.applyUnlessCustom(item, "knowledge-orb", model);
    return item;
  }

  @Override
  public boolean acceptsMaterial(Id material) {
    return true;
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
      lore.add(AdaptLanguage.textStyled(C.WHITE.toString(),
          SnippetsMessages.KNOWLEDGE_ORB_CONTENTS,
          trusted("knowledge", C.UNDERLINE + "" + C.WHITE + knowledge),
          trusted("skill", AdaptCore.get().getAdaptServer().getSkillRegistry().getSkill(skill).getDisplayName())
      ));
    }
    lore.add(AdaptLanguage.textStyled(C.LIGHT_PURPLE.toString(), SnippetsMessages.KNOWLEDGE_ORB_USE));
  }

  @Override
  public void applyMeta(Data data, ItemRef item) {
    item.setEnchantment(Id.minecraft("binding_curse"), 10);
    item.hideTooltip(TooltipPart.ATTRIBUTES);
    item.hideTooltip(TooltipPart.ENCHANTMENTS);
    item.setDisplayName(AdaptLanguage.text(SnippetsMessages.KNOWLEDGE_ORB_KNOWLEDGE_ORB));
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

    public boolean apply(PlayerRef p) {
      AdaptCore core = AdaptCore.current();
      if (p == null || knowledgeMap == null || knowledgeMap.isEmpty() || core == null) {
        return false;
      }
      AdaptServer adaptServer = core.getAdaptServer();
      AdaptPlayer adaptPlayer = adaptServer == null
          ? null
          : adaptServer.getOnlineAdaptPlayer(p.uuid());
      if (adaptPlayer == null || !adaptPlayer.isRuntimeReady() || adaptPlayer.getPlayer() != p) {
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
