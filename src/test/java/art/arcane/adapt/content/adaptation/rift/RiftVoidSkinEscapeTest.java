package art.arcane.adapt.content.adaptation.rift;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RiftVoidSkinEscapeTest {
  private static final Path VOID_SKIN_SOURCE =
      Path.of("src/main/java/art/arcane/adapt/content/adaptation/rift/RiftVoidSkin.java");

  @Test
  void absorptionCountsTowardsSurvivingTheHit() {
    assertThat(RiftVoidSkin.survivableHealth(6D, 8D)).isEqualTo(14D);
    assertThat(RiftVoidSkin.survivableHealth(6D, 0D)).isEqualTo(6D);
    assertThat(RiftVoidSkin.survivableHealth(6D, -3D)).isEqualTo(6D);
    assertThat(RiftVoidSkin.survivableHealth(6D, Double.NaN)).isEqualTo(6D);
    assertThat(RiftVoidSkin.survivableHealth(Double.NaN, 8D)).isNaN();
  }

  @Test
  void aHitAbsorptionSoaksIsNotTreatedAsLethal() {
    double health = 6D;
    double absorption = 8D;

    assertThat(RiftVoidSkin.isLethalDamage(RiftVoidSkin.survivableHealth(health, absorption), 10D)).isFalse();
    assertThat(RiftVoidSkin.isLethalDamage(RiftVoidSkin.survivableHealth(health, absorption), 14D)).isTrue();
    assertThat(RiftVoidSkin.isLethalDamage(RiftVoidSkin.survivableHealth(health, 0D), 10D)).isTrue();
  }

  @Test
  void theEscapeCancelsTheKillBeforeTheTeleportCanFailToStart() throws IOException {
    String source = Files.readString(VOID_SKIN_SOURCE);
    int cancelIndex = source.indexOf("cancelLethalDamage(e, true);");
    int teleportIndex = source.indexOf("PaperCompat.teleportAsync(p, destination");

    assertThat(cancelIndex).isGreaterThan(0);
    assertThat(teleportIndex).isGreaterThan(0);
    assertThat(cancelIndex).isLessThan(teleportIndex);
    assertThat(source).contains("if (teleport == null) {");
  }

  @Test
  void aStrandedEscapeReleasesThePlayerInsteadOfLeavingThemPermanentlyImmune() throws IOException {
    String source = Files.readString(VOID_SKIN_SOURCE);

    assertThat(source).contains(
        "if (pendingEscapes.remove(operation.playerId(), operation)\n"
            + "          && operation.reservation().resolved().compareAndSet(false, true)) {");
  }

  @Test
  void everyDeclinedEscapeLeavesADiagnosticTrail() throws IOException {
    String source = Files.readString(VOID_SKIN_SOURCE);

    assertThat(source).contains(
        "ms cooldown left.",
        "no plain ender pearl in inventory.",
        "no safe spot and no usable world spawn.",
        "pearl cost was refused.");
  }

  @Test
  void lethalityStillUsesSettledDamageAgainstEffectiveHealth() throws IOException {
    String source = Files.readString(VOID_SKIN_SOURCE);

    assertThat(source)
        .contains("isLethalDamage(survivableHealth(p.getHealth(), p.getAbsorptionAmount()), e.getFinalDamage())");
  }
}
