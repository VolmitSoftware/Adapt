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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.item.MaxDurability;
import art.arcane.adapt.platform.EngineFeature;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.PrepareItemCraftEvent;
import art.arcane.adapt.platform.item.BannerLayer;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.ShieldDesign;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Map;

public class BlockingPhalanxCrafter extends SimpleAdaptation<BlockingPhalanxCrafter.Config> {
  public static final Id ID = Id.adapt("blocking/phalanx_crafter");
  private static final Id SHIELD = Id.minecraft("shield");
  private static final int NETHERITE_SHIELD_MAX_DURABILITY = 1200;
  private final RecipeSpec netheriteRecipe;

  public BlockingPhalanxCrafter() {
    super(ID);
    needs(PlatformNeed.emulatedFeature(EngineFeature.ITEM_STACK_LIMITS));
    registerConfiguration(Config.class);
    setLocalizationKey("adapt.blocking.phalanx_crafter");
    setIcon(SHIELD);
    registerRecipe(RecipeSpec.shaped(Id.adapt("blocking-phalanx-field-shield"), List.of(
            "WWW",
            "PIP",
            " P "), Map.of('W', Ingredient.of(Id.minecraft("white_wool")),
            'P', Ingredient.of(Id.minecraft("oak_planks")),
            'I', Ingredient.of(Id.minecraft("iron_ingot"))), AdaptApi.platform().host().items().create(SHIELD, 1)));
    netheriteRecipe = RecipeSpec.shaped(Id.adapt("blocking-phalanx-netherite-shield"), List.of(
            " N ",
            "NSN",
            " N "), Map.of('N', Ingredient.of(Id.minecraft("netherite_ingot")),
            'S', Ingredient.of(SHIELD)), buildNetheriteShield()).withRequiredLevel(2);
    registerRecipe(netheriteRecipe);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("shield"))
        .key("challenge_blocking_phalanx_25")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_blocking_phalanx_25", "blocking.phalanx-crafter.items-crafted", 25, 400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("netherite_ingot"))
        .key("challenge_blocking_phalanx_netherite")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
  }

  private static ItemRef buildNetheriteShield() {
    return buildNetheriteShield(null);
  }

  static ItemRef buildNetheriteShield(ItemRef source) {
    ItemRef shield = source != null && SHIELD.equals(source.type())
        ? source.copy()
        : AdaptApi.platform().host().items().create(SHIELD, 1);
    shield.setAmount(1);
    ShieldDesign design = shield.shieldDesign();
    if (design != null) {
      applyDefaultShieldDesign(shield, design);
    }
    MaxDurability.set(shield, NETHERITE_SHIELD_MAX_DURABILITY);
    shield.setDamage(0);
    shield.setDisplayName(TextFormat.GOLD + Text.key("adapt.blocking.phalanx_crafter.item_name").resolve());
    return shield;
  }

  static void applyDefaultShieldDesign(ItemRef shield, ShieldDesign design) {
    if (hasVisibleShieldDesign(design.baseColor(), design.patterns().size())) {
      return;
    }

    shield.setShieldDesign(new ShieldDesign(DyeColor.BLACK, List.of(
        new BannerLayer(DyeColor.ORANGE, Id.minecraft("border")),
        new BannerLayer(DyeColor.LIGHT_GRAY, Id.minecraft("rhombus"))
    )));
  }

  static boolean hasVisibleShieldDesign(DyeColor baseColor, int patternCount) {
    return patternCount > 0 || baseColor != null;
  }

  static ItemRef findShield(List<ItemRef> matrix) {
    if (matrix == null) {
      return null;
    }
    for (ItemRef item : matrix) {
      if (item != null && SHIELD.equals(item.type())) {
        return item;
      }
    }
    return null;
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + "+ " + TextFormat.GRAY + Text.key("adapt.blocking.phalanx_crafter.lore1").resolve());
    if (level >= 2) {
      v.addLore(TextFormat.GREEN + "+ " + TextFormat.GRAY + Text.key("adapt.blocking.phalanx_crafter.lore2").resolve());
    } else {
      v.addLore(TextFormat.DARK_GRAY + "+ " + TextFormat.DARK_GRAY + Text.key("adapt.blocking.phalanx_crafter.lore2").resolve());
    }
  }

  @Handle
  public void on(CraftItemEvent e) {
    PlayerRef p = e.player();
    if (!isAdaptationRecipe(e.recipeKey())) {
      return;
    }

    boolean netherite = netheriteRecipe.key().equals(e.recipeKey());
    int requiredLevel = netherite ? 2 : 1;
    if (getActiveLevel(p) < requiredLevel) {
      e.setCancelled(true);
      denyCue(p);
      return;
    }

    addStat(p, "blocking.phalanx-crafter.items-crafted", 1);
    if (netherite) {
      grantOnce(p, "challenge_blocking_phalanx_netherite");
    }
    craftCue(p, netherite);
  }

  @Handle
  public void on(PrepareItemCraftEvent e) {
    if (e.recipeKey() == null || !netheriteRecipe.key().equals(e.recipeKey())) {
      return;
    }

    ItemRef source = findShield(e.matrix());
    if (source != null) {
      e.setResult(buildNetheriteShield(source));
    }
  }

  private void craftCue(PlayerRef p, boolean netherite) {
    if (netherite) {
      fx(p, FxPriority.TRANSITION)
          .column(Particles.WAX_ON, 8, 1.6D)
          .burst(Particles.END_ROD, 6, 0.3D)
          .chord(Sounds.BLOCK_ANVIL_USE, 0.5F, 0.7F, Sounds.ITEM_SHIELD_BLOCK, 0.7F, 0.8F, Sounds.BLOCK_BEACON_ACTIVATE, 0.3F, 1.3F);
      return;
    }
    fx(p, FxPriority.TRANSITION)
        .column(Particles.WAX_ON, 5, 1.4D)
        .burst(Particles.CRIT, 3, 0.25D)
        .chord(Sounds.BLOCK_ANVIL_USE, 0.4F, 1.2F, Sounds.ITEM_ARMOR_EQUIP_LEATHER, 0.5F, 1.0F);
  }

  private void denyCue(PlayerRef p) {
    fx(p, FxPriority.AMBIENT)
        .sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.6F);
  }

  @ConfigDescription("Unlock a wool-plank field shield recipe, then reinforce a shield with four netherite ingots for 1,200 durability and a preserved or default banner face.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 1;
      costFactor = 0.5;
      maxLevel = 2;
      initialCost = 1;
    }
  }
}
