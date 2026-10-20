package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.api.golem.GolemRuneType;
import org.bukkit.Color;
import org.bukkit.Particle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

record InfusionTint(List<Particle.DustOptions> dust, List<Particle.DustOptions> trail, Particle accent) {
  private static final float DUST_SIZE = 1.3F;
  private static final float TRAIL_SIZE = 0.8F;
  private static final InfusionTint SWIFTNESS = tint(Particle.CLOUD, 0xA8F0FF);
  private static final InfusionTint MIGHT = tint(Particle.CRIT, 0xA4A7AE);
  private static final InfusionTint EMBER = tint(Particle.FLAME, 0xFF7A1A);
  private static final InfusionTint TIDE = tint(Particle.BUBBLE_POP, 0x1E50E6);
  private static final InfusionTint ORDER = tint(Particle.WAX_ON, 0xFFC830);
  private static final InfusionTint ENTROPY = tint(Particle.WITCH, 0xA040F0);
  private static final InfusionTint BULWARK = tint(Particle.REVERSE_PORTAL, 0x4A1E78);
  private static final InfusionTint MASTERWORK_HEART = tint(Particle.END_ROD, 0xFFF0B4, 0x8CF0FF, 0xFFA0F0, 0xA8FFC0);

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

  Particle.DustOptions dust(int index) {
    return dust.get(Math.floorMod(index, dust.size()));
  }

  Particle.DustOptions trail(int index) {
    return trail.get(Math.floorMod(index, trail.size()));
  }

  Color color() {
    return dust.getFirst().getColor();
  }

  private static InfusionTint tint(Particle accent, int... colors) {
    List<Particle.DustOptions> dust = new ArrayList<>(colors.length);
    List<Particle.DustOptions> trail = new ArrayList<>(colors.length);
    for (int rgb : colors) {
      Color color = Color.fromRGB(rgb);
      dust.add(new Particle.DustOptions(color, DUST_SIZE));
      trail.add(new Particle.DustOptions(color, TRAIL_SIZE));
    }
    return new InfusionTint(dust, trail, accent);
  }
}
