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

package com.volmit.adapt.content.adaptation.architect;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.content.item.BoundRedstoneTorch;
import com.volmit.adapt.util.*;
import lombok.NoArgsConstructor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.AnaloguePowerable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

import static com.volmit.adapt.api.adaptation.chunk.ChunkLoading.loadChunkAsync;


public class ArchitectWirelessRedstone extends SimpleAdaptation<ArchitectWirelessRedstone.Config> {
    private final Map<Player, Long> cooldowns;

    public ArchitectWirelessRedstone() {
        super("architect-wireless-redstone");
        registerConfiguration(ArchitectWirelessRedstone.Config.class);
        setDescription(Localizer.dLocalize("architect", "wirelessredstone", "description"));
        setDisplayName(Localizer.dLocalize("architect", "wirelessredstone", "name"));
        setIcon(Material.REDSTONE_TORCH);
        setInterval(100);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        registerRecipe(AdaptRecipe.shapeless()
                .key("remote-redstone-torch")
                .ingredient(Material.REDSTONE_TORCH)
                .ingredient(Material.TARGET)
                .ingredient(Material.ENDER_PEARL)
                .result(BoundRedstoneTorch.io.withData(new BoundRedstoneTorch.Data(null)))
                .build());
        cooldowns = new HashMap<>();
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Localizer.dLocalize("architect", "wirelessredstone", "lore1"));
    }

    @EventHandler
    public void on(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();

        if(!hand.getType().equals(Material.REDSTONE_TORCH)) {
           return;
        }

        if (isBound(hand)) {
            p.setCooldown(Material.REDSTONE_TORCH, 50000);
        } else {
            p.setCooldown(Material.REDSTONE_TORCH, 0);
        }
    }

    @EventHandler
    public void on(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if(!hand.getType().equals(Material.REDSTONE_TORCH)) {
            return;
        }

        if (!hasAdaptation(p)) {
            return;
        }

        ItemStack offhand = p.getInventory().getItemInOffHand();
        if (e.getHand() != null && e.getHand().equals(EquipmentSlot.OFF_HAND) && BoundRedstoneTorch.isBindableItem(offhand)) {
            e.setCancelled(true);
            return;
        }

        if (BoundRedstoneTorch.isBindableItem(hand)) {
            if (!hasAdaptation(p)) {
                return;
            }
            Adapt.verbose("Player " + p.getName() + " is holding a bound redstone torch");
            switch (e.getAction()) {
                case LEFT_CLICK_BLOCK -> {
                    Adapt.verbose("Player " + p.getName() + " is left clicking a block");
                    if (p.isSneaking()) {
                        Location location;
                        if (e.getClickedBlock() == null) {
                            p.playSound(p.getLocation(), Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 0.1f, 0.9f);
                            return;
                        } else {
                            location = new Location(e.getClickedBlock().getWorld(), e.getClickedBlock().getX(), e.getClickedBlock().getY(), e.getClickedBlock().getZ());
                        }
                        e.setCancelled(true);
                        linkTorch(p, location);
                    } else {
                        e.setCancelled(false);
                    }
                }
                case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> {
                    Adapt.verbose("Player " + p.getName() + " is right clicking a block");
                    if (hasCooldown(p)) {
                        p.playSound(p.getLocation(), Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 0.1f, 0.9f);
                    } else {
                        cooldowns.put(p, System.currentTimeMillis() + getConfig().cooldown);
                        triggerPulse(p);
                        e.setCancelled(true);
                    }
                }
            }
        }
    }


    private boolean hasCooldown(Player i) {
        if (cooldowns.containsKey(i)) {
            if (M.ms() >= cooldowns.get(i)) {
                cooldowns.remove(i);
            }
        }
        return cooldowns.containsKey(i);
    }


    private void linkTorch(Player p, Location l) {
        if (!l.getBlock().getType().equals(Material.TARGET)) {
            return;
        }
        if (getConfig().showParticles) {
            vfxSingleCuboidOutline(l.getBlock(), l.getBlock(), Color.RED, 1);
        }
        p.getWorld().playSound(l, Sound.BLOCK_CHEST_OPEN, 0.1f, 9f);
        p.getWorld().playSound(l, Sound.ENTITY_ENDER_EYE_DEATH, 0.2f, 0.48f);
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getAmount() == 1) {
            BoundRedstoneTorch.setData(hand, l);
        } else {
            hand.setAmount(hand.getAmount() - 1);
            ItemStack torch = BoundRedstoneTorch.withData(l);
            p.getInventory().addItem(torch).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
        }
    }


    private void triggerPulse(Player p) {
        Location l = BoundRedstoneTorch.getLocation(p.getInventory().getItemInMainHand());
        if (isBound(p.getInventory().getItemInMainHand()) && l != null) {
            loadChunkAsync(l, chunk -> {
                Block b = l.getBlock();
                BlockData data = b.getBlockData();
                if (data instanceof AnaloguePowerable redBlock && b.getType().equals(Material.TARGET)) {
                    p.getWorld().playSound(l, Sound.BLOCK_CHEST_OPEN, 0.1f, 9f);
                    redBlock.setPower(15);
                    vfxSingleCuboidOutline(l.getBlock(), l.getBlock(), Color.RED, 1);
                    b.setBlockData(redBlock);
                    J.s(() -> {
                        redBlock.setPower(0);
                        b.setBlockData(redBlock);
                    }, 2);
                } else {
                    p.playSound(p.getLocation(), Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 0.1f, 0.9f);
                }
            });
        }
    }

    private boolean isBound(ItemStack stack) {
        return (stack.getType().equals(Material.REDSTONE_TORCH) && BoundRedstoneTorch.getLocation(stack) != null);
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }


    @Override
    public void onTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            ItemStack offhand = p.getInventory().getItemInOffHand();
            if (!isBound(hand)) {
                J.s(() -> p.setCooldown(Material.REDSTONE_TORCH, 0));
            } else {
                J.s(() -> p.setCooldown(Material.REDSTONE_TORCH, 50000));
            }
        }
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    protected static class Config {
        public int cooldown = 125;
        boolean permanent = true;
        boolean enabled = true;
        boolean showParticles = true;
        int baseCost = 5;
        int maxLevel = 1;
        int initialCost = 0;
        double costFactor = 1;
    }
}
