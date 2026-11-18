package art.arcane.adapt.util.director.handlers;

import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.fx.ParticleSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

public class ParticleHandler implements DirectorParameterHandler<ParticleSpec> {
  @Override
  public KList<ParticleSpec> getPossibilities() {
    KList<ParticleSpec> particles = new KList<>();
    for (Id id : RegistryNames.ids(RegistryKind.PARTICLE)) {
      particles.add(ParticleSpec.of(id));
    }
    return particles;
  }

  @Override
  public String toString(ParticleSpec particle) {
    return RegistryNames.name(particle.type());
  }

  @Override
  public ParticleSpec parse(String in, boolean force) throws DirectorParsingException {
    Id id = RegistryNames.parse(RegistryKind.PARTICLE, in);
    if (id == null) {
      throw new DirectorParsingException("Invalid particle: " + in);
    }
    return ParticleSpec.of(id);
  }

  @Override
  public boolean supports(Class<?> type) {
    return type.equals(ParticleSpec.class);
  }
}
