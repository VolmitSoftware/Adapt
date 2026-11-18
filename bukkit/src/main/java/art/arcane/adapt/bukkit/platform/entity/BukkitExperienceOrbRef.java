package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.platform.entity.ExperienceOrbRef;
import art.arcane.adapt.util.common.compat.PaperCompat;
import org.bukkit.entity.ExperienceOrb;

public final class BukkitExperienceOrbRef extends BukkitEntityRef implements ExperienceOrbRef {
  public BukkitExperienceOrbRef(ExperienceOrb orb) {
    super(orb);
  }

  public ExperienceOrb orb() {
    return (ExperienceOrb) handle();
  }

  @Override
  public int experience() {
    return orb().getExperience();
  }

  @Override
  public void setExperience(int experience) {
    orb().setExperience(experience);
  }

  @Override
  public int count() {
    return PaperCompat.experienceOrbCount(orb());
  }

  @Override
  public void setCount(int count) {
    try {
      orb().setCount(count);
    } catch (NoSuchMethodError ignored) {
    }
  }
}
