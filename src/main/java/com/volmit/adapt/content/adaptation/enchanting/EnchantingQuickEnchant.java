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

package com.volmit.adapt.content.adaptation.enchanting;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.SoundPlayer;
import com.volmit.adapt.util.collection.KMap;
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
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class EnchantingQuickEnchant extends SimpleAdaptation<EnchantingQuickEnchant.Config> {
    private final List<Integer> holds = new ArrayList<>();

    public EnchantingQuickEnchant() {
        super("enchanting-quick-enchant");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("enchanting.quick_enchant.description"));
        setDisplayName(Localizer.dLocalize("enchanting.quick_enchant.name"));
        setIcon(Material.WRITABLE_BOOK);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(15100);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    private int getTotalLevelCount(int level) {
        return level + (level > getConfig().maxPowerBonusLimit ? level / getConfig().maxPowerBonus1PerLevels : 0);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(Localizer.dLocalize("enchanting.quick_enchant.lore", getTotalLevelCount(level)));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(InventoryClickEvent e) {
        if (e.getClickedInventory() == null || e.isCancelled()) {
            return;
        }
        if (e.getWhoClicked() instanceof Player p
                && hasAdaptation(p)
                && e.getAction().equals(InventoryAction.SWAP_WITH_CURSOR)
                && e.getClick().equals(ClickType.LEFT)
                && (e.getSlotType().equals(InventoryType.SlotType.CONTAINER)
                || e.getSlotType().equals(InventoryType.SlotType.ARMOR)
                || e.getSlotType().equals(InventoryType.SlotType.QUICKBAR))
                && e.getCursor() != null
                && e.getCurrentItem() != null
                && e.getCursor().getType().equals(Material.ENCHANTED_BOOK)
                && !e.getCurrentItem().getType().equals(Material.BOOK)
                && !e.getCurrentItem().getType().equals(Material.ENCHANTED_BOOK)
                && e.getCursor().getItemMeta() != null
                && e.getCursor().getItemMeta() instanceof EnchantmentStorageMeta eb
                && e.getCurrentItem().getItemMeta() != null
                && e.getCurrentItem().getAmount() == 1
                && e.getCursor().getAmount() == 1) {
            ItemStack item = e.getCurrentItem();
            ItemStack book = e.getCursor();
            KMap<Enchantment, Integer> itemEnchants = new KMap<>(item.getType().equals(Material.ENCHANTED_BOOK)
                    ? ((EnchantmentStorageMeta) item.getItemMeta()).getStoredEnchants()
                    : item.getEnchantments());
            KMap<Enchantment, Integer> bookEnchants = new KMap<>(eb.getStoredEnchants());
            KMap<Enchantment, Integer> newEnchants = itemEnchants.copy();
            KMap<Enchantment, Integer> addEnchants = new KMap<>();
            int power = itemEnchants.values().stream().mapToInt(i -> i).sum();

            if (bookEnchants.isEmpty()) {
                return;
            }

            for (Enchantment i : bookEnchants.k()) {
                if (itemEnchants.containsKey(i)) {
                    continue;
                }

                power += bookEnchants.get(i);
                newEnchants.put(i, bookEnchants.get(i));
                addEnchants.put(i, bookEnchants.get(i));
                bookEnchants.remove(i);
            }

            SoundPlayer sp = SoundPlayer.of(p);
            if (power > getTotalLevelCount(getLevel(p))) {
                Adapt.actionbar(p, Localizer.dLocalize("enchanting.quick_enchant.max_power", getTotalLevelCount(getLevel(p))));
                sp.play(p.getLocation(), Sound.BLOCK_CONDUIT_DEACTIVATE, 0.5f, 1.7f);
                return;
            }

            if (!itemEnchants.equals(newEnchants)) {
                ItemMeta im = item.getItemMeta();

                if (im instanceof EnchantmentStorageMeta sm) {
                    sm.getStoredEnchants().keySet().forEach(sm::removeStoredEnchant);
                    newEnchants.forEach((ec, l) -> sm.addStoredEnchant(ec, l, true));
                    Adapt.messagePlayer(p, "---");
                    sm.getStoredEnchants().forEach((k, v) -> Adapt.messagePlayer(p, k.getKey().getKey() + " " + v));
                } else {
                    im.getEnchants().keySet().forEach(im::removeEnchant);
                    newEnchants.forEach((ec, l) -> im.addEnchant(ec, l, true));
                }

                xp(p, 50);
                item.setItemMeta(im);
                e.setCurrentItem(item);
                e.setCancelled(true);
                sp.play(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.7f);
                sp.play(p.getLocation(), Sound.BLOCK_DEEPSLATE_TILES_BREAK, 0.5f, 0.7f);
                getSkill().xp(p, 320 * addEnchants.values().stream().mapToInt((i) -> i).sum());

                if (bookEnchants.isEmpty()) {
                    e.setCursor(null);
                } else if (!eb.getStoredEnchants().equals(bookEnchants)) {
                    eb.getStoredEnchants().keySet().forEach(eb::removeStoredEnchant);
                    bookEnchants.forEach((ec, l) -> eb.addStoredEnchant(ec, l, true));
                    book.setItemMeta(eb);
                    e.setCursor(book);
                }
            }
        }
    }

    @Override
    public void onTick() {

    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean permanent = false;
        boolean enabled = true;
        int baseCost = 6;
        int maxLevel = 7;
        int initialCost = 8;
        double costFactor = 1.355;
        int maxPowerBonusLimit = 4;
        int maxPowerBonus1PerLevels = 3;
    }
}
