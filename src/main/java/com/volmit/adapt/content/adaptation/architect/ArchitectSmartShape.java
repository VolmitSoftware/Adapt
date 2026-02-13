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

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.SoundPlayer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Axis;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class ArchitectSmartShape extends SimpleAdaptation<ArchitectSmartShape.Config> {
    private static final List<BlockFace> ROTATION_ORDER = Arrays.asList(
            BlockFace.NORTH,
            BlockFace.NORTH_NORTH_EAST,
            BlockFace.NORTH_EAST,
            BlockFace.EAST_NORTH_EAST,
            BlockFace.EAST,
            BlockFace.EAST_SOUTH_EAST,
            BlockFace.SOUTH_EAST,
            BlockFace.SOUTH_SOUTH_EAST,
            BlockFace.SOUTH,
            BlockFace.SOUTH_SOUTH_WEST,
            BlockFace.SOUTH_WEST,
            BlockFace.WEST_SOUTH_WEST,
            BlockFace.WEST,
            BlockFace.WEST_NORTH_WEST,
            BlockFace.NORTH_WEST,
            BlockFace.NORTH_NORTH_WEST
    );

    public ArchitectSmartShape() {
        super("architect-smart-shape");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("architect.smart_shape.description"));
        setDisplayName(Localizer.dLocalize("architect.smart_shape.name"));
        setIcon(Material.BRICKS);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(800);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.QUARTZ_STAIRS)
                .key("challenge_architect_smart_shape_200")
                .title(Localizer.dLocalize("advancement.challenge_architect_smart_shape_200.title"))
                .description(Localizer.dLocalize("advancement.challenge_architect_smart_shape_200.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.QUARTZ_STAIRS)
                        .key("challenge_architect_smart_shape_5k")
                        .title(Localizer.dLocalize("advancement.challenge_architect_smart_shape_5k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_architect_smart_shape_5k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_architect_smart_shape_200", "architect.smart-shape.rotations", 200, 300);
        registerMilestone("challenge_architect_smart_shape_5k", "architect.smart-shape.rotations", 5000, 1000);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Localizer.dLocalize("architect.smart_shape.lore1"));
        v.addLore(C.GREEN + "+ " + Localizer.dLocalize("architect.smart_shape.lore2"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerInteractEvent e) {
        if (e.getAction() != Action.LEFT_CLICK_BLOCK || e.getClickedBlock() == null) {
            return;
        }

        if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player p = e.getPlayer();
        if (!hasAdaptation(p) || !p.isSneaking()) {
            return;
        }

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (isItem(hand) && hand.getType() != Material.AIR) {
            return;
        }

        Block target = e.getClickedBlock();
        if (!canBlockPlace(p, target.getLocation())) {
            return;
        }

        BlockData data = target.getBlockData().clone();
        int options = rotateData(data);
        if (options <= 0) {
            return;
        }

        target.setBlockData(data, true);
        e.setCancelled(true);
        SoundPlayer.of(p.getWorld()).play(target.getLocation(), Sound.ITEM_AXE_STRIP, 0.45f, 1.8f);
        xp(p, Math.max(getConfig().minXpPerRotate, options * getConfig().xpPerOrientationOption));
        getPlayer(p).getData().addStat("architect.smart-shape.rotations", 1);
    }

    private int rotateData(BlockData data) {
        if (data instanceof Directional directional) {
            BlockFace next = getNextFace(directional.getFacing(), directional.getFaces());
            if (next != null && next != directional.getFacing()) {
                directional.setFacing(next);
                return directional.getFaces().size();
            }
        }

        if (data instanceof Rotatable rotatable) {
            BlockFace next = getNextFace(rotatable.getRotation(), Set.copyOf(ROTATION_ORDER), ROTATION_ORDER);
            if (next != null && next != rotatable.getRotation()) {
                rotatable.setRotation(next);
                return ROTATION_ORDER.size();
            }
        }

        if (data instanceof Orientable orientable) {
            Axis current = orientable.getAxis();
            Axis next = switch (current) {
                case X -> Axis.Y;
                case Y -> Axis.Z;
                case Z -> Axis.X;
            };

            if (orientable.getAxes().contains(next)) {
                orientable.setAxis(next);
                return orientable.getAxes().size();
            }

            if (orientable.getAxes().contains(Axis.X)) {
                orientable.setAxis(Axis.X);
                return orientable.getAxes().size();
            }
        }

        return 0;
    }

    private BlockFace getNextFace(BlockFace current, Set<BlockFace> supported) {
        if (supported == null || supported.isEmpty()) {
            return null;
        }

        List<BlockFace> ordered = new ArrayList<>(supported);
        ordered.sort(Comparator.comparingInt(Enum::ordinal));
        return getNextFace(current, supported, ordered);
    }

    private BlockFace getNextFace(BlockFace current, Set<BlockFace> supported, List<BlockFace> order) {
        if (supported == null || supported.isEmpty()) {
            return null;
        }

        int idx = order.indexOf(current);
        if (idx < 0) {
            for (BlockFace face : order) {
                if (supported.contains(face)) {
                    return face;
                }
            }

            return null;
        }

        for (int i = 1; i <= order.size(); i++) {
            BlockFace candidate = order.get((idx + i) % order.size());
            if (supported.contains(candidate)) {
                return candidate;
            }
        }

        return current;
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
    @ConfigDescription("Sneak-left-click a block with an empty hand to rotate its orientation.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Min Xp Per Rotate for the Architect Smart Shape adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double minXpPerRotate = 0.4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Orientation Option for the Architect Smart Shape adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerOrientationOption = 0.16;
    }
}
