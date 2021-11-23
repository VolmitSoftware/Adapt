package com.volmit.adapt.api.advancement;

import com.volmit.adapt.util.KList;
import eu.endercentral.crazy_advancements.Advancement;
import eu.endercentral.crazy_advancements.AdvancementDisplay;
import eu.endercentral.crazy_advancements.AdvancementVisibility;
import eu.endercentral.crazy_advancements.NameKey;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import org.bukkit.Material;

@Builder
@Data
public class AdaptAdvancement
{
    private String background;
    @Builder.Default
    private Material icon = Material.EMERALD;
    @Builder.Default
    private String title = "MISSING TITLE";
    @Builder.Default
    private String description = "MISSING DESCRIPTION";
    @Builder.Default
    private AdvancementDisplay.AdvancementFrame frame = AdvancementDisplay.AdvancementFrame.TASK;
    @Builder.Default
    private boolean toast = true;
    @Builder.Default
    private boolean announce = false;
    @Builder.Default
    private AdvancementVisibility visibility = AdvancementVisibility.PARENT_GRANTED;
    @Builder.Default
    private String key = "root";
    @Singular
    @Builder.Default
    private KList<AdaptAdvancement> children = new KList<>();

    public Advancement toAdvancement(Advancement parent)
    {
        return new Advancement(parent, new NameKey("adapt", getKey()), new AdvancementDisplay(getIcon(), getTitle(), getDescription(), getFrame(), isToast(), isAnnounce(), getVisibility()));
    }

    public Advancement toAdvancement()
    {
        return toAdvancement(null);
    }
}
