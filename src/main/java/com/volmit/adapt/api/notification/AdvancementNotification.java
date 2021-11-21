package com.volmit.adapt.api.notification;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.RNG;
import eu.endercentral.crazy_advancements.Advancement;
import eu.endercentral.crazy_advancements.AdvancementDisplay;
import eu.endercentral.crazy_advancements.AdvancementVisibility;
import eu.endercentral.crazy_advancements.NameKey;
import eu.endercentral.crazy_advancements.manager.AdvancementManager;
import lombok.Builder;
import lombok.Data;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

@Data
@Builder
public class AdvancementNotification implements Notification{
    @Builder.Default
    private final Material icon = Material.DIAMOND;
    @Builder.Default
    private final String title = " ";
    @Builder.Default
    private final String description = " ";
    @Builder.Default
    private final AdvancementDisplay.AdvancementFrame frameType = AdvancementDisplay.AdvancementFrame.TASK;
    @Builder.Default
    private final String group = "default";

    @Override
    public long getTotalDuration() {
        return 100; // TODO: Actually calculate
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public void play(AdaptPlayer p) {
        AdvancementDisplay d = new AdvancementDisplay(icon, buildTitle(), description, frameType, true, false, AdvancementVisibility.ALWAYS);
        Advancement a = new Advancement(null, new NameKey("adapt-notifications", "n" + p.getId() + RNG.r.lmax()), d);
        a.displayToast(p.getPlayer());
    }

    public String buildTitle()
    {
        if(description.trim().isEmpty())
        {
            return title;
        }

        return title + "\n" + description;
    }
}
