package com.volmit.adapt.api.advancement;

import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.BaseAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.multiParents.AbstractMultiParentsAdvancement;
import com.fren_gor.ultimateAdvancementAPI.database.TeamProgression;
import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;


public interface AdvancementVisibility {

    /**
     * Advancements with this Visibility will always be visible
     */
    AdvancementVisibility ALWAYS = (advancement, progression) -> true;

    /**
     * Advancements with this Visibility will be visible once their parent or any of their children is granted
     */
    AdvancementVisibility PARENT_GRANTED = (advancement, progression) -> {
        Preconditions.checkNotNull(advancement, "Advancement is null.");
        Preconditions.checkNotNull(progression, "TeamProgression is null.");
        if (advancement.getProgression(progression) > 0)
            return true;

        if (advancement instanceof AbstractMultiParentsAdvancement multiParent) {
            return multiParent.isAnyParentGranted(progression);
        }
        if (advancement instanceof BaseAdvancement base) {
            return base.getParent().isGranted(progression);
        }
        return false;
    };

    /**
     * Advancements with this Visibility will be visible once they are granted or any of their children is granted (Similar to Vanilla "hidden")
     */
    AdvancementVisibility HIDDEN = (advancement, progression) -> {
        Preconditions.checkNotNull(advancement, "Advancement is null.");
        Preconditions.checkNotNull(progression, "TeamProgression is null.");
        return advancement.getProgression(progression) > 0;
    };

    /**
     * Advancements with this Visibility will be visible once their parent or grandparent or any of their children is granted (Similar to Vanilla behavior)
     */
    AdvancementVisibility VANILLA = (advancement, progression) -> {
        Preconditions.checkNotNull(advancement, "Advancement is null.");
        Preconditions.checkNotNull(progression, "TeamProgression is null.");
        if (advancement.getProgression(progression) > 0)
            return true;

        if (advancement instanceof AbstractMultiParentsAdvancement multiParent) {
            return multiParent.isAnyGrandparentGranted(progression);
        } else if (advancement instanceof BaseAdvancement base) {
            Advancement parent = base.getParent();

            if (parent.isGranted(progression)) {
                return true;
            }
            if (parent instanceof AbstractMultiParentsAdvancement multiParent) {
                return multiParent.isAnyParentGranted(progression);
            } else if (parent instanceof BaseAdvancement baseA) {
                return baseA.getParent().isGranted(progression);
            }
            return false;
        }
        return false;
    };

    /**
     * Do not call this method directly, use {@link AdvancementVisibility} to get accurate visibility data
     *
     * @param advancement Advancement to check
     * @param progression Progression to check
     * @return true if advancement should be visible
     */
    boolean isVisible(@NotNull Advancement advancement, @NotNull TeamProgression progression);
}