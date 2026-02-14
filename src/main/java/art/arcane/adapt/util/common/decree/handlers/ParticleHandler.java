package art.arcane.adapt.util.decree.handlers;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.adapt.util.decree.DecreeParameterHandler;
import art.arcane.volmlib.util.decree.exceptions.DecreeParsingException;
import org.bukkit.Particle;

public class ParticleHandler implements DecreeParameterHandler<Particle> {
    @Override
    public KList<Particle> getPossibilities() {
        return new KList<>(Particle.values());
    }

    @Override
    public String toString(Particle particle) {
        return particle.name();
    }

    @Override
    public Particle parse(String in, boolean force) throws DecreeParsingException {
        try {
            return Particle.valueOf(in);
        } catch (IllegalArgumentException e) {
            throw new DecreeParsingException("Invalid particle: " + in);
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(Particle.class);
    }
}
