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

import static com.volmit.adapt.api.adaptation.chunk.ChunkLoading.loadChunkAsync;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.item.BoundRedstoneTorch;
import com.volmit.adapt.util.*;

import java.util.HashMap;
import java.util.Map;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.AnaloguePowerable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;


public class ArchitectWirelessRedstone extends SimpleAdaptation<ArchitectWirelessRedstone.Config> {
    private final Map<Player, Long> cooldowns;

    public ArchitectWirelessRedstone() {
        super("architect-wireless-redstone");
        registerConfiguration(ArchitectWirelessRedstone.Config.class);
        setDescription(Localizer.dLocalize("architect.wireless_redstone.description"));
        setDisplayName(Localizer.dLocalize("architect.wireless_redstone.name"));
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
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.REDSTONE)
                .key("challenge_architect_wireless_100")
                .title(Localizer.dLocalize("advancement.challenge_architect_wireless_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_architect_wireless_100.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.REDSTONE)
                        .key("challenge_architect_wireless_5k")
                        .title(Localizer.dLocalize("advancement.challenge_architect_wireless_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_architect_wireless_5k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_architect_wireless_100", "architect.wireless-redstone.pulses", 100, 300);
        registerMilestone("challenge_architect_wireless_5k", "architect.wireless-redstone.pulses", 5000, 1000);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + Localizer.dLocalize("architect.wireless_redstone.lore1"));
    }


    @EventHandler
    public void onPlaceBlock(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (BoundRedstoneTorch.hasItemData(item) && isRedstoneTorch(item)) {
            event.setBuild(false);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }

        ItemStack itemInHand = event.getItem();

        if (itemInHand == null) {
            return;
        }

        boolean specialItem =
            isRedstoneTorch(itemInHand) && BoundRedstoneTorch.hasItemData(itemInHand);
        if (!specialItem) {
            return;
        }

        Player player = event.getPlayer();

        if (!hasAdaptation(player)) {
            return;
        }

        boolean canUseInCreative = AdaptConfig.get().allowAdaptationsInCreative;
        boolean inCreative = player.getGameMode() == GameMode.CREATIVE;
        if (inCreative && !canUseInCreative) {
            return;
        }

        if (!canInteract(event.getPlayer(), event.getPlayer().getLocation())) {
            return;
        }

        switch (event.getAction()) {
            case LEFT_CLICK_BLOCK -> handleLeftClickBlock(event, player);
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> handleRightClick(event, player);
        }
    }


    private boolean isRedstoneTorch(ItemStack item) {
        return item.getType().equals(Material.REDSTONE_TORCH);
    }


    private void handleLeftClickBlock(PlayerInteractEvent event, Player player) {
        Adapt.verbose("Player " + player.getName() + " is left clicking a block");
        if (!player.isSneaking()) {
            return;
        }

        // main hand only
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (event.getClickedBlock() == null) {
            SoundPlayer sp = SoundPlayer.of(player);
            sp.play(player.getLocation(), Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 0.1f, 0.9f);
            return;
        }

        // prevent breaking block
        event.setUseItemInHand(Result.DENY);

        Location location = new Location(event.getClickedBlock().getWorld(), event.getClickedBlock().getX(), event.getClickedBlock().getY(), event.getClickedBlock().getZ());
        linkTorch(player, location);
    }

    private void handleRightClick(PlayerInteractEvent event, Player player) {
        Adapt.verbose("Player " + player.getName() + " is right clicking");

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setUseItemInHand(Result.DENY);
            event.setUseInteractedBlock(Result.DENY);
        }

        if (hasCooldown(player)) {
            SoundPlayer sp = SoundPlayer.of(player);
            sp.play(player.getLocation(), Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 0.1f, 0.9f);
        } else {
            cooldowns.put(player, System.currentTimeMillis() + getConfig().cooldown);
            updatePlayerCooldown(player, false);
            triggerPulse(player, event.getItem());
        }
    }

    public void updatePlayerCooldown(Player player, boolean reset) {
        player.setCooldown(Material.REDSTONE_TORCH, reset ? 0 : 5000);
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
        if (areParticlesEnabled()) {
            vfxCuboidOutline(l.getBlock(), l.getBlock(), Color.RED, 1);
        }
        SoundPlayer spw = SoundPlayer.of(p.getWorld());
        spw.play(l, Sound.BLOCK_CHEST_OPEN, 0.1f, 9f);
        spw.play(l, Sound.ENTITY_ENDER_EYE_DEATH, 0.2f, 0.48f);
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getAmount() == 1) {
            BoundRedstoneTorch.setData(hand, l);
        } else {
            hand.setAmount(hand.getAmount() - 1);
            ItemStack torch = BoundRedstoneTorch.withData(l);
            p.getInventory().addItem(torch).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
        }
    }


    private void triggerPulse(Player p, ItemStack item) {
        Location l = BoundRedstoneTorch.getLocation(item);
        if (isBound(item) && l != null) {
            loadChunkAsync(l, chunk -> {
                Block b = l.getBlock();
                BlockData data = b.getBlockData();
                if (data instanceof AnaloguePowerable redBlock && b.getType().equals(Material.TARGET)) {
                    SoundPlayer spw = SoundPlayer.of(p.getWorld());
                    spw.play(l, Sound.BLOCK_CHEST_OPEN, 0.1f, 9f);
                    redBlock.setPower(15);
                    vfxCuboidOutline(l.getBlock(), l.getBlock(), Color.RED, 1);
                    b.setBlockData(redBlock);
                    getPlayer(p).getData().addStat("architect.wireless-redstone.pulses", 1);
                    J.s(() -> {
                        redBlock.setPower(0);
                        b.setBlockData(redBlock);
                    }, 2);
                } else {
                    SoundPlayer sp = SoundPlayer.of(p);
                    sp.play(p.getLocation(), Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 0.1f, 0.9f);
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
        for (com.volmit.adapt.api.world.AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
            Player p = adaptPlayer.getPlayer();
            ItemStack hand = p.getInventory().getItemInMainHand();
            ItemStack offhand = p.getInventory().getItemInOffHand();
            if ((isRedstoneTorch(hand) && BoundRedstoneTorch.hasItemData(hand)) || (
                isRedstoneTorch(offhand) && BoundRedstoneTorch.hasItemData(offhand))) {
                J.s(() -> updatePlayerCooldown(p, false));
            } else {
                J.s(() -> updatePlayerCooldown(p, true));
            }
        }
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    @ConfigDescription("Use a crafted redstone remote to toggle redstone at a distance.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown for the Architect Wireless Redstone adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        public int cooldown = 125;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Architect Wireless Redstone adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showParticles = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 1;
    }
}
