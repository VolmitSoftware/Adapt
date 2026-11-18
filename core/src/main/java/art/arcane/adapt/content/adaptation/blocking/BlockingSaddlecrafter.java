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

package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.BlockingMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.List;
import java.util.Map;

public class BlockingSaddlecrafter extends SimpleAdaptation<BlockingSaddlecrafter.Config> {

  public BlockingSaddlecrafter() {
    super("blocking-saddlecrafter");
    registerConfiguration(Config.class);
    setLocalizationKey("blocking.saddle_crafter");
    setIcon(Id.minecraft("leather_horse_armor"));
    setInterval(17774);
    ItemOps items = AdaptCore.platform().host().items();
    registerRecipe(new RecipeSpec.Shaped(Id.adapt("blocking-saddlecrafter"),
        List.of(
            "I I",
            "III"),
        Map.of('I', Ingredient.of(Id.minecraft("leather"))),
        items.create(Id.minecraft("saddle"), 1)));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("saddle"))
        .key("challenge_blocking_saddle_25")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_blocking_saddle_25", "blocking.saddlecrafter.saddles-crafted", 25, 400);
  }

  @Handle
  public void on(CraftItemEvent e) {
    PlayerRef p = e.player();
    if (hasActiveAdaptation(p) && isAdaptationRecipe(e.recipeKey())) {
      addStat(p, "blocking.saddlecrafter.saddles-crafted", 1);
      fx(p, FxPriority.TRANSITION)
          .column(Particles.WAX_ON, 5, 1.4D)
          .burst(Particles.CRIT_MAGIC, 3, 0.25D)
          .chord(Sounds.ENTITY_HORSE_SADDLE, 0.7F, 1.0F, Sounds.ITEM_ARMOR_EQUIP_LEATHER, 0.4F, 1.2F);
    }
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(BlockingMessages.SADDLE_CRAFTER_LORE1));
  }



  @ConfigDescription("Craft a Saddle using leather.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      enabled = true;
      permanent = true;
      baseCost = 5;
      costFactor = 0;
      maxLevel = 1;
      initialCost = 1;
    }
  }
}
