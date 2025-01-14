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
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.content.item.BoundEnderPearl;
import com.volmit.adapt.util.*;
import lombok.NoArgsConstructor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import us.lynuxcraft.deadsilenceiv.advancedchests.AdvancedChestsAPI;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.volmit.adapt.api.adaptation.chunk.ChunkLoading.loadChunkAsync;

public class RiftAccess extends SimpleAdaptation<RiftAccess.Config> {
    private final Map<Location, List<InventoryView>> activeViewsMap = new ConcurrentHashMap<>();


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
        setInterval(1000);
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
        ItemStack mainHand = p.getInventory().getItemInMainHand();
        ItemStack offHand = p.getInventory().getItemInOffHand();
        Block block = e.getClickedBlock();

        boolean mainHandBound = BoundEnderPearl.isBindableItem(mainHand);
        boolean offHandBound = BoundEnderPearl.isBindableItem(offHand);

        // Cancel event if the enderpearl is in the offhand
        if (offHandBound && e.getHand() != null && e.getHand().equals(EquipmentSlot.OFF_HAND)) {
            e.setCancelled(true);
            return;
        }

        // If the main hand is holding a bound enderpearl
        if (mainHandBound) {
            e.setCancelled(true);
            if (hasAdaptation(p)) {
                Adapt.verbose("Player using bound enderpearl.");
                handleEnderPearlInteraction(e, p, block);
            }
        }
    }

    private void handleEnderPearlInteraction(PlayerInteractEvent event, Player player, Block block) {
        boolean canUseInCreative = AdaptConfig.get().allowAdaptationsInCreative;
        boolean isCreative = player.getGameMode() == GameMode.CREATIVE;
        boolean sneaking = player.isSneaking();
        boolean allowed = canUseInCreative || !isCreative;


        // Check if the player is allowed to use the bound item in creative
        if (!allowed) {
            Adapt.info("Player " + player.getName() + " tried to use the bound item in creative mode.");
            return;
        }

        switch (event.getAction()) {
            case LEFT_CLICK_BLOCK -> {
                // If player is sneaking and left-clicking a container
                if (sneaking && isStorage(block.getBlockData())) {
                    if (canAccessChest(player, block.getLocation())) {
                        linkPearl(player, block, event);
                    } else {
                        Adapt.verbose("Player " + player.getName() + " doesn't have permission.");
                    }
                }
            }
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK ->
                // If player right-clicks on air or any block
                    openPearl(player);
            default -> {
            }
        }
    }

    private void linkPearl(Player p, Block block, PlayerInteractEvent event) {
        event.setCancelled(true);
        if (getConfig().showParticles) {
            vfxCuboidOutline(block, Particle.REVERSE_PORTAL);
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        SoundPlayer sp = SoundPlayer.of(p);
        sp.play(p.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 0.5f, 0.8f);

        if (hand.getAmount() == 1) {
            BoundEnderPearl.setData(hand, block);
        } else {
            hand.setAmount(hand.getAmount() - 1);
            ItemStack pearl = BoundEnderPearl.withData(block);
            p.getInventory().addItem(pearl).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
        }
    }

    private void openPearl(Player p) {
        SoundPlayer sp = SoundPlayer.of(p);
        Block b = BoundEnderPearl.getBlock(p.getInventory().getItemInMainHand());
        if (b == null || !canAccessChest(p, b.getLocation())) {
            sp.play(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
            return;
        }
        loadChunkAsync(b.getLocation(), chunk -> {
            if (Bukkit.getPluginManager().isPluginEnabled("AdvancedChests") &&
                    AdvancedChestsAPI.getChestManager().getAdvancedChest(b.getLocation()) != null) {
                AdvancedChestsAPI.getChestManager().getAdvancedChest(b.getLocation()).openPage(p, 1);
                Adapt.verbose("Opening AdvancedChests GUI");
            } else if (b.getState() instanceof InventoryHolder holder) {
                InventoryView view = p.openInventory(holder.getInventory());
                activeViewsMap.computeIfAbsent(b.getLocation(), k -> new ArrayList<>()).add(view);
            }
            sp.play(p.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.10f);
            sp.play(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 0.10f);
        });
    }

    @Override
    public void onTick() {
        J.s(this::checkActiveViews);
    }

    private void checkActiveViews() {
        Iterator<Map.Entry<Location, List<InventoryView>>> mapIterator = activeViewsMap.entrySet().iterator();
        while (mapIterator.hasNext()) {
            Map.Entry<Location, List<InventoryView>> entry = mapIterator.next();
            removeInvalidViews(entry);
            removeEntryIfViewsEmpty(mapIterator, entry);
        }
    }

    private void removeInvalidViews(Map.Entry<Location, List<InventoryView>> entry) {
        List<InventoryView> views = entry.getValue();
        for (int ii = views.size() - 1; ii >= 0; ii--) {
            InventoryView i = views.get(ii);
            if (shouldRemoveView(i)) {
                views.remove(ii);
            }
        }
    }

    private boolean shouldRemoveView(InventoryView i) {
        Location location = i.getTopInventory().getLocation();
        return !i.getPlayer().getOpenInventory().equals(i) || (location == null || !isStorage(location.getBlock().getBlockData()));
    }

    private void removeEntryIfViewsEmpty(Iterator<Map.Entry<Location, List<InventoryView>>> mapIterator, Map.Entry<Location, List<InventoryView>> entry) {
        List<InventoryView> views = entry.getValue();
        if (views.isEmpty()) {
            mapIterator.remove();
        }
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockBurnEvent event) {
        if (event.isCancelled()) return;
        invClose(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockPistonRetractEvent event) {
        if (event.isCancelled()) return;
        for (Block b : event.getBlocks()) {
            invClose(b);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockPistonExtendEvent event) {
        if (event.isCancelled()) return;
        for (Block b : event.getBlocks()) {
            invClose(b);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockExplodeEvent event) {
        if (event.isCancelled()) return;
        for (Block b : event.blockList()) {
            invClose(b);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        invClose(event.getBlock());
    }


    private void invClose(Block block) {
        List<InventoryView> views = activeViewsMap.get(block.getLocation());
        if (views != null) {
            for (InventoryView view : views) {
                view.getPlayer().closeInventory();
            }
            activeViewsMap.remove(block.getLocation());
        }
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
        final boolean showParticles = true;
        final int baseCost = 3;
        final double costFactor = 0.2;
        final int initialCost = 15;
    }
}
