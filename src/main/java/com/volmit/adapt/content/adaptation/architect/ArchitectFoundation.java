package com.volmit.adapt.content.adaptation.architect;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.content.block.ScaffoldMatter;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.KMap;
import com.volmit.adapt.util.KSet;
import com.volmit.adapt.util.M;
import lombok.NoArgsConstructor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.HashSet;
import java.util.Set;

public class ArchitectFoundation extends SimpleAdaptation<ArchitectFoundation.Config> {
    private final KMap<Player, Integer> blockPower;
    private final KMap<Player, Long> cooldowns;
    private final Set<Player> active;
    private static final BlockData AIR = Material.AIR.createBlockData();
    private static final BlockData BLOCK = Material.TINTED_GLASS.createBlockData();

    public ArchitectFoundation() {
        super("architect-foundation");
        registerConfiguration(ArchitectFoundation.Config.class);
        setDescription("This allows for you to sneak and place a temporary foundation beneath you");
        setDisplayName("Architect's Magic Foundation");
        setIcon(Material.TINTED_GLASS);
        setInterval(1000);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        blockPower = new KMap<>();
        cooldowns = new KMap<>();
        active = new HashSet<>();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerMoveEvent e) {
        if (!hasAdaptation(e.getPlayer())) {
            return;
        }
        if (!e.getFrom().getBlock().equals(e.getTo().getBlock())) {
            // We only want actual block position changes, this would be spammy if we dont
            return;
        }

        if (!this.active.contains(e.getPlayer())) {
            return;
        }

        int power = blockPower.get(e.getPlayer());

        if (power <= 0) {
            return;
        }

        Location l = e.getTo();
        World world = l.getWorld();
        KSet<Block> locs = new KSet<>();
        locs.add(world.getBlockAt(l.clone().add(0.3, -1, -0.3)));
        locs.add(world.getBlockAt(l.clone().add(-0.3, -1, -0.3)));
        locs.add(world.getBlockAt(l.clone().add(0.3, -1, 0.3)));
        locs.add(world.getBlockAt(l.clone().add(-0.3, -1, +0.3)));

        for (Block b : locs) {
            if (power > 0) {
                if (addFoundation(b)) {
                    xp(e.getPlayer(), 3);
                    power--;
                }
            }

            if (power <= 0) {
                break;
            }
        }

        blockPower.put(e.getPlayer(), power);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerToggleSneakEvent e) {
        if (!hasAdaptation(e.getPlayer()) || e.getPlayer().getGameMode().equals(GameMode.CREATIVE)
                || e.getPlayer().getGameMode().equals(GameMode.SPECTATOR)) {
            return;
        }

        boolean ready = !hasCooldown(e.getPlayer());
        boolean active = this.active.contains(e.getPlayer());

        if (e.isSneaking() && ready && !active) {
            this.active.add(e.getPlayer());
            cooldowns.put(e.getPlayer(), Long.MAX_VALUE);
            // effect start placing
        } else if (!e.isSneaking() && active) {
            this.active.remove(e.getPlayer());
            cooldowns.put(e.getPlayer(), M.ms() + getConfig().cooldown);
            e.getPlayer().getWorld().playSound(e.getPlayer().getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 100.0f, 10.0f);
            e.getPlayer().getWorld().playSound(e.getPlayer().getLocation(), Sound.BLOCK_SCULK_CATALYST_BREAK, 100.0f, 0.81f);
        }
    }

    public boolean addFoundation(Block block) {
        if (!block.getType().isAir()) {
            return false;
        }

        J.s(() -> block.setBlockData(BLOCK));
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_DEEPSLATE_PLACE, 1.0f, 1.0f);
        vfxSingleCubeOutline(block, Particle.REVERSE_PORTAL);
        vfxSingleCubeOutline(block, Particle.ASH);
        J.a(() -> removeFoundation(block), 3 * 20);
        return true;
    }

    public boolean removeFoundation(Block block) {
        if (!block.getBlockData().equals(BLOCK)) {
            return false;
        }

        J.s(() -> block.setBlockData(AIR));
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_DEEPSLATE_BREAK, 1.0f, 1.0f);
        vfxSingleCubeOutline(block, Particle.ENCHANTMENT_TABLE);
        return true;
    }

    public int getBlockPower(double factor) {
        return (int) Math.floor(M.lerp(getConfig().minBlocks, getConfig().maxBlocks, factor));
    }

    @Override
    public void onTick() {
        for (Player i : Bukkit.getOnlinePlayers()) {
            if (!hasAdaptation(i)) {
                continue;
            }

            boolean ready = !hasCooldown(i);
            int availablePower = getBlockPower(getLevelPercent(i));
            int power = blockPower.compute(i, (k, v) -> {
                if ((k == null || v == null) || (ready && v != availablePower)) {
                    i.getWorld().playSound(i.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 100.0f, 10.0f);
                    i.getWorld().playSound(i.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 100.0f, 0.81f);
                    return availablePower;
                }

                return v;
            });
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

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "Magically create " + (getBlockPower(getLevelPercent(level))) + C.GRAY + " Total Blocks beneath you!");
    }

    @NoArgsConstructor
    protected static class Config {
        public long duration = 3000;
        public int minBlocks = 9;
        public int maxBlocks = 35;
        public int cooldown = 5000;
        boolean enabled = true;
        int baseCost = 5;
        int maxLevel = 5;
        int initialCost = 1;
        double costFactor = 0.40;
    }
}
