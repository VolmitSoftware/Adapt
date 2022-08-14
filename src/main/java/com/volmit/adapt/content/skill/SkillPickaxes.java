package com.volmit.adapt.content.skill;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.content.adaptation.pickaxe.PickaxeAutosmelt;
import com.volmit.adapt.content.adaptation.pickaxe.PickaxeChisel;
import com.volmit.adapt.content.adaptation.pickaxe.PickaxeDropToInventory;
import com.volmit.adapt.content.adaptation.pickaxe.PickaxeVeinminer;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.J;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public class SkillPickaxes extends SimpleSkill<SkillPickaxes.Config> {
    public SkillPickaxes() {
        super("pickaxes", Adapt.dLocalize("Skill", "Pickaxe", "Icon"));
        registerConfiguration(Config.class);
        setDescription(Adapt.dLocalize("Skill", "Pickaxe", "Description"));
        setDisplayName(Adapt.dLocalize("Skill", "Pickaxe", "Name"));
        setColor(C.GOLD);
        setInterval(2750);
        setIcon(Material.NETHERITE_PICKAXE);
        registerAdaptation(new PickaxeChisel());
        registerAdaptation(new PickaxeVeinminer());
        registerAdaptation(new PickaxeAutosmelt());
        registerAdaptation(new PickaxeDropToInventory());
    }

    @EventHandler (priority = EventPriority.HIGHEST)
    public void on(EntityDamageByEntityEvent e) {
        if (!e.isCancelled()) {
            if (e.getDamager() instanceof Player) {
                AdaptPlayer a = getPlayer((Player) e.getDamager());
                ItemStack hand = a.getPlayer().getInventory().getItemInMainHand();
                if (isPickaxe(hand)) {
                    xp(a.getPlayer(), e.getEntity().getLocation(), getConfig().damageXPMultiplier * e.getDamage());
                    getPlayer(a.getPlayer()).getData().addStat("pickaxes.swings", 1);
                    getPlayer(a.getPlayer()).getData().addStat("pickaxes.damage", e.getDamage());
                }
            }
        }
    }

    @EventHandler
    public void on(BlockBreakEvent e) {
        if (isPickaxe(e.getPlayer().getInventory().getItemInMainHand())) {
            double v = getValue(e.getBlock().getType());
            getPlayer(e.getPlayer()).getData().addStat("pickaxes.blocks.broken", 1);
            getPlayer(e.getPlayer()).getData().addStat("pickaxes.blocks.value", getValue(e.getBlock().getBlockData()));
            J.a(() -> xp(e.getPlayer(), e.getBlock().getLocation().clone().add(0.5, 0.5, 0.5), blockXP(e.getBlock(), v)));
        }
    }

    public double getValue(Material type) {
        Config c = getConfig();
        double value = super.getValue(type) * c.blockValueMultiplier;
        value += Math.min(c.maxHardnessBonus, type.getHardness());
        value += Math.min(c.maxBlastResistanceBonus, type.getBlastResistance());
        switch (type) {
            case COAL_ORE -> value += c.coalBonus;
            case COPPER_ORE -> value += c.copperBonus;
            case IRON_ORE -> value += c.ironBonus;
            case GOLD_ORE -> value += c.goldBonus;
            case LAPIS_ORE -> value += c.lapisBonus;
            case DIAMOND_ORE -> value += c.diamondBonus;
            case EMERALD_ORE -> value += c.emeraldBonus;
            case NETHER_GOLD_ORE -> value += c.netherGoldBonus;
            case NETHER_QUARTZ_ORE -> value += c.netherQuartzBonus;
            case REDSTONE_ORE -> value += c.redstoneBonus;
            case DEEPSLATE_COAL_ORE -> value += c.coalBonus * c.deepslateMultiplier;
            case DEEPSLATE_COPPER_ORE -> value += c.copperBonus * c.deepslateMultiplier;
            case DEEPSLATE_IRON_ORE -> value += c.ironBonus * c.deepslateMultiplier;
            case DEEPSLATE_GOLD_ORE -> value += c.goldBonus * c.deepslateMultiplier;
            case DEEPSLATE_LAPIS_ORE -> value += c.lapisBonus * c.deepslateMultiplier;
            case DEEPSLATE_DIAMOND_ORE -> value += c.diamondBonus * c.deepslateMultiplier;
            case DEEPSLATE_EMERALD_ORE -> value += c.emeraldBonus * c.deepslateMultiplier;
            case DEEPSLATE_REDSTONE_ORE -> value += c.redstoneBonus * c.deepslateMultiplier;
        }

        return value * 0.48;
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
        double damageXPMultiplier = 13.26;
        double blockValueMultiplier = 0.125;
        double maxHardnessBonus = 9;
        double maxBlastResistanceBonus = 10;
        double coalBonus = 101;
        double ironBonus = 251;
        double redstoneBonus = 386;
        double copperBonus = 166;
        double goldBonus = 651;
        double lapisBonus = 851;
        double diamondBonus = 525;
        double emeraldBonus = 721;
        double netherGoldBonus = 770;
        double netherQuartzBonus = 111;
        double deepslateMultiplier = 1.35;
    }
}
