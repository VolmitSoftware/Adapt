package com.volmit.adapt.content.adaptation.stealth;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.util.*;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class StealthSnatch extends SimpleAdaptation<StealthSnatch.Config> {
    private final List<Integer> holds = new ArrayList<>();

    public StealthSnatch() {
        super("stealth-snatch");
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Stealth","ItemSnatch", "Description"));
        setDisplayName(Adapt.dLocalize("Stealth","ItemSnatch", "Name"));
        setIcon(Material.CHEST_MINECART);
        setBaseCost(getConfig().baseCost);
        setInterval(50);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getRange(getLevelPercent(level)), 1) + C.GRAY + Adapt.dLocalize("Stealth","ItemSnatch", "Lore1"));
    }

    @EventHandler
    public void on(PlayerToggleSneakEvent e) {
        if (!hasAdaptation(e.getPlayer())) {
            return;
        }

        if (e.isSneaking()) {
            snatch(e.getPlayer());
        }
    }

    private void snatch(Player player) {
        double factor = getLevelPercent(player);

        if (factor == 0) {
            return;
        }

        if (!player.isDead()) {
            double range = getRange(factor);

            for (Entity j : player.getWorld().getNearbyEntities(player.getLocation(), range, range / 1.5, range)) {
                if (j instanceof Item && !holds.contains(j.getEntityId())) {
                    double dist = j.getLocation().distanceSquared(player.getLocation());

                    if (dist < range * range && player.isSneaking() && j.getTicksLived() > 1) {
                        ItemStack is = ((Item) j).getItemStack().clone();

                        if (Inventories.hasSpace(player.getInventory(), is)) {
                            holds.add(j.getEntityId());

                            if (player.isSneaking()) {
                                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_LAVA_POP, 1f, (float) (1.0 + (Math.random() / 3)));
                            }

                            player.getInventory().addItem(is);
                            sendCollected(player, (Item) j);
                            j.remove();
                            getSkill().xp(player, 1.27);

                            int id = j.getEntityId();

                            J.s(() -> holds.remove(Integer.valueOf(id)));
                        }
                    }
                }
            }
        }
    }

    private double getRange(double factor) {
        return (factor * getConfig().radiusFactor) + 1;
    }

    public void sendCollected(Player plr, Item item) {
        try {
            String nmstag = Bukkit.getServer().getClass().getCanonicalName().split("\\Q.\\E")[3];
            Class<?> c = Class.forName("net.minecraft.server." + nmstag + ".PacketPlayOutCollect");
            Class<?> p = Class.forName("net.minecraft.server." + nmstag + ".EntityPlayer");
            Class<?> pk = Class.forName("net.minecraft.server." + nmstag + ".Packet");
            Object v = c.getConstructor().newInstance();
            new V(v).set("a", item.getEntityId());
            new V(v).set("b", plr.getEntityId());
            new V(v).set("c", item.getItemStack().getAmount());

            for (Entity i : plr.getWorld().getNearbyEntities(plr.getLocation(), 8, 8, 8)) {
                if (i instanceof Player) {
                    Object pconnect = new V(new V(i).invoke("getHandle")).get("playerConnection");
                    pconnect.getClass().getMethod("sendPacket", pk).invoke(pconnect, v);
                }
            }
        } catch (Throwable e) {

        }
    }

    @Override
    public void onTick() {
        for (Player i : Bukkit.getOnlinePlayers()) {
            if (i.isSneaking()) {
                J.s(() -> snatch(i));
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        int baseCost = 4;
        int maxLevel = 3;
        int initialCost = 12;
        double costFactor = 0.125;
        double radiusFactor = 5.55;
    }
}
