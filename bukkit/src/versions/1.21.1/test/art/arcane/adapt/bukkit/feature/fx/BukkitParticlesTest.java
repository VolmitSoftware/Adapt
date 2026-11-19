package art.arcane.adapt.bukkit.feature.fx;

import art.arcane.adapt.platform.fx.ParticleData;
import art.arcane.adapt.platform.value.Rgb;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class BukkitParticlesTest {
  @Test
  void dataTheParticleDoesNotTakeOnThisVersionFallsBackToItsDefault() {
    assertNull(BukkitParticles.data(Particle.FLASH, new ParticleData.Color(new Rgb(255, 32, 32))));
    assertNull(BukkitParticles.data(Particle.FLASH, new ParticleData.Power(2.0F)));
    assertNull(BukkitParticles.data(Particle.FLASH, new ParticleData.None()));
  }

  @Test
  void dataTheParticleTakesIsKept() {
    Object data = BukkitParticles.data(Particle.DUST_COLOR_TRANSITION,
        new ParticleData.DustTransition(new Rgb(10, 20, 30), new Rgb(40, 50, 60), 2.0F));
    Particle.DustTransition transition = assertInstanceOf(Particle.DustTransition.class, data);
    assertEquals(Color.fromRGB(10, 20, 30), transition.getColor());
    assertEquals(Color.fromRGB(40, 50, 60), transition.getToColor());
    assertEquals(2.0F, transition.getSize());
  }
}
