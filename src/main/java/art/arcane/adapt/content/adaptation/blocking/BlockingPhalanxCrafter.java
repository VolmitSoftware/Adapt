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

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.BlockingMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.recipe.MaterialChar;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.ShieldMeta;

import java.util.List;

public class BlockingPhalanxCrafter extends SimpleAdaptation<BlockingPhalanxCrafter.Config> {
  private static final int NETHERITE_SHIELD_MAX_DURABILITY = 1200;
  private final AdaptRecipe netheriteRecipe;

  public BlockingPhalanxCrafter() {
    super("blocking-phalanx-crafter");
    registerConfiguration(Config.class);
    setLocalizationKey("blocking.phalanx_crafter");
    setIcon(Material.SHIELD);
    registerRecipe(AdaptRecipe.shaped()
        .key("blocking-phalanx-field-shield")
        .ingredient(new MaterialChar('W', Material.WHITE_WOOL))
        .ingredient(new MaterialChar('P', Material.OAK_PLANKS))
        .ingredient(new MaterialChar('I', Material.IRON_INGOT))
        .shapes(List.of(
            "WWW",
            "PIP",
            " P "))
        .result(new ItemStack(Material.SHIELD, 1))
        .build());
    netheriteRecipe = AdaptRecipe.shaped()
        .key("blocking-phalanx-netherite-shield")
        .requiredLevel(2)
        .ingredient(new MaterialChar('N', Material.NETHERITE_INGOT))
        .ingredient(new MaterialChar('S', Material.SHIELD))
        .shapes(List.of(
            " N ",
            "NSN",
            " N "))
        .result(buildNetheriteShield())
        .build();
    registerRecipe(netheriteRecipe);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SHIELD)
        .key("challenge_blocking_phalanx_25")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_blocking_phalanx_25", "blocking.phalanx-crafter.items-crafted", 25, 400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.NETHERITE_INGOT)
        .key("challenge_blocking_phalanx_netherite")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
  }

  private static ItemStack buildNetheriteShield() {
    return buildNetheriteShield(new ItemStack(Material.SHIELD, 1));
  }

  static ItemStack buildNetheriteShield(ItemStack source) {
    ItemStack shield = source != null && source.getType() == Material.SHIELD
        ? source.clone()
        : new ItemStack(Material.SHIELD, 1);
    shield.setAmount(1);
    ItemMeta meta = shield.getItemMeta();
    if (meta instanceof ShieldMeta shieldMeta) {
      applyDefaultShieldDesign(shieldMeta);
    }
    if (meta instanceof Damageable damageable) {
      damageable.setMaxDamage(NETHERITE_SHIELD_MAX_DURABILITY);
      damageable.setDamage(0);
    }
    if (meta != null) {
      meta.setDisplayName(C.GOLD + AdaptLanguage.text(BlockingMessages.PHALANX_CRAFTER_ITEM_NAME));
      shield.setItemMeta(meta);
    }
    return shield;
  }

  static void applyDefaultShieldDesign(ShieldMeta meta) {
    if (hasVisibleShieldDesign(meta.getBaseColor(), meta.numberOfPatterns())) {
      return;
    }

    meta.setBaseColor(DyeColor.BLACK);
    meta.setPatterns(List.of(
        new Pattern(DyeColor.ORANGE, PatternType.BORDER),
        new Pattern(DyeColor.LIGHT_GRAY, PatternType.RHOMBUS)
    ));
  }

  static boolean hasVisibleShieldDesign(DyeColor baseColor, int patternCount) {
    return patternCount > 0 || (baseColor != null && baseColor != DyeColor.WHITE);
  }

  static ItemStack findShield(ItemStack[] matrix) {
    if (matrix == null) {
      return null;
    }
    for (ItemStack item : matrix) {
      if (item != null && item.getType() == Material.SHIELD) {
        return item;
      }
    }
    return null;
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(BlockingMessages.PHALANX_CRAFTER_LORE1));
    if (level >= 2) {
      v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(BlockingMessages.PHALANX_CRAFTER_LORE2));
    } else {
      v.addLore(C.DARK_GRAY + "+ " + C.DARK_GRAY + AdaptLanguage.text(BlockingMessages.PHALANX_CRAFTER_LORE2));
    }
  }

  @EventHandler
  public void on(CraftItemEvent e) {
    if (!(e.getWhoClicked() instanceof Player p) || !isAdaptationRecipe(e.getRecipe())) {
      return;
    }

    boolean netherite = netheriteRecipe.is(e.getRecipe());
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

  @EventHandler
  public void on(PrepareItemCraftEvent e) {
    if (e.getRecipe() == null || !netheriteRecipe.is(e.getRecipe())) {
      return;
    }

    ItemStack source = findShield(e.getInventory().getMatrix());
    if (source != null) {
      e.getInventory().setResult(buildNetheriteShield(source));
    }
  }

  private void craftCue(Player p, boolean netherite) {
    if (netherite) {
      fx(p, FxPriority.TRANSITION)
          .column(Particle.WAX_ON, 8, 1.6D)
          .burst(Particles.END_ROD, 6, 0.3D)
          .chord(Sound.BLOCK_ANVIL_USE, 0.5F, 0.7F, Sound.ITEM_SHIELD_BLOCK, 0.7F, 0.8F, Sound.BLOCK_BEACON_ACTIVATE, 0.3F, 1.3F);
      return;
    }
    fx(p, FxPriority.TRANSITION)
        .column(Particle.WAX_ON, 5, 1.4D)
        .burst(Particles.CRIT_MAGIC, 3, 0.25D)
        .chord(Sound.BLOCK_ANVIL_USE, 0.4F, 1.2F, Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.5F, 1.0F);
  }

  private void denyCue(Player p) {
    fx(p, FxPriority.AMBIENT)
        .sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.6F);
  }


  @ConfigDescription("Craft banner-faced shields directly, and reinforce shields with netherite for bonus durability.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 3;
      costFactor = 0;
      maxLevel = 2;
      initialCost = 2;
    }
  }
}
