package art.arcane.adapt.gameplay.demo;

import java.util.function.BooleanSupplier;

interface PlateSurvey {
    String goal();

    PlateSearch.Verdict judge(PlateSearch.Candidate candidate, BooleanSupplier cancelled) throws InterruptedException;

    default boolean confirm(PlateSearch.Candidate candidate) {
        return true;
    }
}
