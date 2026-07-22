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

package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.AdvancementMessages;
import art.arcane.adapt.localization.catalog.CraftingMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdvancementSpec;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.Map;

public class CraftingDeconstruction extends SimpleAdaptation<CraftingDeconstruction.Config> {
  public CraftingDeconstruction() {
    super("crafting-deconstruction");
    registerConfiguration(Config.class);
    setIcon(Material.SHEARS);
    setInterval(5590);
    AdvancementSpec deconstruction5k = AdvancementSpec.challenge(
        "challenge_crafting_decon_5k",
        Material.IRON_INGOT,
        AdaptLanguage.text(AdvancementMessages.CHALLENGE_CRAFTING_DECON_5K_TITLE),
        AdaptLanguage.text(AdvancementMessages.CHALLENGE_CRAFTING_DECON_5K_DESCRIPTION)
    );
    AdvancementSpec deconstruction200 = AdvancementSpec.challenge(
        "challenge_crafting_decon_200",
        Material.SHEARS,
        AdaptLanguage.text(AdvancementMessages.CHALLENGE_CRAFTING_DECON_200_TITLE),
        AdaptLanguage.text(AdvancementMessages.CHALLENGE_CRAFTING_DECON_200_DESCRIPTION)
    ).withChild(deconstruction5k);
    registerAdvancementSpec(deconstruction200);
    registerStatTracker(deconstruction200.statTracker("crafting.deconstruction.items-deconstructed", 200, 300));
    registerStatTracker(deconstruction5k.statTracker("crafting.deconstruction.items-deconstructed", 5000, 1000));
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + AdaptLanguage.text(CraftingMessages.DECONSTRUCTION_LORE1));
    v.addLore(C.GREEN + AdaptLanguage.text(CraftingMessages.DECONSTRUCTION_LORE2));
  }

  public ItemStack getDeconstructionOffering(ItemStack forStuff) {
    if (forStuff == null) return null;

    int maxPow = 0;
    Recipe selectedRecipe = null;

    for (Recipe recipe : Bukkit.getRecipesFor(forStuff)) {
      int currentPower = 0;
      if (recipe instanceof ShapelessRecipe r) {
        for (ItemStack ingredient : r.getIngredientList()) {
          if (ingredient == null) {
            continue;
          }
          currentPower += ingredient.getAmount();
        }
      } else if (recipe instanceof ShapedRecipe r) {
        for (ItemStack ingredient : r.getIngredientMap().values()) {
          if (ingredient == null) {
            continue;
          }
          currentPower += ingredient.getAmount();
        }
      }
      if (currentPower > maxPow) {
        selectedRecipe = recipe;
        maxPow = currentPower;
      }
    }

    if (selectedRecipe == null) return null;

    int v = 0;
    int outa = 1;
    ItemStack sel = null;

    if (selectedRecipe instanceof ShapelessRecipe r) {
      for (ItemStack i : r.getIngredientList()) {
        int amount = i.getAmount() * forStuff.getAmount();
        if (amount > v) {
          v = amount;
          sel = i;
          outa = r.getResult().getAmount();
        }
      }
    } else {
      ShapedRecipe r = (ShapedRecipe) selectedRecipe;
      Map<Material, Integer> ings = new HashMap<>();
      for (ItemStack ingredient : r.getIngredientMap().values()) {
        if (ingredient == null) {
          continue;
        }
        ings.merge(ingredient.getType(), ingredient.getAmount(), Integer::sum);
      }

      for (Map.Entry<Material, Integer> entry : ings.entrySet()) {
        int amount = entry.getValue() * forStuff.getAmount();
        if (amount > v) {
          v = amount;
          sel = new ItemStack(entry.getKey(), entry.getValue());
          outa = r.getResult().getAmount();
        }
      }
    }

    if (sel != null && sel.getAmount() * forStuff.getAmount() > 1) {
      int a = ((sel.getAmount() * forStuff.getAmount()) / outa) / 2;
      if (a >= 1 && a <= sel.getMaxStackSize() && getValue(sel) < getValue(forStuff)) {
        sel.setAmount(a);
        return sel.clone();
      }
    }

    return null;
  }


  @EventHandler
  public void on(PlayerInteractEvent e) {
    if (e.getHand() == EquipmentSlot.OFF_HAND) {
      return;
    }

    Player player = e.getPlayer();
    ItemStack mainHandItem = player.getInventory().getItemInMainHand();
    if (!hasActiveAdaptation(player)) {
      return;
    }

    if (!player.isSneaking() || mainHandItem.getType() != Material.SHEARS) {
      return;
    }

    // Perform a ray trace for 6 blocks looking for an item
    RayTraceResult rayTrace = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getLocation().getDirection(), 6, entity -> entity instanceof Item);
    if (rayTrace != null && rayTrace.getHitEntity() instanceof Item itemEntity) {
      processItemInteraction(player, mainHandItem, itemEntity);
    }
  }

  private void processItemInteraction(Player player, ItemStack mainHandItem, Item itemEntity) {
    ItemStack forStuff = itemEntity.getItemStack();
    ItemStack offering = getDeconstructionOffering(forStuff);
    Location itemLocation = itemEntity.getLocation();

    if (offering != null) {
      itemEntity.setItemStack(offering);
      fx(itemLocation, FxPriority.COMBAT)
          .particle(Particles.ITEM_CRACK, 18, 0, 0, 0, 0.15D, 0.15D, forStuff)
          .ring(Particle.WAX_ON, 0.5D, 10, 0.2D)
          .chord(Sound.BLOCK_BASALT_BREAK, 1F, 0.2F, Sound.BLOCK_BEEHIVE_SHEAR, 1F, 0.7F, Sound.ITEM_SHIELD_BREAK, 0.4F, 1.4F);
      xp(player, getValue(offering), "deconstruct");
      addStat(player, "crafting.deconstruction.items-deconstructed", 1);

      Damageable damageable = (Damageable) mainHandItem.getItemMeta();
      int newDamage = damageable.getDamage() + 8 * forStuff.getAmount();
      int maxDurability = mainHandItem.getType().getMaxDurability();
      if (newDamage >= maxDurability) {
        player.getInventory().setItemInMainHand(null);
        fx(itemLocation, FxPriority.COMBAT)
            .particle(Particles.ITEM_CRACK, 6, 0, 0.1D, 0, 0.2D, 0.05D, new ItemStack(Material.SHEARS))
            .sound(Sound.ENTITY_ITEM_BREAK, 0.8F, 1.0F);
      } else {
        damageable.setDamage(newDamage);
        mainHandItem.setItemMeta(damageable);
        if (newDamage >= maxDurability * 0.9D) {
          fx(itemLocation, FxPriority.TRANSITION)
              .particle(Particle.ELECTRIC_SPARK, 6, 0, 0.1D, 0, 0.2D, 0.05D)
              .burst(Particles.CRIT_MAGIC, 3, 0.15D)
              .sound(Sound.ENTITY_ITEM_BREAK, 0.4F, 1.6F);
        }
      }
    } else {
      fx(itemLocation, FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 4, 0.15D)
          .particle(Particle.WAX_ON, 2, 0, 0.2D, 0, 0.06D, 0.02D)
          .chord(Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 1F, 1F, Sound.BLOCK_FIRE_EXTINGUISH, 0.3F, 1.5F);
    }
  }



  @ConfigDescription("Deconstruct blocks and items into salvageable base components using shears.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 9;
      costFactor = 1.0;
      initialCost = 8;
      maxLevel = 1;
    }
  }
}
