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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.item.ItemListings;
import art.arcane.adapt.content.item.multiItems.MultiArmor;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public class BlockingMultiArmor extends SimpleAdaptation<BlockingMultiArmor.Config> {
  private static final MultiArmor multiarmor = new MultiArmor();
  private static final long SWAP_COOLDOWN_MILLIS = 3000L;
  private final Cooldowns swapCooldowns = cooldowns();


  public BlockingMultiArmor() {
    super("blocking-multiarmor");
    registerConfiguration(BlockingMultiArmor.Config.class);
    setLocalizationKey("blocking.multi_armor");
    setIcon(Material.ELYTRA);
    setInterval(20202);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ELYTRA)
        .key("challenge_blocking_multi_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHERITE_CHESTPLATE)
            .key("challenge_blocking_multi_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_blocking_multi_200", "blocking.multi-armor.swaps", 200, 400);
    registerMilestone("challenge_blocking_multi_5k", "blocking.multi-armor.swaps", 5000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GRAY + Localizer.dLocalize("blocking.multi_armor.lore1"));
    v.addLore(C.GRAY + "" + C.GRAY + Localizer.dLocalize("blocking.multi_armor.lore2"));
    v.addLore(C.GREEN + Localizer.dLocalize("blocking.multi_armor.lore3"));
    v.addLore(C.RED + Localizer.dLocalize("blocking.multi_armor.lore4"));
    v.addLore(C.GRAY + Localizer.dLocalize("blocking.multi_armor.lore5"));
    v.addLore(C.UNDERLINE + Localizer.dLocalize("blocking.multi_armor.lore6"));
  }


  @EventHandler(priority = EventPriority.HIGH)
  public void on(PlayerMoveEvent e) {
    if (!e.hasChangedBlock()) {
      return;
    }

    Player p = e.getPlayer();
    ItemStack chest = p.getInventory().getChestplate();
    if (chest == null || !hasActiveAdaptation(p) || !validateArmor(chest)) {
      return;
    }

    if (!swapCooldowns.isReady(p.getUniqueId(), SWAP_COOLDOWN_MILLIS)) {
      return;
    }

    if (p.isOnGround() && !p.isFlying()) {
      if (isChestplate(chest)) {
        return;
      }
      J.runEntity(p, () -> p.getInventory().setChestplate(multiarmor.nextChestplate(chest)));
      swapCooldowns.mark(p.getUniqueId());
      transformFx(p, false);
      addStat(p, "blocking.multi-armor.swaps", 1);
    } else if (p.getFallDistance() > 4) {
      if (isElytra(chest)) {
        return;
      }
      J.runEntity(p, () -> p.getInventory().setChestplate(multiarmor.nextElytra(chest)));
      swapCooldowns.mark(p.getUniqueId());
      transformFx(p, true);
      addStat(p, "blocking.multi-armor.swaps", 1);
    }
  }

  private void transformFx(Player p, boolean toElytra) {
    if (toElytra) {
      fx(p, FxPriority.TRANSITION)
          .column(Particle.WAX_ON, 10, 1.8D)
          .burst(Particles.END_ROD, 3, 0.2D)
          .chord(Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1.0F, 0.77F, Sound.ENTITY_IRON_GOLEM_STEP, 0.5F, 0.77F, Sound.BLOCK_CONDUIT_ACTIVATE, 0.4F, 1.4F);
      return;
    }
    fx(p, FxPriority.TRANSITION)
        .column(Particle.WAX_ON, 10, 1.8D)
        .burst(Particles.END_ROD, 3, 0.2D)
        .chord(Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1.0F, 0.77F, Sound.BLOCK_BEEHIVE_SHEAR, 0.5F, 0.77F, Sound.BLOCK_CONDUIT_DEACTIVATE, 0.4F, 0.9F);
  }


  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerDropItemEvent e) {
    Player p = e.getPlayer();
    if (!hasActiveAdaptation(p)) {
      return;
    }
    if (p.isSneaking()) {
      if (validateArmor(e.getItemDrop().getItemStack())) {
        ItemStack source = e.getItemDrop().getItemStack();
        List<ItemStack> drops = multiarmor.explode(e.getItemDrop().getItemStack());
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

        J.runEntity(p, () -> {
          fx(p, FxPriority.TRANSITION)
              .particle(Particles.ITEM_CRACK, 12, 0, 0.2D, 0, 0.3D, 0.05D, source)
              .burst(Particles.CRIT_MAGIC, 4, 0.3D)
              .chord(Sound.ENTITY_IRON_GOLEM_DEATH, 0.25F, 0.77F, Sound.BLOCK_ANVIL_DESTROY, 0.3F, 1.1F);
          for (ItemStack i : drops) {
            p.getWorld().dropItem(p.getLocation(), i);
          }
        });
        e.getItemDrop().setItemStack(new ItemStack(Material.AIR));
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
    if (e.getClickedInventory() != null
        && e.getClick().equals(ClickType.LEFT)
        && e.getClickedInventory().getItem(e.getSlot()) != null
        && e.getAction().equals(InventoryAction.SWAP_WITH_CURSOR)) {
      ItemStack cursor = player.getItemOnCursor().clone();
      ItemStack clicked = e.getClickedInventory().getItem(e.getSlot()).clone();

      if (cursor.getType().equals(Material.ELYTRA) || clicked.getType().equals(Material.ELYTRA)) { // One must be an ELYTRA

        if (multiarmor.explode(cursor).size() > 1 || multiarmor.explode(clicked).size() > 1) {

          if (multiarmor.explode(cursor).size() >= getSlots(level) || multiarmor.explode(clicked).size() >= getSlots(level)) {
            e.setCancelled(true);
            fx(player, FxPriority.TRANSITION)
                .particle(Particles.SMOKE, 3, 0, 1.0D, 0, 0.1D, 0.01D)
                .sound(Sound.BLOCK_BEACON_DEACTIVATE, 1.0F, 0.77F);
            return;
          }
        }
        if (ItemListings.getMultiArmorable().contains(cursor.getType()) && ItemListings.getMultiArmorable().contains(clicked.getType())) { // Chest/Elytra Only

          if (!cursor.getType().isAir() && !clicked.getType().isAir() && multiarmor.supportsItem(cursor) && multiarmor.supportsItem(clicked)) {
            e.setCancelled(true);
            player.setItemOnCursor(new ItemStack(Material.AIR));
            e.getClickedInventory().setItem(e.getSlot(), multiarmor.build(cursor, clicked));
            fx(player, FxPriority.TRANSITION)
                .burst(Particles.END_ROD, 8, 0.4D)
                .chord(Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1.0F, 0.77F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.4F);
          }
        }
      }
    }
  }


  private boolean validateArmor(ItemStack item) {
    if (item.getItemMeta() != null && item.getItemMeta().getLore() != null) {
      for (String lore : item.getItemMeta().getLore()) {
        if (lore != null && lore.contains("MultiArmor")) {
          return true;
        }
      }
    }
    return false;
  }


  private double getSlots(double level) {
    return getConfig().startingSlots + level;
  }

  @ConfigDescription("Bind Elytras to armor for dynamic merge and swap.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Starting Slots for the Blocking Multi Armor adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int startingSlots = 1;

    public Config() {
      baseCost = 1;
      costFactor = 1;
      maxLevel = 1;
      initialCost = 3;
    }
  }
}
