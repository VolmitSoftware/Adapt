package com.volmit.adapt.content.adaptation.brewing;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.data.WorldData;
import com.volmit.adapt.api.world.PlayerData;
import com.volmit.adapt.content.matter.BrewingStandOwner;
import com.volmit.adapt.util.*;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;


public class BrewingSuperHeated extends SimpleAdaptation<BrewingSuperHeated.Config> {
    private final Set<Block> activeStands = new HashSet<>();

    public BrewingSuperHeated() {
        super("brewing-super-heated");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("SuperHeated.Description"));
        setDisplayName(Adapt.dLocalize("SuperHeated.Name"));
        setIcon(Material.LAVA_BUCKET);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(250);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getFireBoost(getLevelPercent(level)), 0) + C.GRAY + Adapt.dLocalize("SuperHeated.Lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getLavaBoost(getLevelPercent(level)), 0) + C.GRAY + Adapt.dLocalize("SuperHeated.Lore2"));
    }

    public double getLavaBoost(double factor) {
        return (getConfig().lavaMultiplier) * (getConfig().multiplierFactor * factor);
    }

    public double getFireBoost(double factor) {
        return (getConfig().fireMultiplier) * (getConfig().multiplierFactor * factor);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(BrewEvent e) {
        J.s(() -> {
            if (((BrewingStand) e.getBlock().getState()).getBrewingTime() > 0) {
                activeStands.add(e.getBlock());
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getType().equals(InventoryType.BREWING)) {
            activeStands.add(e.getView().getTopInventory().getLocation().getBlock());
        }
    }


    @Override
    public void onTick() {
        if (activeStands.isEmpty()) {
            return;
        }

        Iterator<Block> it = activeStands.iterator();

        J.s(() -> {
            while (it.hasNext()) {
                BlockState s = it.next().getState();

                if (s instanceof BrewingStand b) {
                    if (b.getBrewingTime() <= 0) {
                        J.s(() -> {
                            BrewingStand bb = (BrewingStand) s.getBlock().getState();

                            if (bb.getBrewingTime() <= 0) {
                                activeStands.remove(b.getBlock());
                            }
                        });
                        continue;
                    }

                    BrewingStandOwner owner = WorldData.of(b.getWorld()).getMantle().get(b.getX(), b.getY(), b.getZ(), BrewingStandOwner.class);

                    if (owner == null) {
                        it.remove();
                        continue;
                    }

                    PlayerData p = getServer().peekData(owner.getOwner());

                    if (p.getSkillLines().get(getSkill().getName()) != null && p.getSkillLines().get(getSkill().getName()).getAdaptations().containsKey(getName())
                            && p.getSkillLines().get(getSkill().getName()).getAdaptations().get(getName()).getLevel() > 0) {
                        updateHeat(b, getLevelPercent(p.getSkillLines().get(getSkill().getName()).getAdaptations().get(getName()).getLevel()));
                    } else {
                        it.remove();
                    }
                } else {
                    it.remove();
                }
            }
        });
    }

    private void updateHeat(BrewingStand b, double factor) {
        double l = 0;
        double f = 0;

        switch (b.getBlock().getRelative(BlockFace.DOWN).getType()) {
            case LAVA -> l = l + 1;
            case FIRE -> f = f + 1;
        }
        switch (b.getBlock().getRelative(BlockFace.NORTH).getType()) {
            case LAVA -> l = l + 1;
            case FIRE -> f = f + 1;
        }
        switch (b.getBlock().getRelative(BlockFace.SOUTH).getType()) {
            case LAVA -> l = l + 1;
            case FIRE -> f = f + 1;
        }
        switch (b.getBlock().getRelative(BlockFace.EAST).getType()) {
            case LAVA -> l = l + 1;
            case FIRE -> f = f + 1;
        }
        switch (b.getBlock().getRelative(BlockFace.WEST).getType()) {
            case LAVA -> l = l + 1;
            case FIRE -> f = f + 1;
        }

        double pct = (getFireBoost(factor) * f) + (getLavaBoost(factor) * l) + 1;
        int warp = (int) ((getInterval() / 50D) * pct);
        b.setBrewingTime(Math.max(1, b.getBrewingTime() - warp));
        b.update();

        if (M.r(1D / (333D / getInterval()))) {
            b.getBlock().getWorld().playSound(b.getBlock().getLocation(), Sound.BLOCK_FIRE_AMBIENT, 1f, 1f + RNG.r.f(0.3f, 0.6f));
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        int baseCost = 3;
        double costFactor = 0.75;
        int maxLevel = 5;
        int initialCost = 5;
        double multiplierFactor = 1.33;
        double fireMultiplier = 0.14;
        double lavaMultiplier = 0.69;
    }
}
