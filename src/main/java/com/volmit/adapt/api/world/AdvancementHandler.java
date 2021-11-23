package com.volmit.adapt.api.world;

import com.volmit.adapt.util.J;
import eu.endercentral.crazy_advancements.Advancement;
import eu.endercentral.crazy_advancements.AdvancementDisplay;
import eu.endercentral.crazy_advancements.AdvancementVisibility;
import eu.endercentral.crazy_advancements.NameKey;
import eu.endercentral.crazy_advancements.manager.AdvancementManager;
import lombok.Data;
import org.bukkit.Material;

@Data
public class AdvancementHandler
{
    private AdvancementManager manager;
    private AdaptPlayer player;

    public AdvancementHandler(AdaptPlayer player)
    {
        this.player = player;
        this.manager = new AdvancementManager(player.getPlayer());
    }

    public void activate()
    {
        J.s(() -> {
            removeAllAdvancements();
        }, 20);
    }

    private void dumb()
    {
        AdvancementDisplay d = new AdvancementDisplay(Material.EMERALD, "Adapt", "Adapt Stuff", AdvancementDisplay.AdvancementFrame.TASK, false, false, AdvancementVisibility.ALWAYS);
        d.setBackgroundTexture("minecraft:textures/block/deepslate_tiles.png");
        Advancement root = new Advancement(null, new NameKey("adapt", "root"), d);
        getManager().addAdvancement(root);
    }

    public void deactivate()
    {
        removeAllAdvancements();
    }

    public void removeAllAdvancements()
    {
        for(Advancement i : getManager().getAdvancements())
        {
            getManager().removeAdvancement(i);
        }
    }
}
