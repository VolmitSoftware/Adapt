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

package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;

public class AxeCraftLogSwap extends SimpleAdaptation<AxeCraftLogSwap.Config> {
  public static final Id ID = Id.adapt("axe/logswap");
  private static final int LOGS_PER_SWAP = 8;

  public AxeCraftLogSwap() {
    super(ID);
    registerConfiguration(Config.class);
    setLocalizationKey("adapt.axe.log_swap");
    setIcon(Id.minecraft("muddy_mangrove_roots"));
    setInterval(17773);

    List<Wood> woods = availableWoods(AdaptApi.platform().host().registries());
    ItemOps items = AdaptApi.platform().host().items();
    for (Wood source : woods) {
      for (Wood target : woods) {
        if (target == source || (source.optional && target.key.equals("mangrove"))) {
          continue;
        }
        List<Ingredient> ingredients = new ArrayList<>(LOGS_PER_SWAP + 1);
        for (int i = 0; i < LOGS_PER_SWAP; i++) {
          ingredients.add(Ingredient.of(source.log));
        }
        ingredients.add(Ingredient.of(target.sapling));
        registerRecipe(RecipeSpec.shapeless(Id.adapt("axe-swap" + source.key + target.key), ingredients, items.create(target.log, LOGS_PER_SWAP)));
      }
    }
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("oak_sapling"))
        .key("challenge_axe_log_swap_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_axe_log_swap_500", "axe.log-swap.conversions", 500, 400);
  }

  static List<Wood> availableWoods(RegistryLookup registries) {
    List<Wood> available = new ArrayList<>(Wood.ALL.size());
    for (Wood wood : Wood.ALL) {
      if (!wood.optional
          || (registries.exists(RegistryKind.ITEM, wood.log) && registries.exists(RegistryKind.ITEM, wood.sapling))) {
        available.add(wood);
      }
    }
    return available;
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + "+ " + TextFormat.GRAY + Text.key("adapt.axe.log_swap.lore1").resolve());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(CraftItemEvent e) {
    PlayerRef p = e.player();
    if (p == null || !hasActiveAdaptation(p)) {
      return;
    }
    Id recipe = e.recipeKey();
    if (e.recipeKind() == CraftItemEvent.RecipeKind.SHAPELESS && recipe != null
        && recipe.namespace().equals("adapt") && recipe.path().startsWith("axe-swap")) {
      addStat(p, "axe.log-swap.conversions", 1);
      fx(p.location().add(0.0D, 1.0D, 0.0D), FxPriority.TRANSITION)
          .particle(Particles.VILLAGER_HAPPY, 8, 0.0D, 0.0D, 0.0D, 0.4D, 0.02D)
          .dustBurst(4, 0.3D, 1.0F)
          .chord(Sounds.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 0.6F, 1.2F, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.6F);
    }
  }

  record Wood(String key, Id log, Id sapling, boolean optional) {
    static final List<Wood> ALL = List.of(
        new Wood("birch", Id.minecraft("birch_log"), Id.minecraft("birch_sapling"), false),
        new Wood("oak", Id.minecraft("oak_log"), Id.minecraft("oak_sapling"), false),
        new Wood("acacia", Id.minecraft("acacia_log"), Id.minecraft("acacia_sapling"), false),
        new Wood("darkoak", Id.minecraft("dark_oak_log"), Id.minecraft("dark_oak_sapling"), false),
        new Wood("jungle", Id.minecraft("jungle_log"), Id.minecraft("jungle_sapling"), false),
        new Wood("spruce", Id.minecraft("spruce_log"), Id.minecraft("spruce_sapling"), false),
        new Wood("mangrove", Id.minecraft("mangrove_log"), Id.minecraft("mangrove_propagule"), false),
        new Wood("cherry", Id.minecraft("cherry_log"), Id.minecraft("cherry_sapling"), true),
        new Wood("paleoak", Id.minecraft("pale_oak_log"), Id.minecraft("pale_oak_sapling"), true));
  }

  @ConfigDescription("Convert log types using a sapling in a crafting table.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      initialCost = 1;
      permanent = true;
      baseCost = 1;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
