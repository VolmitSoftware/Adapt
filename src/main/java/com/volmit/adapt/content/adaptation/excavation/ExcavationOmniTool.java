package com.volmit.adapt.content.adaptation.excavation;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.content.item.multiItems.OmniTool;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ExcavationOmniTool extends SimpleAdaptation<ExcavationOmniTool.Config> {
    public ExcavationOmniTool() {
        super("excavation-omnitool");
        registerConfiguration(ExcavationOmniTool.Config.class);
        setDisplayName("Trusty T.O.O.L.");
        setDescription("A Craftable Leatherman Tool, This will swap based on needs.");
        setIcon(Material.DISC_FRAGMENT_5);
        setInterval(20202);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        registerRecipe(AdaptRecipe.shapeless()
                .key("excavation-omnitool")
                .ingredient(Material.IRON_SHOVEL)
                .ingredient(Material.IRON_PICKAXE)
                .ingredient(Material.IRON_AXE)
                .ingredient(Material.IRON_HOE)
                .ingredient(Material.DIAMOND_BLOCK)
                .ingredient(Material.ENDER_PEARL)
                .result(new ItemStack(Material.DISC_FRAGMENT_5))
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GRAY + "This is a morphing tool that holds all the tools, switching when needed");
        v.addLore(C.GREEN + "" + (level + 3) + C.GRAY + "x Levels of haste when you start mining ANY block with the right tool");
        v.addLore(C.ITALIC + "if you lose this item, you lose all of the items");
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public void onTick() {
    }

    @EventHandler
    public void on(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getAction().equals(Action.RIGHT_CLICK_BLOCK) || e.getAction().equals(Action.RIGHT_CLICK_AIR)) {
            OmniTool tool = new OmniTool();
            ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
            ItemStack shovel = new ItemStack(Material.DIAMOND_SHOVEL);
            ItemStack axe = new ItemStack(Material.DIAMOND_AXE);
            ItemStack starter = pickaxe;
            tool.setItems(starter, List.of(shovel, axe));
            p.getInventory().setItemInMainHand(tool.switchTo(starter, -1));


        }
    }

    @NoArgsConstructor
    protected static class Config {
        boolean enabled = true;
        int baseCost = 15;
        int initialCost = 5;
        double costFactor = 1;
        int maxLevel = 1;
    }
}
