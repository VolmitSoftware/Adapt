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
import art.arcane.adapt.api.xp.XpAward;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.SnippetsMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.TooltipPart;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.misc.ItemLooks;
import art.arcane.volmlib.util.format.Form;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

@AllArgsConstructor
@Data
public class ExperienceOrb implements DataBoundItem<ExperienceOrb.Data> {
  public static ExperienceOrb io = new ExperienceOrb();
  private static final Id SNOWBALL = Id.minecraft("snowball");

  public static Data get(ItemRef item) {
    return io.getData(item);
  }

  public static void set(ItemRef item, String skill, double xp) {
    io.setData(item, new Data(skill, xp));
  }

  public static ItemRef with(String skill, double xp) {
    return io.withData(new Data(skill, xp));
  }

  public static ItemRef with(Map<String, Double> experienceMap) {
    return io.withData(new Data(new HashMap<>(experienceMap)));
  }

  private static CustomModel model() {
    return CustomModel.get(SNOWBALL, "items", "experience-orb");
  }

  @Override
  public Id getMaterial() {
    return model().material();
  }

  @Override
  public ItemRef blank() {
    CustomModel model = model();
    ItemRef item = model.toItemStack();
    ItemLooks.applyUnlessCustom(item, "experience-orb", model);
    return item;
  }

  @Override
  public boolean acceptsMaterial(Id material) {
    return true;
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
      lore.add(AdaptLanguage.textStyled(C.WHITE.toString(),
          SnippetsMessages.EXPERIENCE_ORB_CONTENTS,
          trusted("experience", C.UNDERLINE + "" + C.WHITE + Form.f(experience, 0)),
          trusted("skill", AdaptCore.get().getAdaptServer().getSkillRegistry().getSkill(skill).getDisplayName() + C.GRAY)
      ));
    }
    lore.add(AdaptLanguage.textStyled(C.LIGHT_PURPLE.toString(), SnippetsMessages.EXPERIENCE_ORB_USE));
  }

  @Override
  public void applyMeta(Data data, ItemRef item) {
    item.setEnchantment(Id.minecraft("binding_curse"), 10);
    item.hideTooltip(TooltipPart.ATTRIBUTES);
    item.hideTooltip(TooltipPart.ENCHANTMENTS);
    item.setDisplayName(AdaptLanguage.text(SnippetsMessages.EXPERIENCE_ORB_XPORB));
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

    public boolean apply(PlayerRef p) {
      AdaptCore core = AdaptCore.current();
      if (p == null || experienceMap == null || experienceMap.isEmpty() || core == null) {
        return false;
      }
      AdaptServer adaptServer = core.getAdaptServer();
      AdaptPlayer adaptPlayer = adaptServer == null
          ? null
          : adaptServer.getOnlineAdaptPlayer(p.uuid());
      if (adaptPlayer == null || !adaptPlayer.isRuntimeReady() || adaptPlayer.getPlayer() != p) {
        return false;
      }

      Map<PlayerSkillLine, Double> awards = new LinkedHashMap<>();
      for (Map.Entry<String, Double> entry : experienceMap.entrySet()) {
        Double experience = entry.getValue();
        PlayerSkillLine skillLine = adaptPlayer.getSkillLine(entry.getKey());
        if (experience == null || !Double.isFinite(experience) || experience <= 0D || skillLine == null) {
          return false;
        }
        awards.merge(skillLine, experience, Double::sum);
      }
      for (Map.Entry<PlayerSkillLine, Double> award : awards.entrySet()) {
        XpAward redemption = new XpAward("consumable:experience-orb", award.getValue(), null, true, XpAward.Kind.CONSUMABLE);
        award.getKey().award(adaptPlayer.getNot(), redemption, award.getValue(), adaptPlayer.getData().getXpEarnings());
      }
      return true;
    }
  }
}
