package art.arcane.adapt.content.adaptation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EventOwnerThreadAuditTest {
  private static final Path FIELD_NOTES_SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/discovery/DiscoveryFieldNotes.java"
  );
  private static final Path INSIGHT_SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/discovery/DiscoveryInsight.java"
  );

  @Test
  void fieldNotesDeathHandlerDefersKillerStateToKillerOwnership() throws Exception {
    String source = Files.readString(FIELD_NOTES_SOURCE);
    String handler = method(
        source,
        "public void on(EntityDeathEvent e)",
        "public void on(EntityDamageByEntityEvent e)"
    );

    assertThat(handler)
        .contains("J.runEntity(killer, () -> recordKillOwned(killer, species, where))")
        .doesNotContain("hasActiveAdaptation(killer)", "getActiveLevel(killer)");
  }

  @Test
  void insightReadsTargetDetailsBeforeReturningToViewerOwnership() throws Exception {
    String source = Files.readString(INSIGHT_SOURCE);
    String handler = method(
        source,
        "private void inspectTargetOwned(",
        "private void acceptInspectionOwned("
    );

    assertThat(handler)
        .contains("buildInsightDetails(readStats(target))", "J.runEntity(owner, () -> acceptInspectionOwned")
        .doesNotContain("getActiveLevel(owner)", "owner.isOnline()", "gloss.update(");
  }

  private static String method(String source, String startMarker, String endMarker) {
    int start = source.indexOf(startMarker);
    int end = source.indexOf(endMarker, start);
    if (start < 0 || end < 0) {
      throw new IllegalArgumentException(
          "Missing method markers: " + startMarker + ", " + endMarker
      );
    }
    return source.substring(start, end);
  }
}
