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
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.SoundPlayer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class RiftInflatedPocketDimension extends SimpleAdaptation<RiftInflatedPocketDimension.Config> {
    public RiftInflatedPocketDimension() {
        super("rift-inflated-pocket-dimension");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("rift.inflated_pocket_dimension.description"));
        setDisplayName(Localizer.dLocalize("rift.inflated_pocket_dimension.name"));
        setIcon(Material.ENDER_EYE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(600);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.ENDER_CHEST)
                .key("challenge_rift_pocket_5k")
                .title(Localizer.dLocalize("advancement.challenge_rift_pocket_5k.title"))
                .description(Localizer.dLocalize("advancement.challenge_rift_pocket_5k.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.ENDER_CHEST)
                        .key("challenge_rift_pocket_store_10k")
                        .title(Localizer.dLocalize("advancement.challenge_rift_pocket_store_10k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_rift_pocket_store_10k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_rift_pocket_5k", "rift.inflated-pocket.items-pulled", 5000, 400);
        registerMilestone("challenge_rift_pocket_store_10k", "rift.inflated-pocket.items-stored", 10000, 1000);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Localizer.dLocalize("rift.inflated_pocket_dimension.lore1"));
        v.addLore(C.GREEN + "+ " + Localizer.dLocalize("rift.inflated_pocket_dimension.lore2"));
        v.addLore(C.GREEN + "+ " + Localizer.dLocalize("rift.inflated_pocket_dimension.lore3"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) {
            return;
        }

        Player p = e.getPlayer();
        if (!hasAdaptation(p)) {
            return;
        }

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!hand.getType().isAir()) {
            return;
        }

        Material requested = e.getClickedBlock().getType();
        if (!requested.isItem() || requested.isAir()) {
            return;
        }

        int moved = moveFromEnderToPlayer(p, requested, getConfig().rightClickPullAmount, true);
        if (moved <= 0) {
            return;
        }

        e.setCancelled(true);
        SoundPlayer.of(p).play(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.4f, 1.7f);
        getPlayer(p).getData().addStat("rift.inflated-pocket.items-pulled", moved);
        xp(p, moved * getConfig().xpPerTransferredItem, "rift:inflated-pocket:pull");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (!hasAdaptation(p)) {
            return;
        }

        Material placed = e.getBlockPlaced().getType();
        if (!placed.isItem() || placed.isAir()) {
            return;
        }

        J.s(() -> {
            ItemStack hand = p.getInventory().getItemInMainHand();
            int needed = 0;
            if (hand.getType().isAir()) {
                needed = Math.min(getConfig().buildRefillAmount, placed.getMaxStackSize());
            } else if (hand.getType() == placed && hand.getAmount() < placed.getMaxStackSize()) {
                needed = Math.min(getConfig().buildRefillAmount, placed.getMaxStackSize() - hand.getAmount());
            }

            if (needed <= 0) {
                return;
            }

            int moved = moveFromEnderToPlayer(p, placed, needed, true);
            if (moved <= 0) {
                return;
            }

            SoundPlayer.of(p).play(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.3f, 1.9f);
            getPlayer(p).getData().addStat("rift.inflated-pocket.items-pulled", moved);
            xp(p, moved * getConfig().xpPerTransferredItem, "rift:inflated-pocket:build-refill");
        }, 1);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (!hasAdaptation(p) || !p.isSneaking()) {
            return;
        }

        ItemStack dropped = e.getItemDrop().getItemStack().clone();
        if (!canFullyFitInInventory(p.getEnderChest().getContents(), dropped, p.getEnderChest().getMaxStackSize())) {
            e.setCancelled(true);
            e.getItemDrop().remove();
            SoundPlayer.of(p).play(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
            return;
        }

        e.getItemDrop().remove();
        p.getEnderChest().addItem(dropped);

        SoundPlayer.of(p).play(p.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 0.5f, 1.4f);
        getPlayer(p).getData().addStat("rift.inflated-pocket.items-stored", dropped.getAmount());
        xp(p, dropped.getAmount() * getConfig().xpPerTransferredItem, "rift:inflated-pocket:store");
    }

    private int moveFromEnderToPlayer(Player p, Material type, int amount, boolean preferMainHand) {
        if (amount <= 0) {
            return 0;
        }

        int moved = 0;
        ItemStack[] chest = p.getEnderChest().getContents();
        for (int slot = 0; slot < chest.length && moved < amount; slot++) {
            ItemStack stack = chest[slot];
            if (!isItem(stack) || stack.getType() != type) {
                continue;
            }

            int take = Math.min(amount - moved, stack.getAmount());
            ItemStack transfer = stack.clone();
            transfer.setAmount(take);

            int inserted = insertIntoPlayerInventory(p, transfer, preferMainHand);
            if (inserted <= 0) {
                continue;
            }

            moved += inserted;
            if (stack.getAmount() <= inserted) {
                chest[slot] = null;
            } else {
                stack.setAmount(stack.getAmount() - inserted);
                chest[slot] = stack;
            }
        }

        p.getEnderChest().setContents(chest);
        return moved;
    }

    private int insertIntoPlayerInventory(Player p, ItemStack transfer, boolean preferMainHand) {
        int requested = transfer.getAmount();
        if (requested <= 0) {
            return 0;
        }

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (preferMainHand && (hand == null || hand.getType().isAir())) {
            p.getInventory().setItemInMainHand(transfer);
            return requested;
        }

        if (preferMainHand && hand.getType() == transfer.getType() && hand.getAmount() < hand.getMaxStackSize()) {
            int room = hand.getMaxStackSize() - hand.getAmount();
            int moved = Math.min(room, transfer.getAmount());
            hand.setAmount(hand.getAmount() + moved);
            p.getInventory().setItemInMainHand(hand);
            if (moved >= requested) {
                return requested;
            }

            transfer.setAmount(requested - moved);
            Map<Integer, ItemStack> overflow = p.getInventory().addItem(transfer);
            int remaining = overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
            return moved + Math.max(0, transfer.getAmount() - remaining);
        }

        Map<Integer, ItemStack> overflow = p.getInventory().addItem(transfer);
        int remaining = overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
        return Math.max(0, requested - remaining);
    }

    private boolean canFullyFitInInventory(ItemStack[] contents, ItemStack stack, int inventoryMaxStackSize) {
        if (!isItem(stack)) {
            return false;
        }

        int remaining = stack.getAmount();
        int maxPerSlot = Math.min(stack.getMaxStackSize(), inventoryMaxStackSize);

        for (ItemStack existing : contents) {
            if (!isItem(existing) || !existing.isSimilar(stack)) {
                continue;
            }

            remaining -= Math.max(0, maxPerSlot - existing.getAmount());
            if (remaining <= 0) {
                return true;
            }
        }

        for (ItemStack existing : contents) {
            if (existing == null || existing.getType().isAir()) {
                remaining -= maxPerSlot;
                if (remaining <= 0) {
                    return true;
                }
            }
        }

        return false;
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
    @ConfigDescription("Building and empty-hand block targeting can fetch materials from your ender chest, and sneak-drop stores items into it.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Build Refill Amount for the Rift Inflated Pocket Dimension adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int buildRefillAmount = 64;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Right Click Pull Amount for the Rift Inflated Pocket Dimension adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int rightClickPullAmount = 64;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls XP Per Transferred Item for the Rift Inflated Pocket Dimension adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerTransferredItem = 0.08;
    }
}
