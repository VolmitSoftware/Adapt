package com.volmit.adapt.content.adaptation.brewing;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.data.WorldData;
import com.volmit.adapt.api.world.AdaptServer;
import com.volmit.adapt.api.world.PlayerAdaptation;
import com.volmit.adapt.api.world.PlayerData;
import com.volmit.adapt.content.skill.SkillBrewing;
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
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BrewingLongLasting extends SimpleAdaptation<BrewingLongLasting.Config> {
    private final KList<Integer> holds = new KList<>();

    public BrewingLongLasting() {
        super("lasting");
        registerConfiguration(Config.class);
        setDescription("Brewed potions last longer!");
        setIcon(Material.CLOCK);
        setBaseCost(getConfig().baseCost);
        setCostFactor(getConfig().costFactor);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(5000);
    }

    public double getDurationBoost(double factor)
    {
        return (getConfig().durationBoostFactor * factor) + getConfig().baseDurationBoost;
    }

    public double getPercentBoost(double factor)
    {
        return (factor * factor * getConfig().durationMultiplierFactor) + getConfig().baseDurationMultiplier;
    }

    @EventHandler
    public void on(BrewEvent e)
    {
        if(e.getBlock().getType().equals(Material.BREWING_STAND))
        {
            SkillBrewing.BrewingStandOwner owner = WorldData.of(e.getBlock().getWorld()).getMantle().get(e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ(), SkillBrewing.BrewingStandOwner.class);

            if(owner != null)
            {
                PlayerData data = null;
                for(int i = 2; i < e.getContents().getStorageContents().length; i++)
                {
                    ItemStack is = e.getContents().getStorageContents()[i];

                    if(is != null && is.getItemMeta() != null && is.getItemMeta() instanceof PotionMeta p)
                    {
                        data = data == null ? getServer().peekData(owner.getOwner()) : data;

                        if(data.getSkillLines().containsKey(getSkill().getName()) && data.getSkillLine(getSkill().getName()).getAdaptations().containsKey(getName()))
                        {
                            PlayerAdaptation a = data.getSkillLine(getSkill().getName()).getAdaptations().get(getName());

                            if(a.getLevel() > 0)
                            {
                                double factor = getLevelPercent(a.getLevel());
                                enhance(factor, is, p);
                            }
                        }
                    }
                }
            }
        }
    }

    private void enhance(double factor, ItemStack is, PotionMeta p) {
        if(p.getBasePotionData() != null && !p.getBasePotionData().getType().isInstant())
        {
            PotionEffect effect = getRawPotionEffect(is);

            if(effect != null)
            {
                p.addCustomEffect(new PotionEffect(effect.getType(), (int) (getDurationBoost(factor) + (effect.getDuration() * getPercentBoost(factor))), effect.getAmplifier()), true);
                List<String> lore = p.getLore();
                lore = lore != null ? lore : new ArrayList<>();
                is.setItemMeta(p);
            }
        }
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.duration((long) getDurationBoost(getLevelPercent(level)), 0) + C.GRAY + " Duration");
        v.addLore(C.GREEN + "+ " + Form.pc(getPercentBoost(getLevelPercent(level)), 0) + C.GRAY + " Duration");
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
        int maxLevel = 5;
        int initialCost = 5;
        double baseDurationBoost = 5000;
        double durationBoostFactor = 10000;
        double durationMultiplierFactor = 0.45;
        double baseDurationMultiplier = 0.05;
    }
}
