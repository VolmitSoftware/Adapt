package art.arcane.adapt.gameplay.demo;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProbeFormatTest {
    @Test
    void headerCarriesTheRefundGolemAndNearestFieldsTheStudioPolls() {
        assertThat(ProbeFormat.header(2, 1, 3.456D)).isEqualTo("ADAPT_QA DEMO PROBE refunds=2 golems=1 nearest=3.46");
    }

    @Test
    void headerNamesNoGolemWhenNoneIsLoaded() {
        assertThat(ProbeFormat.header(0, 0, null)).isEqualTo("ADAPT_QA DEMO PROBE refunds=0 golems=0 nearest=none");
    }

    @Test
    void nearReplyCarriesOnlyTheNearestField() {
        assertThat(ProbeFormat.near(2.5D)).isEqualTo("ADAPT_QA DEMO NEAR nearest=2.50");
        assertThat(ProbeFormat.near(null)).isEqualTo("ADAPT_QA DEMO NEAR nearest=none");
    }

    @Test
    void nearestIsTheSmallestDistanceOrNullWhenThereIsNone() {
        assertThat(ProbeFormat.nearest(List.of(6.1D, 2.4D, 9D))).isEqualTo(2.4D);
        assertThat(ProbeFormat.nearest(List.of())).isNull();
    }

    @Test
    void nearestUsesTheRootLocaleForTheDecimalPoint() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);
        try {
            assertThat(ProbeFormat.near(2.5D)).isEqualTo("ADAPT_QA DEMO NEAR nearest=2.50");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void levelsListOnlyTheAdaptationsTheActorHasLearned() {
        Map<String, Integer> levels = new LinkedHashMap<>();
        levels.put("golem-animus-binding", 5);
        levels.put("golem-copper-frame", 0);
        levels.put("golem-runic-augments", 3);

        assertThat(ProbeFormat.levels(levels)).isEqualTo(" | levels golem-animus-binding=5 golem-runic-augments=3");
    }

    @Test
    void levelsKeepTheMarkerWhenNothingIsLearned() {
        assertThat(ProbeFormat.levels(Map.of("golem-copper-frame", 0))).isEqualTo(" | levels");
    }

    @Test
    void golemLineUsesOneDecimalAndTheRootLocale() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);
        try {
            assertThat(ProbeFormat.golem("COPPER", "WORK", "[GATHERER]", -1279.44, 56D, 5116.86, false))
                .isEqualTo(" | golem COPPER WORK cores=[GATHERER] at=-1279.4,56.0,5116.9 dormant=false");
        } finally {
            Locale.setDefault(previous);
        }
    }
}
