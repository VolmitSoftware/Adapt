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

package com.volmit.adapt.content.adaptation.hunter;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.content.item.ItemListings;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.SoundPlayer;
import com.volmit.adapt.util.collection.KList;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.List;

public class HunterDropToInventory extends SimpleAdaptation<HunterDropToInventory.Config> {
    public HunterDropToInventory() {
        super("hunter-drop-to-inventory");
        registerConfiguration(HunterDropToInventory.Config.class);
        setDescription(Localizer.dLocalize("hunter.drop_to_inventory.description"));
        setDisplayName(Localizer.dLocalize("hunter.drop_to_inventory.name"));
        setIcon(Material.DIRT);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(18440);

    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + Localizer.dLocalize("hunter.drop_to_inventory.lore1"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(BlockDropItemEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        SoundPlayer sp = SoundPlayer.of(p);
        if (!hasAdaptation(p)) {
            return;
        }
        if (p.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        if (!canInteract(p, e.getBlock().getLocation())) {
            return;
        }
        if (!canPVP(p, e.getBlock().getLocation())) {
            return;
        }
        if (ItemListings.toolSwords.contains(p.getInventory().getItemInMainHand().getType())) {
            List<Item> items = new KList<>(e.getItems());
            e.getItems().clear();
            sp.play(p.getLocation(), Sound.BLOCK_CALCITE_HIT, 0.05f, 0.01f);
            for (Item i : items) {
                if (!p.getInventory().addItem(i.getItemStack()).isEmpty()) {
                    p.getWorld().dropItem(p.getLocation(), i.getItemStack());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDeathEvent e) {
        LivingEntity k = e.getEntity();
        if (k.getKiller() == null || k.getKiller().getType() != EntityType.PLAYER) {
            return;
        }
        Player p = k.getKiller();
        if (!hasAdaptation(p)) {
            return;
        }
        if (e.getEntity() instanceof Player) {
            return;
        }
        if (e.getEntity().getKiller() != null && e.getEntity().getKiller().getClass().getSimpleName().equals("CraftPlayer")) {
            SoundPlayer sp = SoundPlayer.of(p);
            sp.play(p.getLocation(), Sound.BLOCK_CALCITE_HIT, 0.05f, 0.01f);
            e.getDrops().forEach(i -> {
                if (!p.getInventory().addItem(i).isEmpty()) {
                    p.getWorld().dropItem(p.getLocation(), i);
                }
            });
            e.getDrops().clear();
        }
    }


    @Override
    public void onTick() {
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    @ConfigDescription("Mob and block drops teleport directly into your inventory.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 1;
    }
}
