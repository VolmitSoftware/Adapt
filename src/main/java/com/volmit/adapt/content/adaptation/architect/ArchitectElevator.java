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

import com.jeff_media.customblockdata.CustomBlockData;
import com.jeff_media.customblockdata.events.CustomBlockDataMoveEvent;
import com.jeff_media.customblockdata.events.CustomBlockDataRemoveEvent;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.api.recipe.MaterialChar;
import com.volmit.adapt.util.CustomModel;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.SoundPlayer;
import lombok.NoArgsConstructor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ArchitectElevator extends SimpleAdaptation<ArchitectElevator.Config> {
    private static final NamespacedKey ELEVATOR_KEY = new NamespacedKey(Adapt.instance, "elevator");
    private static final NamespacedKey TARGET_DOWN = new NamespacedKey(Adapt.instance, "target_down");
    private static final NamespacedKey TARGET_UP = new NamespacedKey(Adapt.instance, "target_up");

    private static final int PARTICLE_COUNT = 20;
    private static final float SOUND_VOLUME = 1f;
    private static final float SOUND_PITCH = 1f;

    private final Set<UUID> players = new HashSet<>();

    public ArchitectElevator() {
        super("architect-elevator");
        registerConfiguration(ArchitectElevator.Config.class);
        setDescription(Localizer.dLocalize("architect.elevator.description"));
        setDisplayName(Localizer.dLocalize("architect.elevator.name"));
        setIcon(Material.HEAVY_WEIGHTED_PRESSURE_PLATE);
        setInterval(988);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);

        registerRecipe(AdaptRecipe.shaped()
                .key("elevator")
                .shape("XXX")
                .shape("XYX")
                .shape("XXX")
                .ingredient(new MaterialChar('X', Tag.WOOL))
                .ingredient(new MaterialChar('Y', Material.ENDER_PEARL))
                .result(getElevatorItem())
                .build());
    }

    @Override
    public void addStats(int level, Element v) {

    }

    public ItemStack getElevatorItem() {
        ItemStack elevatorItem = CustomModel.get(Material.NOTE_BLOCK, "architect", "elevator", "item")
                        .toItemStack();
        ItemMeta meta = elevatorItem.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(ELEVATOR_KEY, PersistentDataType.BYTE, (byte) 0);
            meta.setDisplayName(Localizer.dLocalize("items.elevator_block.name"));
            meta.setLore(List.of(Localizer.dLocalize("items.elevator_block.usage1"),
                    Localizer.dLocalize("items.elevator_block.usage2"),
                    Localizer.dLocalize("items.elevator_block.usage3")));
            elevatorItem.setItemMeta(meta);
        }
        return elevatorItem;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player player = e.getPlayer();

        if (!players.add(player.getUniqueId())) {
            if (e.getFrom().getY() < e.getTo().getY() || player.isFlying())
                players.remove(player.getUniqueId());
            return;
        }

        if (player.isFlying() || player.getVelocity().getY() <= 0 || e.getFrom().getY() >= e.getTo().getY())
            return;

        Block block = findElevator(player);
        if (block == null) return;
        handleElevatorMovement(block, player, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerToggleSneakEvent event) {
        if (!event.isSneaking() || event.getPlayer().isInsideVehicle()) return;
        Player player = event.getPlayer();
        Block block = findElevator(player);
        if (block == null) return;
        handleElevatorMovement(block, player, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(BlockPlaceEvent event) {
        ItemMeta meta = event.getItemInHand().getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(ELEVATOR_KEY, PersistentDataType.BYTE))
            return;
        int maxDistance = getMaxDistance(event.getPlayer());
        if (maxDistance <= 0) {
            event.setCancelled(true);
            return;
        }

        Block block = event.getBlock();
        World world = block.getWorld();
        CustomBlockData data = new CustomBlockData(block, Adapt.instance);
        data.set(ELEVATOR_KEY, PersistentDataType.INTEGER, maxDistance);

        int lowerDist = Math.min(block.getY() - world.getMinHeight(), maxDistance);
        for (int d = 1; d <= lowerDist; d++) {
            var lower = block.getRelative(BlockFace.DOWN, d);
            if (checkElevator(lower, TARGET_UP, d)) {
                data.set(TARGET_DOWN, PersistentDataType.INTEGER, d);
                break;
            }
        }

        int upperDist = Math.min(world.getMaxHeight() - block.getY(), maxDistance);
        for (int d = 1; d <= upperDist; d++) {
            var upper = block.getRelative(BlockFace.UP, d);
            if (checkElevator(upper, TARGET_DOWN, d)) {
                data.set(TARGET_UP, PersistentDataType.INTEGER, d);
            }
        }
    }

    public int getMaxDistance(Player player) {
        int level = getLevel(player);
        if (level == 0) return 0;
        Config config = getConfig();
        return config.baseDistance * (level * config.multiplier);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void on(CustomBlockDataMoveEvent event) {
        if (!event.getCustomBlockData().has(ELEVATOR_KEY)) return;
        event.setCancelled(true);

        Event bukkit = event.getBukkitEvent();
        if (bukkit instanceof Cancellable cancellable) {
            cancellable.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void on(BlockExplodeEvent event) {
        event.blockList().removeIf(ArchitectElevator::isElevator);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void on(EntityExplodeEvent event) {
        event.blockList().removeIf(ArchitectElevator::isElevator);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void on(CustomBlockDataRemoveEvent event) {
        CustomBlockData data = event.getCustomBlockData();
        if (!data.has(ELEVATOR_KEY)) return;
        Event bukkit = event.getBukkitEvent();
        if (!(bukkit instanceof BlockBreakEvent breakEvent)) {
            if (bukkit instanceof Cancellable cancellable)
                cancellable.setCancelled(true);
            event.setCancelled(true);
            return;
        }

        breakEvent.setDropItems(false);
        Block block = event.getBlock();
        World world = block.getWorld();
        Location location = block.getLocation();
        world.dropItemNaturally(location, getElevatorItem());

        data.remove(ELEVATOR_KEY);
        int y = block.getY();
        int lowerY = data.getOrDefault(TARGET_DOWN, PersistentDataType.INTEGER, 0);
        int upperY = data.getOrDefault(TARGET_UP, PersistentDataType.INTEGER, 0);
        data.remove(TARGET_DOWN);
        data.remove(TARGET_UP);

        if (y - lowerY < world.getMinHeight())
            lowerY = 0;

        if (y + upperY > world.getMaxHeight())
            upperY = 0;

        if (lowerY != 0 && upperY != 0) {
            Block lower = block.getRelative(BlockFace.DOWN, lowerY);
            Block upper = block.getRelative(BlockFace.UP, upperY);

            boolean lowerElevator = isElevator(lower);
            boolean upperElevator = isElevator(upper);

            if (lowerElevator && upperElevator) {
                CustomBlockData lowerData = new CustomBlockData(lower, Adapt.instance);
                CustomBlockData upperData = new CustomBlockData(upper, Adapt.instance);

                int dist = upperY + lowerY;
                int lowerDist = lowerData.getOrDefault(ELEVATOR_KEY, PersistentDataType.INTEGER, 0);
                int upperDist = upperData.getOrDefault(ELEVATOR_KEY, PersistentDataType.INTEGER, 0);
                int maxDistance = Math.max(upperDist, lowerDist);

                if (dist <= maxDistance) {
                    lowerData.set(TARGET_UP, PersistentDataType.INTEGER, dist);
                    upperData.set(TARGET_DOWN, PersistentDataType.INTEGER, dist);
                } else {
                    lowerData.remove(TARGET_UP);
                    upperData.remove(TARGET_DOWN);
                }
            } else if (lowerElevator) {
                new CustomBlockData(lower, Adapt.instance)
                        .remove(TARGET_UP);
            } else if (upperElevator) {
                new CustomBlockData(upper, Adapt.instance)
                        .remove(TARGET_DOWN);
            }
        } else if (lowerY != 0) {
            Block lower = block.getRelative(BlockFace.DOWN, lowerY);

            if (isElevator(lower)) {
                new CustomBlockData(lower, Adapt.instance)
                        .remove(TARGET_UP);
            }
        } else if (upperY != 0) {
            Block upper = block.getRelative(BlockFace.UP, upperY);

            if (isElevator(upper)) {
                new CustomBlockData(upper, Adapt.instance)
                        .remove(TARGET_DOWN);
            }
        }
    }

    @Nullable
    private Block findElevator(Player player) {
        Block base = player.getLocation().getBlock();
        for (int d = 1; d <= 2; d++) {
            Block rel = base.getRelative(BlockFace.DOWN, d);
            if (isElevator(rel))
                return rel;
        }
        return null;
    }

    private boolean checkElevator(Block block, NamespacedKey key, int source) {
        if (!isElevator(block))
            return false;

        new CustomBlockData(block, Adapt.instance)
                .set(key, PersistentDataType.INTEGER, source);
        return true;
    }

    private void handleElevatorMovement(Block block, Player player, boolean down) {
        if (!isElevator(block) || player.isInsideVehicle())
            return;

        CustomBlockData data = new CustomBlockData(block, Adapt.instance);
        int distance = data.getOrDefault(down ? TARGET_DOWN : TARGET_UP, PersistentDataType.INTEGER, 0);
        if (distance == 0)
            return;
        int targetY = block.getY() + (down ? -distance : distance);
        if (targetY < block.getWorld().getMinHeight() || targetY > block.getWorld().getMaxHeight())
            return;

        Block target = block.getRelative(down ? BlockFace.DOWN : BlockFace.UP, distance);
        if (!isElevator(target))
            return;

        var loc = player.getLocation();
        loc.setY(target.getY() + 1);

        if (!hasEnoughSpace(player, loc.getBlockY()))
            return;

        teleportPlayer(player, loc);
    }

    private static boolean isElevator(Block b) {
        return b.getType() == Material.NOTE_BLOCK
                && CustomBlockData.hasCustomBlockData(b, Adapt.instance)
                && new CustomBlockData(b, Adapt.instance)
                .has(ELEVATOR_KEY, PersistentDataType.INTEGER);
    }

    private static boolean hasEnoughSpace(Player player, int targetY) {
        BoundingBox box = player.getBoundingBox()
                .shift(0, -player.getLocation().getY(), 0)
                .shift(0, targetY, 0);

        double maxX = Math.ceil(box.getMaxX());
        double maxY = Math.ceil(box.getMaxY());
        double maxZ = Math.ceil(box.getMaxZ());
        World world = player.getWorld();
        for (int x = (int) box.getMinX(); x <= maxX; x++) {
            for (int z = (int) box.getMinZ(); z <= maxZ; z++) {
                for (int y = (int) box.getMinY(); y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.isPassable() || block.isLiquid())
                        continue;
                    VoxelShape shape = block.getCollisionShape();
                    box.shift(-x, -y, -z);
                    if (shape.overlaps(box))
                        return false;
                    box.shift(x, y, z);
                }
            }
        }
        return true;
    }

    private void teleportPlayer(Player p, Location l) {
        playTeleportEffects(p);
        p.teleport(l);
        SoundPlayer.of(p.getWorld()).play(p, Sound.ENTITY_ENDERMAN_TELEPORT, SOUND_VOLUME, SOUND_PITCH);
        playTeleportEffects(p);
    }

    private void playTeleportEffects(Player p) {
        p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation(), PARTICLE_COUNT);
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
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Base Distance for the Architect Elevator adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int baseDistance = 32;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Multiplier for the Architect Elevator adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int multiplier = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.40;
    }
}