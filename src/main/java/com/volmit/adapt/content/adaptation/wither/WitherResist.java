package com.volmit.adapt.content.adaptation.wither;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.content.adaptation.rift.RiftResist;
import com.volmit.adapt.content.skill.SkillWither;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_19_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

public class WitherResist extends SimpleAdaptation<WitherResist.Config> {

    private static final Random RANDOM = new Random();

    public WitherResist() {
        super(SkillWither.id("resist"));
        registerConfiguration(Config.class);
        setDescription("Resists withering through the power of Netherite.");
        setIcon(Material.NETHERITE_CHESTPLATE);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(9283);
    }

    @Override
    public void addStats(int level, Element v) {
        int chance = (int)(getConfig().basePieceChance + getConfig().getChanceAddition() * level);
        v.addLore(C.GREEN + "+ " + chance + "%" + C.GRAY + " chance to negate withering (per piece).");
        v.addLore(C.GRAY + "Passive: Wearing Netherite Armor has a chance to negate " + C.DARK_GRAY + "withering.");
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent e) {
        if(e.getCause() == EntityDamageEvent.DamageCause.WITHER && e.getEntity() instanceof Player p) {
            if(!hasAdaptation(p))
                return;
            double chance = getTotalChange(p);
            if(RANDOM.nextInt(0, 101) <= chance)
                e.setCancelled(true);
        }
    }

    @Override
    public boolean isEnabled() { return getConfig().isEnabled(); }

    @Override
    public void onTick() { }

    private double getTotalChange(Player p) {
        return getChance(p, EquipmentSlot.HEAD) + getChance(p, EquipmentSlot.CHEST) + getChance(p, EquipmentSlot.LEGS) + getChance(p, EquipmentSlot.FEET);
    }

    private double getChance(Player p, EquipmentSlot slot) {
        if(p.getEquipment() == null)
            return 0.0;
        ItemStack item = p.getEquipment().getItem(slot);
        if(item.getType() == Material.NETHERITE_HELMET || item.getType() == Material.NETHERITE_CHESTPLATE || item.getType() == Material.NETHERITE_LEGGINGS || item.getType() == Material.NETHERITE_BOOTS)
            return getConfig().basePieceChance + getConfig().chanceAddition * getLevel(p);
        return 0.0D;
    }

    @Data
    @NoArgsConstructor
    public static class Config {
        private boolean enabled = true;
        private int baseCost = 3;
        private double costFactor = 1;
        private int maxLevel = 3;
        private int initialCost = 5;
        private double basePieceChance = 10;
        private double chanceAddition = 5;
    }
}
