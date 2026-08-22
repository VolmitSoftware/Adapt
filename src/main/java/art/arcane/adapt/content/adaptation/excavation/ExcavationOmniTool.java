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

package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ExcavationMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.item.ItemListings;
import art.arcane.adapt.content.item.multiItems.OmniTool;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public class ExcavationOmniTool extends SimpleAdaptation<ExcavationOmniTool.Config> {
  private static final OmniTool omniTool = new OmniTool();

  public ExcavationOmniTool() {
    super("excavation-omnitool");
    registerConfiguration(ExcavationOmniTool.Config.class);
    setLocalizationKey("excavation.omni_tool");
    setIcon(Material.DISC_FRAGMENT_5);
    setInterval(20202);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_PICKAXE)
        .key("challenge_excavation_omni_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_PICKAXE)
            .key("challenge_excavation_omni_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_excavation_omni_1k", "excavation.omni-tool.auto-swaps", 1000, 400);
    registerMilestone("challenge_excavation_omni_25k", "excavation.omni-tool.auto-swaps", 25000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GRAY + AdaptLanguage.text(ExcavationMessages.OMNI_TOOL_LORE1));
    v.addLore(C.GRAY + AdaptLanguage.text(ExcavationMessages.OMNI_TOOL_LORE2));
    v.addLore(C.GREEN + AdaptLanguage.text(ExcavationMessages.OMNI_TOOL_LORE3));
    v.addLore(C.RED + AdaptLanguage.text(ExcavationMessages.OMNI_TOOL_LORE4));
    v.addLore(C.GRAY + AdaptLanguage.text(ExcavationMessages.OMNI_TOOL_LORE5));
    statLore(v, C.GREEN, "", level + getConfig().startingSlots, 6);
    v.addLore(C.UNDERLINE + AdaptLanguage.text(ExcavationMessages.OMNI_TOOL_LORE7));


  }


  @EventHandler(priority = EventPriority.HIGH)
  public void on(EntityDamageByEntityEvent e) {
    if (e.getDamager() instanceof Player p && validateTool(p.getInventory().getItemInMainHand())) {
      //deny if the tool durability is about to break
      if (p.getInventory().getItemInMainHand().getType().getMaxDurability() - p.getInventory().getItemInMainHand().getDurability() <= 2) {
        e.setCancelled(true);
        return;
      }
      if (!hasActiveAdaptation(p) && validateTool(p.getInventory().getItemInMainHand())) {
        e.setCancelled(true);
        return;
      }
      ItemStack hand = p.getInventory().getItemInMainHand();
      Damageable inHand = (Damageable) hand.getItemMeta();

      if (!validateTool(hand)) {
        return;
      }
      if (!isSword(hand)) {
        J.runEntity(p, () -> p.getInventory().setItemInMainHand(omniTool.nextSword(hand)));
        swapFx(p);
      }
      if (inHand != null && inHand.hasDamage()) {
        if ((hand.getType().getMaxDurability() - inHand.getDamage() - 2) <= 2) {
          e.setCancelled(true);
          nearBreakFx(p);
        }
      }

    }
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void on(BlockBreakEvent e) {
    Player p = e.getPlayer();
    if (validateTool(p.getInventory().getItemInMainHand())) {
      //deny if the tool durability is about to break
      if (p.getInventory().getItemInMainHand().getType().getMaxDurability() - p.getInventory().getItemInMainHand().getDurability() <= 2) {
        e.setCancelled(true);
        return;
      }


      //deny if they dont have the adaptation
      if (!hasActiveAdaptation(p)) {
        e.setCancelled(true);
        return;
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void on(PlayerInteractEvent e) {
    Player p = e.getPlayer();
    if (validateTool(p.getInventory().getItemInMainHand())) {
      //deny if the tool durability is about to break
      if (p.getInventory().getItemInMainHand().getType().getMaxDurability() - p.getInventory().getItemInMainHand().getDurability() <= 2) {
        e.setCancelled(true);
        return;
      }

      if (!hasActiveAdaptation(p)) {
        return;
      }
      Action action = e.getAction();
      if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
        if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) {
          return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        Damageable imHand = (Damageable) hand.getItemMeta();
        Block block = action == Action.RIGHT_CLICK_BLOCK ? e.getClickedBlock() : p.getTargetBlockExact(5);
        if (block != null) {
          if (ItemListings.farmable.contains(block.getType())) {
            if (!isHoe(hand)) {
              e.setCancelled(true);
              J.runEntity(p, () -> p.getInventory().setItemInMainHand(omniTool.nextHoe(hand)));
              swapFx(p);
              if (imHand != null && imHand.hasDamage()) {
                if ((hand.getType().getMaxDurability() - imHand.getDamage() - 2) <= 2) {
                  nearBreakFx(p);
                }
              }
            }
          } else if (ItemListings.burnable.contains(block.getType())) {
            J.runEntity(p, () -> p.getInventory().setItemInMainHand(omniTool.nextFnS(hand)));
            swapFx(p);
            if (imHand != null && imHand.hasDamage()) {
              if ((hand.getType().getMaxDurability() - imHand.getDamage() - 2) <= 2) {
                e.setCancelled(true);
                nearBreakFx(p);
              }
            }
          }
        }
      }
    }

  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerDropItemEvent e) {
    Player p = e.getPlayer();
    if (!hasActiveAdaptation(p)) {
      return;
    }
    if (p.isSneaking()) {
      if (validateTool(e.getItemDrop().getItemStack())) {
        List<ItemStack> drops = omniTool.explode(e.getItemDrop().getItemStack());
        for (ItemStack i : drops) {
          Damageable iDmgable = (Damageable) i.getItemMeta();
          if (i.hasItemMeta()) {
            ItemMeta im = i.getItemMeta().clone();
            ItemMeta im2 = im;
            if (im.hasDisplayName()) {
              im2.setDisplayName(im.getDisplayName());
            }
            if (im.hasEnchants()) {
              Map<Enchantment, Integer> enchants = im.getEnchants();
              for (Enchantment enchant : enchants.keySet()) {
                im2.addEnchant(enchant, enchants.get(enchant), true);
              }
            }
            if (iDmgable != null && iDmgable.hasDamage()) {
              ((Damageable) im2).setDamage(iDmgable.getDamage());
            }
            im2.setLore(null);
            i.setItemMeta(im2);
          }
          drops.set(drops.indexOf(i), i);
        }

        e.getItemDrop().remove();
        splitFx(p);
        for (ItemStack i : drops) {
          p.getWorld().dropItem(p.getLocation(), i);
        }
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void on(BlockDamageEvent e) {
    Player p = e.getPlayer();
    Block b = e.getBlock();
    ItemStack hand = p.getInventory().getItemInMainHand();

    if (validateTool(hand)) {
      if (!hasActiveAdaptation(p)) {
        return;
      }

      Damageable imHand = (Damageable) hand.getItemMeta();
      if (ItemListings.getAxePreference().contains(b.getType())) {
        if (!isAxe(hand)) {
          J.runEntity(p, () -> p.getInventory().setItemInMainHand(omniTool.nextAxe(hand)));
          itemDelegate(e, hand, imHand);
        }
      } else if (ItemListings.getShovelPreference().contains(b.getType())) {
        if (!isShovel(hand)) {
          J.runEntity(p, () -> p.getInventory().setItemInMainHand(omniTool.nextShovel(hand)));
          itemDelegate(e, hand, imHand);
        }
      } else if (ItemListings.getSwordPreference().contains(b.getType())) {
        if (!isSword(hand)) {
          J.runEntity(p, () -> p.getInventory().setItemInMainHand(omniTool.nextSword(hand)));
          itemDelegate(e, hand, imHand);
        }
      } else {
        if (!isPickaxe(hand)) {
          J.runEntity(p, () -> p.getInventory().setItemInMainHand(omniTool.nextPickaxe(hand)));
          itemDelegate(e, hand, imHand);
        }
      }
    }
  }


  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(InventoryClickEvent e) {
    if (!(e.getWhoClicked() instanceof Player player)) {
      return;
    }
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }
    if (e.getClickedInventory() != null && e.getClick().equals(ClickType.SHIFT_LEFT) && e.getAction().equals(InventoryAction.MOVE_TO_OTHER_INVENTORY)) {
      ItemStack cursor = e.getWhoClicked().getItemOnCursor().clone();
      ItemStack clicked = e.getClickedInventory().getItem(e.getSlot()).clone();

      int cursorComponents = omniTool.explode(cursor).size();
      int clickedComponents = omniTool.explode(clicked).size();
      if (!fitsCapacity(cursorComponents, clickedComponents, getSlots(level))) {
        e.setCancelled(true);
        fx(player, FxPriority.TRANSITION).sound(Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.77f);
        return;
      }
      if (ItemListings.tool.contains(cursor.getType()) && ItemListings.tool.contains(clicked.getType())) { // TOOLS ONLY
        if (!cursor.getType().isAir() && !clicked.getType().isAir() && omniTool.supportsItem(cursor) && omniTool.supportsItem(clicked)) {
          e.setCancelled(true);
          e.getWhoClicked().setItemOnCursor(new ItemStack(Material.AIR));
          e.getClickedInventory().setItem(e.getSlot(), omniTool.build(cursor, clicked));
          mergeFx(player);
        }
      }
    }

  }

  private void itemDelegate(BlockDamageEvent e, ItemStack hand, Damageable imHand) {
    Player p = e.getPlayer();
    addStat(p, "excavation.omni-tool.auto-swaps", 1);
    swapFx(p);
    if (imHand != null && imHand.hasDamage()) {
      if ((hand.getType().getMaxDurability() - imHand.getDamage() - 2) <= 2) {
        e.setCancelled(true);
        nearBreakFx(p);
      }
    }
  }

  private void swapFx(Player p) {
    fx(p, FxPriority.TRANSITION)
        .particle(Particles.ENCHANTMENT_TABLE, 6, 0, 1.0D, 0, 0.3D, 0.05D)
        .particle(Particles.CRIT_MAGIC, 3, 0, 1.0D, 0, 0.3D, 0.1D)
        .sound(Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
  }

  private void nearBreakFx(Player p) {
    fx(p, FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 3, 0, 1.0D, 0, 0.2D, 0.01D)
        .particle(Particles.CRIT_MAGIC, 1, 0, 1.0D, 0, 0.2D, 0.05D)
        .sound(Sound.ENTITY_IRON_GOLEM_STEP, 0.25f, 0.77f);
  }

  private void splitFx(Player p) {
    fx(p, FxPriority.TRANSITION)
        .particle(Particles.CRIT_MAGIC, 8, 0, 1.0D, 0, 0.4D, 0.1D)
        .chord(Sound.ENTITY_IRON_GOLEM_DEATH, 0.25f, 0.77f, Sound.ITEM_TRIDENT_RETURN, 0.3f, 1.2f);
  }

  private void mergeFx(Player p) {
    fx(p, FxPriority.TRANSITION)
        .particle(Particle.WAX_ON, 10, 0, 1.0D, 0, 0.4D, 0.05D)
        .particle(Particle.ELECTRIC_SPARK, 5, 0, 1.0D, 0, 0.4D, 0.1D)
        .chord(Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f, Sound.BLOCK_ANVIL_USE, 0.4f, 1.4f);
  }

  private boolean validateTool(ItemStack item) {
    return (item.getItemMeta() != null
        && item.getItemMeta().getLore() != null
        && item.getItemMeta().getLore().toString().contains("Leatherman"));
  }

  static boolean fitsCapacity(int firstComponents, int secondComponents, int capacity) {
    return (long) firstComponents + secondComponents <= capacity;
  }

  private int getSlots(int level) {
    return getConfig().startingSlots + level;
  }

  @ConfigDescription("Dynamically merge and swap tools on the fly based on what you are mining.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Starting Slots for the Excavation Omni Tool adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int startingSlots = 1;

    public Config() {
      baseCost = 10;
      costFactor = 0.20;
      initialCost = 3;
    }
  }
}
