package com.volmit.adapt.content.adaptation.axe;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.RNG;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class AxeChop extends SimpleAdaptation<AxeChop.Config> {
    private final KList<Integer> holds = new KList<>();

    public AxeChop() {
        super("axe-chop");
        registerConfiguration(Config.class);
        setDescription("Chop down trees by right clicking the base log!");
        setDisplayName("Axe Chop");
        setIcon(Material.IRON_AXE);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(5000);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + level + C.GRAY + " Blocks Per Chop");
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownTime(getLevelPercent(level)) * 50D, 1) + C.GRAY + " Chop Cooldown");
        v.addLore(C.RED + "- " + getDamagePerBlock(getLevelPercent(level)) + C.GRAY + " Tool Wear");
    }

    @EventHandler
    public void on(PlayerInteractEvent e) {
        if(e.getClickedBlock() != null && e.getAction().equals(Action.RIGHT_CLICK_BLOCK) && isAxe(e.getPlayer().getInventory().getItemInMainHand()) && getLevel(e.getPlayer()) > 0) {
            if(e.getPlayer().getCooldown(e.getPlayer().getInventory().getItemInMainHand().getType()) > 0) {
                e.getPlayer().getLocation().getWorld().playSound(e.getPlayer().getLocation(), Sound.ITEM_AXE_STRIP, 0.2f, (1.5f + RNG.r.f(0.5f)));
                getSkill().xp(e.getPlayer(), 2.25);

                return;
            }

            BlockData b = e.getClickedBlock().getBlockData();
            if(isLog(b)) {
                e.getPlayer().getLocation().getWorld().playSound(e.getPlayer().getLocation(), Sound.ITEM_AXE_STRIP, 1.25f, 0.6f);

                for(int i = 0; i < getLevel(e.getPlayer()); i++) {
                    if(breakStuff(e.getClickedBlock(), getRange(getLevel(e.getPlayer())))) {
                        getSkill().xp(e.getPlayer(), 37);
                        e.getPlayer().setCooldown(e.getPlayer().getInventory().getItemInMainHand().getType(), getCooldownTime(getLevelPercent(e.getPlayer())));
                        damageHand(e.getPlayer(), getDamagePerBlock(getLevelPercent(e.getPlayer())));
                    }
                }
            }
        }
    }

    private int getRange(int level) {
        return level * getConfig().rangeLevelMultiplier;
    }

    private int getCooldownTime(double levelPercent) {
        return (int) (getConfig().cooldownTicksBase + (getConfig().cooldownTicksInverseLevelMultiplier * ((1D - levelPercent))));
    }

    private int getDamagePerBlock(double levelPercent) {
        return (int) (getConfig().damagePerBlockBase + (getConfig().damagePerBlockInverseLevelMultiplier * ((1D - levelPercent))));
    }

    private boolean breakStuff(Block b, int power) {
        Block last = b;
        for(int i = b.getY(); i < power + b.getY(); i++) {
            Block bb = b.getWorld().getBlockAt(b.getX(), i, b.getZ());
            if(isLog(bb.getBlockData())) {
                last = bb;
            } else {
                break;
            }
        }

        if(!isLog(last.getBlockData())) {
            return false;
        }

        Block ll = last;
        b.getWorld().playSound(ll.getLocation(), Sound.ITEM_AXE_STRIP, 0.75f, 1.3f);
        ll.breakNaturally();
        return true;
    }

    private boolean isLog(BlockData b) {
        switch(b.getMaterial()) {
            case ACACIA_LOG:
            case BIRCH_LOG:
            case DARK_OAK_LOG:
            case JUNGLE_LOG:
            case OAK_LOG:
            case SPRUCE_LOG:
            case STRIPPED_ACACIA_LOG:
            case STRIPPED_BIRCH_LOG:
            case STRIPPED_DARK_OAK_LOG:
            case STRIPPED_JUNGLE_LOG:
            case STRIPPED_OAK_LOG:
            case STRIPPED_SPRUCE_LOG:
            case ACACIA_WOOD:
            case BIRCH_WOOD:
            case DARK_OAK_WOOD:
            case JUNGLE_WOOD:
            case OAK_WOOD:
            case SPRUCE_WOOD:
            case STRIPPED_ACACIA_WOOD:
            case STRIPPED_BIRCH_WOOD:
            case STRIPPED_DARK_OAK_WOOD:
            case STRIPPED_JUNGLE_WOOD:
            case STRIPPED_OAK_WOOD:
            case STRIPPED_SPRUCE_WOOD:
            case MUSHROOM_STEM:
            case BROWN_MUSHROOM_BLOCK:
            case RED_MUSHROOM_BLOCK:
                return true;
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

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        int baseCost = 3;
        double costFactor = 0.75;
        int maxLevel = 3;
        int initialCost = 5;
        int rangeLevelMultiplier = 5;
        double cooldownTicksBase = 15;
        double cooldownTicksInverseLevelMultiplier = 16;
        double damagePerBlockBase = 1;
        double damagePerBlockInverseLevelMultiplier = 4;
    }
}
