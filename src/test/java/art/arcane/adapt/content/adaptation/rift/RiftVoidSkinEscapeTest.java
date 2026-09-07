package art.arcane.adapt.content.adaptation.rift;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RiftVoidSkinEscapeTest {
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
}
