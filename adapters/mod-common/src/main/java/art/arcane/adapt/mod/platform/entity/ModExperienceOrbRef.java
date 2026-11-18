package art.arcane.adapt.mod.platform.entity;

import art.arcane.adapt.mod.platform.mixin.ExperienceOrbAccess;
import art.arcane.adapt.platform.entity.ExperienceOrbRef;
import net.minecraft.world.entity.ExperienceOrb;

public class ModExperienceOrbRef extends ModEntityRef implements ExperienceOrbRef {
    public ModExperienceOrbRef(ExperienceOrb orb) {
        super(orb);
    }

    public ExperienceOrb orb() {
        return (ExperienceOrb) handle();
    }

    @Override
    public int experience() {
        return orb().getValue();
    }

    @Override
    public void setExperience(int experience) {
        ((ExperienceOrbAccess) orb()).adapt$setValue(experience);
    }

    @Override
    public int count() {
        return ((ExperienceOrbAccess) orb()).adapt$count();
    }

    @Override
    public void setCount(int count) {
        ((ExperienceOrbAccess) orb()).adapt$setCount(Math.max(1, count));
    }
}
