package art.arcane.adapt.util.decree.handlers;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.adapt.util.decree.DecreeParameterHandler;
import art.arcane.volmlib.util.decree.exceptions.DecreeParsingException;
import org.bukkit.Sound;

public class SoundHandler implements DecreeParameterHandler<Sound> {
    @Override
    public KList<Sound> getPossibilities() {
        return new KList<>(Sound.values());
    }

    @Override
    public String toString(Sound sound) {
        return sound.name();
    }

    @Override
    public Sound parse(String in, boolean force) throws DecreeParsingException {
        try {
            return Sound.valueOf(in);
        } catch (IllegalArgumentException e) {
            throw new DecreeParsingException("Invalid sound: " + in);
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(Sound.class);
    }
}
