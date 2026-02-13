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

package com.volmit.adapt.content.adaptation.stealth;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.*;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class StealthSnatch extends SimpleAdaptation<StealthSnatch.Config> {
    private final Set<Integer> holds;

    public StealthSnatch() {
        super("stealth-snatch");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("stealth.snatch.description"));
        setDisplayName(Localizer.dLocalize("stealth.snatch.name"));
        setIcon(Material.CHEST_MINECART);
        setBaseCost(getConfig().baseCost);
        setInterval(getConfig().snatchRate);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        holds = new HashSet<>();
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.CHEST)
                .key("challenge_stealth_snatch_2500")
                .title(Localizer.dLocalize("advancement.challenge_stealth_snatch_2500.title"))
                .description(Localizer.dLocalize("advancement.challenge_stealth_snatch_2500.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.HOPPER)
                        .key("challenge_stealth_snatch_25k")
                        .title(Localizer.dLocalize("advancement.challenge_stealth_snatch_25k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_stealth_snatch_25k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_stealth_snatch_2500", "stealth.snatch.items-snatched", 2500, 400);
        registerMilestone("challenge_stealth_snatch_25k", "stealth.snatch.items-snatched", 25000, 1500);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getRange(getLevelPercent(level)), 1) + C.GRAY + " " + Localizer.dLocalize("stealth.snatch.lore1"));
    }

    @EventHandler
    public void on(PlayerToggleSneakEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        if (!hasAdaptation(p)) {
            return;
        }
        if (!canAccessChest(p, p.getLocation())) {
            return;
        }
        if (e.isSneaking()) {
            snatch(p);
        }
    }

    private void snatch(Player player) {
        double factor = getLevelPercent(player);

        if (factor == 0) {
            return;
        }

        double range = getRange(factor);
        HashSet<Item> items = new HashSet<>();
        for (Entity droppedItemEntity : player.getWorld().getNearbyEntities(player.getLocation(), range, range / 1.5, range)) {
            if (droppedItemEntity instanceof Item droppedItem) {
                if (droppedItem.getPickupDelay() <= 0 || droppedItem.getTicksLived() > 1) {
                    UUID owner = droppedItem.getOwner();
                    if (owner == null || owner.equals(player.getUniqueId())) items.add(droppedItem);
                }
            }
        }

        for (Item droppedItemEntity : items) {
            if (!holds.contains(droppedItemEntity.getEntityId())) {
                double dist = droppedItemEntity.getLocation().distanceSquared(player.getLocation());
                if (dist < range * range) {
                    ItemStack is = droppedItemEntity.getItemStack().clone();

                        if (Inventories.hasSpace(player.getInventory(), is)) {
                            holds.add(droppedItemEntity.getEntityId());
                            SoundPlayer spw = SoundPlayer.of(player.getWorld());
                            spw.play(player.getLocation(), Sound.BLOCK_LAVA_POP, 1f, (float) (1.0 + (ThreadLocalRandom.current().nextDouble() / 3D)));
                            safeGiveItem(player, droppedItemEntity, is);
                        getPlayer(player).getData().addStat("stealth.snatch.items-snatched", 1);
                        //sendCollected(player, droppedItemEntity);
                        int id = droppedItemEntity.getEntityId();
                        J.s(() -> holds.remove(Integer.valueOf(id)));
                    }
                }
            }
        }

    }

    private double getRange(double factor) {
        return (factor * getConfig().radiusFactor) + 1;
    }

    /*
    public void sendCollected(Player p, Item item) {
        try {
            PacketPlayOutCollect packet = new PacketPlayOutCollect(item.getEntityId(), p.getEntityId(), item.getItemStack().getAmount());
            for (Entity i : p.getWorld().getNearbyEntities(p.getLocation(), 8, 8, 8, entity -> entity instanceof Player)) {
                ((CraftPlayer) i).getHandle().c.a(packet);
            }
        } catch (Exception e) {
            Adapt.error("Failed to send collected packet");
            e.printStackTrace();
        }
    }*/

    @Override
    public void onTick() {
        for (com.volmit.adapt.api.world.AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
            Player i = adaptPlayer.getPlayer();
            if (i.isSneaking()) {
                J.s(() -> snatch(i));
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    protected void onConfigReload(Config previousConfig, Config newConfig) {
        super.onConfigReload(previousConfig, newConfig);
        setInterval(newConfig.snatchRate);
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    @ConfigDescription("Snatch dropped items instantly while sneaking.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Snatch Rate for the Stealth Snatch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int snatchRate = 250;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 12;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.125;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Stealth Snatch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double radiusFactor = 5.55;
    }
}
