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

public class BlockingChainArmorer extends SimpleAdaptation<BlockingChainArmorer.Config> {

  public BlockingChainArmorer() {
    super("blocking-chainarmorer");
    registerConfiguration(Config.class);
    setLocalizationKey("blocking.chain_armorer");
    setIcon(Id.minecraft("chainmail_chestplate"));
    setInterval(17774);
    ItemOps items = AdaptCore.platform().host().items();
    registerRecipe(new RecipeSpec.Shaped(Id.adapt("blocking-chainarmorer-boots"),
        List.of(
            "I I",
            "I I"),
        Map.of('I', Ingredient.of(Id.minecraft("iron_nugget"))),
        items.create(Id.minecraft("chainmail_boots"), 1)));
    registerRecipe(new RecipeSpec.Shaped(Id.adapt("blocking-chainarmorer-leggings"),
        List.of(
            "III",
            "I I",
            "I I"),
        Map.of('I', Ingredient.of(Id.minecraft("iron_nugget"))),
        items.create(Id.minecraft("chainmail_leggings"), 1)));
    registerRecipe(new RecipeSpec.Shaped(Id.adapt("blocking-chainarmorer-chestplate"),
        List.of(
            "I I",
            "III",
            "III"),
        Map.of('I', Ingredient.of(Id.minecraft("iron_nugget"))),
        items.create(Id.minecraft("chainmail_chestplate"), 1)));
    registerRecipe(new RecipeSpec.Shaped(Id.adapt("blocking-chainarmorer-helmet"),
        List.of(
            "III",
            "I I"),
        Map.of('I', Ingredient.of(Id.minecraft("iron_nugget"))),
        items.create(Id.minecraft("chainmail_helmet"), 1)));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("chainmail_chestplate"))
        .key("challenge_blocking_chain_25")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_blocking_chain_25", "blocking.chain-armorer.pieces-crafted", 25, 400);
  }

  @Handle
  public void on(CraftItemEvent e) {
    PlayerRef p = e.player();
    if (hasActiveAdaptation(p) && isAdaptationRecipe(e.recipeKey())) {
      addStat(p, "blocking.chain-armorer.pieces-crafted", 1);
      fx(p, FxPriority.TRANSITION)
          .column(Particles.WAX_ON, 6, 1.4D)
          .burst(Particles.CRIT_MAGIC, 3, 0.25D)
          .chord(Sounds.BLOCK_ANVIL_USE, 0.4F, 1.2F, Sounds.ITEM_ARMOR_EQUIP_CHAIN, 0.6F, 1.0F);
    }
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(BlockingMessages.CHAIN_ARMORER_LORE1));
  }



  @ConfigDescription("Craft Chainmail Armor using iron nuggets.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      permanent = true;
      baseCost = 1;
      costFactor = 0;
      maxLevel = 1;
      initialCost = 1;
    }
  }
}
