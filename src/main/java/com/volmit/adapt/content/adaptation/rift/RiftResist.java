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

package com.volmit.adapt.content.adaptation.rift;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.SoundPlayer;
import com.volmit.adapt.util.reflect.enums.PotionEffectTypes;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;


public class RiftResist extends SimpleAdaptation<RiftResist.Config> {
    public RiftResist() {
        super("rift-resist");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("rift", "resist", "description"));
        setDisplayName(Localizer.dLocalize("rift", "resist", "name"));
        setIcon(Material.SCULK_VEIN);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(10288);
    }

    static void riftResistStackAdd(Player p, int duration, int amplifier) {
        if (p.getLocation().getWorld() == null) {
            return;
        }
        SoundPlayer spw = SoundPlayer.of(p.getWorld());
        spw.play(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 1.24f);
        spw.play(p.getLocation(), Sound.BLOCK_CONDUIT_AMBIENT_SHORT, 1f, 0.01f);
        spw.play(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 0.01f);
        p.addPotionEffect(new PotionEffect(PotionEffectTypes.DAMAGE_RESISTANCE, duration, amplifier, true, false, false));
    }

    public static boolean hasRiftResistPerk(AdaptPlayer p) {
        return p.getData().getLevel() > 0;
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.ITALIC + Localizer.dLocalize("rift", "resist", "lore1"));
        v.addLore(C.UNDERLINE + Localizer.dLocalize("rift", "resist", "lore2"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!hasAdaptation(p)) {
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();

        if (e.getAction() == Action.RIGHT_CLICK_AIR) {
            switch (hand.getType()) {
                case ENDER_EYE, ENDER_PEARL -> {
                    xp(p, 3);
                    riftResistStackAdd(p, getConfig().duration, getConfig().amplitude);
                }
            }
        }

    }

    @Override
    public void onTick() {

    }

    @Override
    public boolean isEnabled() {
        return !getConfig().enabled;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    protected static class Config {
        final boolean permanent = false;
        final boolean enabled = true;
        final int baseCost = 3;
        final double costFactor = 1;
        final int maxLevel = 1;
        final int amplitude = 1;
        final int duration = 80;
        final int initialCost = 5;
    }
}
