package art.arcane.adapt.mod.feature.advancement;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.advancements.triggers.ImpossibleTrigger;

final class AdvancementCriteria {
    private AdvancementCriteria() {
    }

    static Advancement.Builder impossible(Advancement.Builder builder, String name) {
        return builder.addCriterion(name, CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance()));
    }
}
