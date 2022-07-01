package com.volmit.adapt.api.advancement;

import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.advancements.advancement.Advancement;
import com.volmit.adapt.util.advancements.advancement.AdvancementDisplay;
import com.volmit.adapt.util.advancements.advancement.AdvancementVisibility;
import com.volmit.adapt.util.advancements.NameKey;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import org.bukkit.Material;

import java.util.List;

@Builder
@Data
public class AdaptAdvancement {
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
    private boolean toast = false;
    @Builder.Default
    private boolean announce = false;
    @Builder.Default
    private AdvancementVisibility visibility = AdvancementVisibility.PARENT_GRANTED;
    @Builder.Default
    private String key = "root";
    @Singular
    private List<AdaptAdvancement> children;

    public Advancement toAdvancement() {
        return toAdvancement(null, 0, 0);
    }

    public Advancement toAdvancement(Advancement parent, int index, int depth) {
        if(children == null) {
            children = new KList<>();
        }

        AdvancementDisplay d = new AdvancementDisplay(getIcon(), getTitle(), getDescription(), getFrame(), getVisibility());

        if(background != null) {
            d.setBackgroundTexture(getBackground());
        }

        d.setX(1f + depth);
        d.setY(1f + index);

        return new Advancement(parent, new NameKey("adapt", getKey()), d);
    }

    public KList<Advancement> toAdvancements() {
        return toAdvancements(null, 0, 0);
    }

    public KList<Advancement> toAdvancements(Advancement p, int index, int depth) {
        KList<Advancement> aa = new KList<>();
        Advancement a = toAdvancement(p, index, depth);
        int ind = 0;
        if(children != null && !children.isEmpty()) {
            for(AdaptAdvancement i : children) {
                aa.addAll(i.toAdvancements(a, aa.size(), depth + 1));
            }
        }

        aa.add(a);

        return aa;
    }
}
