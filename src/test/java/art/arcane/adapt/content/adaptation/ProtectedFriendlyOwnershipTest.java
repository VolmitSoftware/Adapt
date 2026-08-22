package art.arcane.adapt.content.adaptation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProtectedFriendlyOwnershipTest {
  @Test
  void targetOwnedCallbacksUseOwnerIdsForFriendlyChecks() throws Exception {
    String timeBomb = Files.readString(source(
        "chronos/ChronosTimeBomb.java"
    ));
    String snareLine = Files.readString(source(
        "hunter/HunterSnareLine.java"
    ));
    String plagueBearer = Files.readString(source(
        "tragoul/TragoulPlagueBearer.java"
    ));

    assertThat(timeBomb)
        .contains("isProtectedFriendlyOwned(field.owner(), entity)")
        .doesNotContain("isProtectedFriendly(owner, entity)");
    assertThat(snareLine)
        .contains("isProtectedFriendlyOwned(snare.owner, monster)")
        .doesNotContain("isProtectedFriendly(owner, monster)");
    assertThat(plagueBearer)
        .contains(
            "isProtectedFriendlyOwned(ownerId, mob)",
            "isProtectedFriendlyOwned(plan.ownerId(), target)"
        )
        .doesNotContain(
            "isProtectedFriendly(owner, mob)",
            "isProtectedFriendly(plan.owner(), target)"
        );
  }

  private static Path source(String relativePath) {
    return Path.of("src/main/java/art/arcane/adapt/content/adaptation").resolve(relativePath);
  }
}
