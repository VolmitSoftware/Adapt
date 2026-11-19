package art.arcane.adapt.mod.feature.advancement;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.critereon.ImpossibleTrigger;

final class AdvancementCriteria {
    private AdvancementCriteria() {
    }

    static Advancement.Builder impossible(Advancement.Builder builder, String name) {
        return builder.addCriterion(name, new ImpossibleTrigger.TriggerInstance());
    }
}
