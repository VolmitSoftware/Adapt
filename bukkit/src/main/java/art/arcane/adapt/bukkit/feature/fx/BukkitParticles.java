package art.arcane.adapt.bukkit.feature.fx;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.bukkit.platform.BukkitResolve;
import art.arcane.adapt.platform.fx.ParticleData;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import org.bukkit.Color;
import org.bukkit.Particle;

/** Particle specs to Bukkit particles and data, with the same data defaults the plugin's FX emitter uses. */
final class BukkitParticles {
  private static final float DEFAULT_DUST_SIZE = 1.0F;

  private BukkitParticles() {
  }

  static Particle particle(Id type) {
    return BukkitResolve.particle(type);
  }

  /**
   * Data the server accepts for this particle. Data the server's particle type does not take (a colour or power for a
   * particle that has neither on an older Minecraft version) falls back to that particle's default.
   */
  static Object data(Particle particle, ParticleData data) {
    Object value = switch (data) {
      case ParticleData.Dust dust -> new Particle.DustOptions(color(dust.color()), dust.size());
      case ParticleData.DustTransition transition -> new Particle.DustTransition(color(transition.from()), color(transition.to()), transition.size());
      case ParticleData.Block block -> BukkitRefs.blockData(block.state());
      case ParticleData.Item item -> BukkitRefs.item(item.item());
      case ParticleData.Color color -> color(color.color());
      case ParticleData.Power power -> Float.valueOf(power.value());
      case ParticleData.None none -> null;
    };
    return value != null && particle.getDataType().isInstance(value) ? value : fallback(particle);
  }

  static Object fallback(Particle particle) {
    Class<?> type = particle.getDataType();
    if (Color.class.equals(type)) {
      return Color.WHITE;
    }
    if (Particle.DustOptions.class.equals(type)) {
      return new Particle.DustOptions(Color.WHITE, DEFAULT_DUST_SIZE);
    }
    if (Float.class.equals(type)) {
      return Float.valueOf(1.0F);
    }
    if (Particle.DustTransition.class.equals(type)) {
      return new Particle.DustTransition(Color.WHITE, Color.WHITE, DEFAULT_DUST_SIZE);
    }
    return null;
  }

  static Color color(Rgb rgb) {
    return Color.fromRGB(rgb.red(), rgb.green(), rgb.blue());
  }
}
