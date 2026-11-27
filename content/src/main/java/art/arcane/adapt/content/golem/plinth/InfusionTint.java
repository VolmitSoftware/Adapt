package art.arcane.adapt.content.golem.plinth;

import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.content.golem.GolemRuneType;
import art.arcane.adapt.platform.fx.ParticleData;
import art.arcane.adapt.platform.fx.ParticleSpec;
import art.arcane.adapt.platform.value.Rgb;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

record InfusionTint(List<ParticleData.Dust> dust, List<ParticleData.Dust> trail, ParticleSpec accent) {
  private static final float DUST_SIZE = 1.3F;
  private static final float TRAIL_SIZE = 0.8F;
  private static final InfusionTint SWIFTNESS = tint(Particles.CLOUD, 0xA8F0FF);
  private static final InfusionTint MIGHT = tint(Particles.CRIT, 0xA4A7AE);
  private static final InfusionTint EMBER = tint(Particles.FLAME, 0xFF7A1A);
  private static final InfusionTint TIDE = tint(Particles.BUBBLE_POP, 0x1E50E6);
  private static final InfusionTint ORDER = tint(Particles.WAX_ON, 0xFFC830);
  private static final InfusionTint ENTROPY = tint(Particles.WITCH, 0xA040F0);
  private static final InfusionTint BULWARK = tint(Particles.REVERSE_PORTAL, 0x4A1E78);
  private static final InfusionTint MASTERWORK_HEART = tint(Particles.END_ROD, 0xFFF0B4, 0x8CF0FF, 0xFFA0F0, 0xA8FFC0);

  InfusionTint {
    dust = List.copyOf(dust);
    trail = List.copyOf(trail);
    Objects.requireNonNull(accent);
    if (dust.isEmpty() || dust.size() != trail.size()) {
      throw new IllegalArgumentException("An infusion tint needs at least one dust colour and a trail dust for each");
    }
  }

  static InfusionTint of(Infusion infusion) {
    return infusion.runeAspect().map(InfusionTint::of).orElse(MASTERWORK_HEART);
  }

  static InfusionTint of(GolemRuneType aspect) {
    return switch (aspect) {
      case SWIFTNESS -> SWIFTNESS;
      case MIGHT -> MIGHT;
      case EMBER -> EMBER;
      case TIDE -> TIDE;
      case ORDER -> ORDER;
      case ENTROPY -> ENTROPY;
      case BULWARK -> BULWARK;
    };
  }

  ParticleData.Dust dust(int index) {
    return dust.get(Math.floorMod(index, dust.size()));
  }

  ParticleData.Dust trail(int index) {
    return trail.get(Math.floorMod(index, trail.size()));
  }

  Rgb color() {
    return dust.getFirst().color();
  }

  private static InfusionTint tint(ParticleSpec accent, int... colors) {
    List<ParticleData.Dust> dust = new ArrayList<>(colors.length);
    List<ParticleData.Dust> trail = new ArrayList<>(colors.length);
    for (int rgb : colors) {
      Rgb color = Rgb.of(rgb);
      dust.add(new ParticleData.Dust(color, DUST_SIZE));
      trail.add(new ParticleData.Dust(color, TRAIL_SIZE));
    }
    return new InfusionTint(dust, trail, accent);
  }
}
