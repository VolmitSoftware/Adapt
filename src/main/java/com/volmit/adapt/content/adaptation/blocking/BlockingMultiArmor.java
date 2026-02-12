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

package com.volmit.adapt.content.adaptation.blocking;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.item.ItemListings;
import com.volmit.adapt.content.item.multiItems.MultiArmor;
import com.volmit.adapt.util.*;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class BlockingMultiArmor extends SimpleAdaptation<BlockingMultiArmor.Config> {
    private static final MultiArmor multiarmor = new MultiArmor();
    private final Map<Player, Long> cooldowns;


    public BlockingMultiArmor() {
        super("blocking-multiarmor");
        registerConfiguration(BlockingMultiArmor.Config.class);
        setDisplayName(Localizer.dLocalize("blocking.multi_armor.name"));
        setDescription(Localizer.dLocalize("blocking.multi_armor.description"));
        setIcon(Material.ELYTRA);
        setInterval(20202);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        cooldowns = new HashMap<>();
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.ELYTRA)
                .key("challenge_blocking_multi_200")
                .title(Localizer.dLocalize("advancement.challenge_blocking_multi_200.title"))
                .description(Localizer.dLocalize("advancement.challenge_blocking_multi_200.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.NETHERITE_CHESTPLATE)
                        .key("challenge_blocking_multi_5k")
                        .title(Localizer.dLocalize("advancement.challenge_blocking_multi_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_blocking_multi_5k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder()
                .advancement("challenge_blocking_multi_200")
                .goal(200)
                .stat("blocking.multi-armor.swaps")
                .reward(400)
                .build());
        registerStatTracker(AdaptStatTracker.builder()
                .advancement("challenge_blocking_multi_5k")
                .goal(5000)
                .stat("blocking.multi-armor.swaps")
                .reward(1500)
                .build());
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

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public void onTick() {
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void on(PlayerMoveEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        ItemStack chest = p.getInventory().getChestplate();
        if (chest != null && hasAdaptation(p) && validateArmor(chest)) {
            Long cooldown = cooldowns.get(p);
            if (cooldown != null) {
                if (cooldown + 3000 > System.currentTimeMillis())
                    return;
                else cooldowns.remove(p);
            }

            SoundPlayer spw = SoundPlayer.of(p.getWorld());
            if (p.isOnGround() && !p.isFlying()) {
                if (isChestplate(chest)) {
                    return;
                }
                J.s(() -> p.getInventory().setChestplate(multiarmor.nextChestplate(chest)));
                cooldowns.put(p, System.currentTimeMillis());
                spw.play(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
                spw.play(p.getLocation(), Sound.BLOCK_BEEHIVE_SHEAR, 0.5f, 0.77f);
                getPlayer(p).getData().addStat("blocking.multi-armor.swaps", 1);

            } else if (p.getFallDistance() > 4) {
                if (isElytra(chest)) {
                    return;
                }
                J.s(() -> p.getInventory().setChestplate(multiarmor.nextElytra(chest)));
                cooldowns.put(p, System.currentTimeMillis());
                spw.play(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
                spw.play(p.getLocation(), Sound.ENTITY_IRON_GOLEM_STEP, 0.5f, 0.77f);
                getPlayer(p).getData().addStat("blocking.multi-armor.swaps", 1);
            }
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        SoundPlayer sp = SoundPlayer.of(p);
        if (!hasAdaptation(p)) {
            return;
        }
        if (p.isSneaking()) {
            if (validateArmor(e.getItemDrop().getItemStack())) {
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

                J.s(() -> {
                    sp.play(p.getLocation(), Sound.ENTITY_IRON_GOLEM_DEATH, 0.25f, 0.77f);
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
        if (!hasAdaptation((Player) e.getWhoClicked())) {
            return;
        }
        if (e.getClickedInventory() != null
                && e.getClick().equals(ClickType.SHIFT_LEFT)
                && e.getClickedInventory().getItem(e.getSlot()) != null
                && e.getAction().equals(InventoryAction.MOVE_TO_OTHER_INVENTORY)) {
            ItemStack cursor = e.getWhoClicked().getItemOnCursor().clone();
            ItemStack clicked = e.getClickedInventory().getItem(e.getSlot()).clone();

            if (cursor.getType().equals(Material.ELYTRA) || clicked.getType().equals(Material.ELYTRA)) { // One must be an ELYTRA

                if (multiarmor.explode(cursor).size() > 1 || multiarmor.explode(clicked).size() > 1) {

                    if (multiarmor.explode(cursor).size() >= getSlots(getLevel((Player) e.getWhoClicked())) || multiarmor.explode(clicked).size() >= getSlots(getLevel((Player) e.getWhoClicked()))) {
                        e.setCancelled(true);
                        SoundPlayer sp = SoundPlayer.of((Player) e.getWhoClicked());
                        sp.play(e.getWhoClicked().getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.77f);
                        return;
                    }
                }
                if (ItemListings.getMultiArmorable().contains(cursor.getType()) && ItemListings.getMultiArmorable().contains(clicked.getType())) { // Chest/Elytra Only

                    if (!cursor.getType().isAir() && !clicked.getType().isAir() && multiarmor.supportsItem(cursor) && multiarmor.supportsItem(clicked)) {
                        e.setCancelled(true);
                        e.getWhoClicked().setItemOnCursor(new ItemStack(Material.AIR));
                        e.getClickedInventory().setItem(e.getSlot(), multiarmor.build(cursor, clicked));
                        SoundPlayer spw = SoundPlayer.of(e.getWhoClicked().getWorld());
                        spw.play(e.getWhoClicked().getLocation(), Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
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

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    @ConfigDescription("Bind Elytras to armor for dynamic merge and swap.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Starting Slots for the Blocking Multi Armor adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int startingSlots = 1;
    }
}
