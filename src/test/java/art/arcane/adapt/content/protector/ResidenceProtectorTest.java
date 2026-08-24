package art.arcane.adapt.content.protector;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ResidenceProtectorTest {
  private static final Path SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/protector/ResidenceProtector.java");

  @Test
  void deniedClaimPermissionIsReturnedSynchronously() throws IOException {
    String source = Files.readString(SOURCE);

    assertThat(source)
        .contains("return checkRegion(player, blockLocation, adaptation)"
            + " && checkPerm(player, blockLocation, Flags.destroy);")
        .contains("ClaimedResidence claimedResidence = residence.getResidenceManager().getByLoc(location);")
        .contains("claimedResidence.getPermissions().playerHas(player.getName(), flag, true);")
        .doesNotContain("J.a(")
        .doesNotContain("CompletableFuture");
  }

  @Test
  void unclaimedAndDisabledWorldsRemainAllowed() throws IOException {
    String source = Files.readString(SOURCE);

    assertThat(source)
        .contains("if (residence.isDisabledWorld(location.getWorld()))")
        .contains("return claimedResidence == null")
        .contains("FlagPermissions.addFlag(\"use-adaptations\");");
  }
}
