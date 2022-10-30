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

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.content.item.BoundEnderPearl;
import com.volmit.adapt.nms.NMS;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import us.lynuxcraft.deadsilenceiv.advancedchests.AdvancedChestsAPI;

import java.util.ArrayList;
import java.util.List;

import static com.volmit.adapt.api.adaptation.chunk.ChunkLoading.loadChunkAsync;

public class RiftAccess extends SimpleAdaptation<RiftAccess.Config> {
    private final List<InventoryView> activeViews = new ArrayList<>();

    public RiftAccess() {
        super("rift-access");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("rift", "remoteaccess", "description"));
        setDisplayName(Localizer.dLocalize("rift", "remoteaccess", "name"));
        setMaxLevel(1);
        setIcon(Material.NETHER_STAR);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setInitialCost(getConfig().initialCost);
        setInterval(5544);
        registerRecipe(AdaptRecipe.shapeless()
                .key("rift-remote-access")
                .ingredient(Material.ENDER_PEARL)
                .ingredient(Material.COMPASS)
                .result(BoundEnderPearl.io.withData(new BoundEnderPearl.Data(null)))
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.ITALIC + Localizer.dLocalize("rift", "remoteaccess", "lore1"));
        v.addLore(C.ITALIC + Localizer.dLocalize("rift", "remoteaccess", "lore2"));
        v.addLore(C.ITALIC + Localizer.dLocalize("rift", "remoteaccess", "lore3"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        ItemMeta handMeta = hand.getItemMeta();
        Block block = e.getClickedBlock();

        ItemStack offhand = p.getInventory().getItemInOffHand();
        if (e.getHand() != null && e.getHand().equals(EquipmentSlot.OFF_HAND) && BoundEnderPearl.isBindableItem(offhand)) {
            e.setCancelled(true);
            return;
        }

        if (BoundEnderPearl.isBindableItem(hand) && hasAdaptation(p)) {
            e.setCancelled(true);
            switch (e.getAction()) {
                case LEFT_CLICK_BLOCK -> {
                    if (block != null && isStorage(block.getBlockData())) { // Ensure its a container
                        if (p.isSneaking()) { // Binding (Sneak Container)
                            linkPearl(p, block);
                        }
                    } else if (block != null && !isStorage(block.getBlockData())) {
                        if (p.isSneaking()) { //(Sneak NOT Container)
                            Adapt.messagePlayer(p, C.LIGHT_PURPLE + Localizer.dLocalize("rift", "remoteaccess", "notcontainer"));
                        }
                    }
                }
                case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> {
                    if (p.hasCooldown(hand.getType())) {
                        return;
                    } else {
                        NMS.get().sendCooldown(p, Material.ENDER_PEARL, 100);
                        p.setCooldown(Material.ENDER_PEARL, 100);
                    }
                    if (isBound(hand)) {
                        openPearl(p);
                    }
                }
            }
        } else if (BoundEnderPearl.isBindableItem(hand)) {
            e.setCancelled(true);
        }
    }

    private void linkPearl(Player p, Block block) {
        if (getConfig().showParticles) {
            vfxSingleCubeOutline(block, Particle.REVERSE_PORTAL);
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 0.5f, 0.8f);

        if (hand.getAmount() == 1) {
            BoundEnderPearl.setData(hand, block);
        } else {
            hand.setAmount(hand.getAmount() - 1);
            ItemStack pearl = BoundEnderPearl.withData(block);
            p.getInventory().addItem(pearl).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
        }
    }

    private void openPearl(Player p) {
        Block b = BoundEnderPearl.getBlock(p.getInventory().getItemInMainHand());
        if (b == null) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
            return;
        }
        loadChunkAsync(b.getLocation(), chunk -> {
            if (AdvancedChestsAPI.getChestManager().getAdvancedChest(b.getLocation()) != null) {
                AdvancedChestsAPI.getChestManager().getAdvancedChest(b.getLocation()).openPage(p, 1);
                p.playSound(p.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.10f);
                p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 0.10f);
                return;
            }
            if (b.getState() instanceof InventoryHolder holder) {
                activeViews.add(p.openInventory(holder.getInventory()));
                p.playSound(p.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.10f);
                p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 0.10f);
            }
        });
    }

    private boolean isBound(ItemStack stack) {
        return (stack.getType().equals(Material.ENDER_PEARL) && BoundEnderPearl.getBlock(stack) != null);
    }

    @Override
    public void onTick() {
        if (!this.isEnabled()) {
            return;
        }
        J.s(() -> {
            for (int ii = activeViews.size() - 1; ii >= 0; ii--) {
                InventoryView i = activeViews.get(ii);

                if (i.getPlayer().getOpenInventory().equals(i)) {
                    if (i.getTopInventory().getLocation() == null || !isStorage(i.getTopInventory().getLocation().getBlock().getBlockData())) {
                        i.getPlayer().closeInventory();
                        i.getPlayer().removePotionEffect(PotionEffectType.BLINDNESS);
                        activeViews.remove(ii);
                    }
                } else {
                    i.getPlayer().removePotionEffect(PotionEffectType.BLINDNESS);
                    activeViews.remove(ii);
                }
            }
        });
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
        boolean showParticles = true;
        int baseCost = 3;
        double costFactor = 0.2;
        int initialCost = 15;
    }

}