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

package art.arcane.adapt.content.adaptation.agility;
import art.arcane.volmlib.util.format.Form;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.math.M;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.inventorygui.Element;
import art.arcane.adapt.util.common.inventorygui.Window;
import art.arcane.adapt.util.common.misc.SoundPlayer;

public class AgilityWallJump extends SimpleAdaptation<AgilityWallJump.Config> {
    private final Map<Player, Double> airjumps;
    private final Map<Player, Vector> horizontalIntent;
    private final Map<Player, Long> horizontalIntentTime;

    public AgilityWallJump() {
        super("agility-wall-jump");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("agility.wall_jump.description"));
        setDisplayName(Localizer.dLocalize("agility.wall_jump.name"));
        setIcon(Material.VINE);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(50);
        airjumps = new HashMap<>();
        horizontalIntent = new HashMap<>();
        horizontalIntentTime = new HashMap<>();
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.LADDER)
                .key("challenge_agility_wall_jump_500")
                .title(Localizer.dLocalize("advancement.challenge_agility_wall_jump_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_agility_wall_jump_500.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.FEATHER)
                .key("challenge_agility_parkour_master")
                .title(Localizer.dLocalize("advancement.challenge_agility_parkour_master.title"))
                .description(Localizer.dLocalize("advancement.challenge_agility_parkour_master.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.HIDDEN)
                .build());
        registerMilestone("challenge_agility_wall_jump_500", "agility.wall-jump.air-jumps", 500, 500);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + getMaxJumps(level) + C.GRAY + " " + Localizer.dLocalize("agility.wall_jump.lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getJumpHeight(level), 0) + C.GRAY + " " + Localizer.dLocalize("agility.wall_jump.lore2"));
    }

    @EventHandler
    public void on(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        airjumps.remove(p);
        horizontalIntent.remove(p);
        horizontalIntentTime.remove(p);
    }

    private int getMaxJumps(int level) {
        return (int) (level + (level / getConfig().maxJumpsLevelBonusDivisor));
    }

    private double getJumpHeight(int level) {
        return getConfig().jumpHeightBase + (getLevelPercent(level) * getConfig().jumpHeightBonusLevelMultiplier);
    }

    @EventHandler
    public void on(PlayerMoveEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        if (!canInteract(p, p.getLocation())) {
            return;
        }
        if (airjumps.containsKey(p)) {
            if (p.isOnGround() && !p.getLocation().getBlock().getRelative(BlockFace.DOWN).getBlockData().getMaterial().isAir()) {
                airjumps.remove(p);
            }
        }

        if (e.getTo() == null || e.getFrom().getWorld() == null || e.getTo().getWorld() == null || !e.getFrom().getWorld().equals(e.getTo().getWorld())) {
            return;
        }

        Vector delta = e.getTo().toVector().subtract(e.getFrom().toVector());
        delta.setY(0);
        double movementThresholdSq = getConfig().inputMovementThreshold * getConfig().inputMovementThreshold;
        if (delta.lengthSquared() >= movementThresholdSq) {
            horizontalIntent.put(p, delta.normalize());
            horizontalIntentTime.put(p, M.ms());
        }
    }

    @Override
    public void onTick() {
        for (art.arcane.adapt.api.world.AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
            Player p = adaptPlayer.getPlayer();
            int level = getLevel(p);
            if (level <= 0) {
                continue;
            }

            Double j = airjumps.get(p);

            if (j != null && j - 0.25 >= getMaxJumps(level)) {
                p.setGravity(true);
                continue;
            }

            if (p.isOnGround()) {
                airjumps.remove(p);
                if (!p.hasGravity()) {
                    p.setGravity(true);
                }
                continue;
            }

            if (!canInteract(p, p.getLocation())) {
                continue;
            }

            Block stickBlock = stickToWall(p);
            if (p.isFlying() || !p.isSneaking() || p.getFallDistance() < 0.3) {
                boolean jumped = false;

                if (!p.hasGravity() && p.getFallDistance() > 0.45 && stickBlock != null) {
                    j = j == null ? 0 : j;
                    j++;

                    if (j - 0.25 <= getMaxJumps(level)) {
                        jumped = true;
                        Vector launch = p.getVelocity().clone().setY(getJumpHeight(level));
                        if (isBackwardLaunch(p)) {
                            Vector direction = p.getLocation().getDirection().clone().setY(0);
                            if (direction.lengthSquared() > 0.000001) {
                                direction.normalize().multiply(-getConfig().backwardPushSpeed);
                                launch.setX(direction.getX());
                                launch.setZ(direction.getZ());
                            }
                        }
                        p.setVelocity(launch);
                        if (areParticlesEnabled()) {
                            p.getWorld().spawnParticle(Particles.BLOCK_CRACK, p.getLocation().clone().add(0, 0.3, 0), 15, 0.1, 0.8, 0.1, 0.1, stickBlock.getBlockData());
                        }
                        getPlayer(p).getData().addStat("agility.wall-jump.air-jumps", 1);
                        if (j >= 5 && AdaptConfig.get().isAdvancements() && !getPlayer(p).getData().isGranted("challenge_agility_parkour_master")) {
                            getPlayer(p).getAdvancementHandler().grant("challenge_agility_parkour_master");
                        }
                    }
                    airjumps.put(p, j);
                }

                if (!jumped && !p.hasGravity()) {
                    p.setGravity(true);
                    SoundPlayer spw = SoundPlayer.of(p.getWorld());
                    spw.play(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 0.439f);
                }
                continue;
            }

            if (stickBlock != null) {
                if (p.hasGravity()) {
                    SoundPlayer spw = SoundPlayer.of(p.getWorld());
                    spw.play(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 0.89f);
                    spw.play(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 1f, 1.39f);
                    if (areParticlesEnabled()) {
                        p.getWorld().spawnParticle(Particles.BLOCK_CRACK, p.getLocation().clone().add(0, 0.3, 0), 15, 0.1, 0.2, 0.1, 0.1, stickBlock.getBlockData());
                    }
                }

                applyWallStickForce(p, stickBlock);
                p.setGravity(false);
                Vector c = p.getVelocity();
                p.setVelocity(p.getVelocity().setY((c.getY() * 0.35) - 0.0025));
                Double vv = airjumps.get(p);
                vv = vv == null ? 0 : vv;
                vv += 0.0127;
                airjumps.put(p, vv);
            }

            if (stickBlock == null && !p.hasGravity()) {
                p.setGravity(true);
            }
        }
    }

    private boolean isBackwardLaunch(Player p) {
        Long at = horizontalIntentTime.get(p);
        Vector intent = horizontalIntent.get(p);
        if (at == null || intent == null || M.ms() - at > getConfig().inputWindowMs) {
            return false;
        }

        Vector facing = p.getLocation().getDirection().clone().setY(0);
        if (facing.lengthSquared() <= 0.000001) {
            return false;
        }

        facing.normalize();
        return intent.dot(facing) <= -Math.abs(getConfig().backwardIntentDotThreshold);
    }

    private Block stickToWall(Player p) {
        for (Block wall : getBlocks(p)) {
            if (wall.getBlockData().getMaterial().isSolid()) {
                return wall;
            }
        }

        return null;
    }

    private void applyWallStickForce(Player p, Block wall) {
        Vector velocity = p.getVelocity();
        Vector shift = p.getLocation().toVector().subtract(wall.getLocation().clone().add(0.5, 0.5, 0.5).toVector());
        velocity.setX(velocity.getX() - (shift.getX() / 16));
        velocity.setZ(velocity.getZ() - (shift.getZ() / 16));
        p.setVelocity(velocity);
    }

    private Block[] getBlocks(Player p) {
        Block base = p.getLocation().getBlock();
        return new Block[]{
                base.getRelative(BlockFace.NORTH),
                base.getRelative(BlockFace.SOUTH),
                base.getRelative(BlockFace.EAST),
                base.getRelative(BlockFace.WEST),
                base.getRelative(BlockFace.NORTH_EAST),
                base.getRelative(BlockFace.SOUTH_EAST),
                base.getRelative(BlockFace.NORTH_WEST),
                base.getRelative(BlockFace.SOUTH_WEST),
                base.getRelative(BlockFace.NORTH_EAST).getRelative(BlockFace.UP),
                base.getRelative(BlockFace.SOUTH_EAST).getRelative(BlockFace.UP),
                base.getRelative(BlockFace.NORTH_WEST).getRelative(BlockFace.UP),
                base.getRelative(BlockFace.SOUTH_WEST).getRelative(BlockFace.UP),
                base.getRelative(BlockFace.NORTH).getRelative(BlockFace.UP),
                base.getRelative(BlockFace.SOUTH).getRelative(BlockFace.UP),
                base.getRelative(BlockFace.EAST).getRelative(BlockFace.UP),
                base.getRelative(BlockFace.WEST).getRelative(BlockFace.UP),
        };
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
    @ConfigDescription("Hold shift while mid-air against a wall to latch and jump.")
    protected static class Config {
        @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Agility Wall Jump adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showParticles = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 2;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.65;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 8;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Jumps Level Bonus Divisor for the Agility Wall Jump adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxJumpsLevelBonusDivisor = 2;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Jump Height Base for the Agility Wall Jump adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double jumpHeightBase = 0.625;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Jump Height Bonus Level Multiplier for the Agility Wall Jump adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double jumpHeightBonusLevelMultiplier = 0.225;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Backward Push Speed for the Agility Wall Jump adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double backwardPushSpeed = 0.22;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Backward Intent Dot Threshold for the Agility Wall Jump adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double backwardIntentDotThreshold = 0.35;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Input Movement Threshold for the Agility Wall Jump adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double inputMovementThreshold = 0.0025;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Input Window Ms for the Agility Wall Jump adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        long inputWindowMs = 450;
    }
}
