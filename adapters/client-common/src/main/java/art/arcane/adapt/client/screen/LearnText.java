package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.view.LearnRules;
import art.arcane.adapt.client.view.CatalogIndex;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.RequirementView;

import java.util.ArrayList;
import java.util.List;

/** The words for a {@link LearnRules} answer. */
final class LearnText {
    private LearnText() {
    }

    /** One line that says why a learn is refused, or what it costs when it is allowed. */
    static String learnBlock(CatalogIndex catalog, LearnRules.LearnCheck check) {
        return switch (check.block()) {
            case NONE -> check.free() ? ScreenText.LEARN_FREE.text(ScreenText.roman(check.to()))
                    : ScreenText.LEARN_FOR.text(ScreenText.roman(check.to()), ScreenText.number(check.knowledgeCost()));
            case DISABLED -> ScreenText.DISABLED.text();
            case DENIED -> ScreenText.DENIED.text();
            case LEARNED -> ScreenText.LEARNED.text();
            case REQUIREMENTS -> requirement(catalog, check.missing().get(0));
            case POWER -> ScreenText.NEEDS_POWER.text(check.powerCost(), check.freePower());
            case KNOWLEDGE -> check.knowledge() <= 0L ? ScreenText.NO_KNOWLEDGE.text()
                    : ScreenText.NEEDS_KNOWLEDGE.text(ScreenText.number(check.knowledgeShort()));
        };
    }

    /** One line that says why an unlearn is refused; empty when it is allowed. */
    static String unlearnBlock(LearnRules.UnlearnCheck check) {
        return switch (check.block()) {
            case NONE, NOT_LEARNED -> "";
            case DISABLED -> ScreenText.DISABLED.text();
            case DENIED -> ScreenText.DENIED.text();
            case PERMANENT -> ScreenText.NO_UNLEARN.text();
            case DEPENDENTS -> ScreenText.NEEDED_BY.text(names(check.dependents()));
        };
    }

    /** What unlearning gives back: the refund, or that the server pays none. */
    static String refund(LearnRules.UnlearnCheck check) {
        return check.refunded() ? ScreenText.REFUND.text(ScreenText.number(check.refund())) : ScreenText.NO_REFUND.text();
    }

    /** What bringing the adaptation down to the check's level does, for a check that is allowed. */
    static String unlearnOffer(LearnRules.UnlearnCheck check) {
        String level = ScreenText.roman(check.to());
        return check.refunded() ? ScreenText.UNLEARN_REFUND.text(level, ScreenText.number(check.refund())) : ScreenText.UNLEARN_NO_REFUND.text(level);
    }

    static String requirement(CatalogIndex catalog, RequirementView requirement) {
        return ScreenText.REQUIRES.text(catalog.adaptationName(requirement.adaptationId()), ScreenText.roman(requirement.level()));
    }

    private static String names(List<AdaptationView> adaptations) {
        List<String> names = new ArrayList<>();
        for (AdaptationView adaptation : adaptations) {
            names.add(adaptation.name());
        }
        return String.join(", ", names);
    }
}
