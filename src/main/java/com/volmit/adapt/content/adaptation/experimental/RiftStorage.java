package com.volmit.adapt.content.adaptation.experimental;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;


public class RiftStorage extends SimpleAdaptation<RiftStorage.Config> {
    public RiftStorage() {
        super("rift-storage");
        setDescription("Open an enderchest by clicking");
        setIcon(Material.ENDER_CHEST);
        setBaseCost(0);
        setCostFactor(0);
        setMaxLevel(1);
        setInitialCost(10);
        setInterval(9248);
        registerConfiguration(Config.class);
    }

    private double getConsumePercent(int level) {
        return 0.15 + (0.15 * level);
    }


    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getConsumePercent(level)) + C.GRAY + " Chance to Resist Consumption");
        v.addLore(C.ITALIC + "*Click an Enderchest in your hand to open (Just dont place)*");
    }

    @EventHandler
    public void on(PlayerInteractEvent e) {
        if(getLevel(e.getPlayer()) > 0) {
            if(e.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.ENDER_CHEST)
                && (e.getAction().equals(Action.RIGHT_CLICK_AIR)
                || e.getAction().equals(Action.LEFT_CLICK_AIR)
                || e.getAction().equals(Action.LEFT_CLICK_BLOCK))) {
                Adapt.info("Opened Enderchest");
                Player p = e.getPlayer();

                p.getLocation().getWorld().playSound(p.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 5.35f, 0.10f);
                p.getLocation().getWorld().playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 5.35f, 0.10f);
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 35, 1, true, false, false));
                p.openInventory(e.getPlayer().getEnderChest());


            }
        }
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
    }
}