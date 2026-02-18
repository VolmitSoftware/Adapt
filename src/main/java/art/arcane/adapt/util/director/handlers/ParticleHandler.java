package art.arcane.adapt.util.director.handlers;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import org.bukkit.Particle;

public class ParticleHandler implements DirectorParameterHandler<Particle> {
  @Override
  public KList<Particle> getPossibilities() {
    return new KList<>(Particle.values());
  }

  @Override
  public String toString(Particle particle) {
    return particle.name();
  }

  @Override
  public Particle parse(String in, boolean force) throws DirectorParsingException {
    try {
      return Particle.valueOf(in);
    } catch (IllegalArgumentException e) {
      throw new DirectorParsingException("Invalid particle: " + in);
    }
  }

  @Override
  public boolean supports(Class<?> type) {
    return type.equals(Particle.class);
  }
}
