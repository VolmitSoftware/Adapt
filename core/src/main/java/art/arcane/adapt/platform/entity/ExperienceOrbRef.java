package art.arcane.adapt.platform.entity;

/** An experience orb entity. Vanilla merges orbs: {@code count} orbs of {@code experience} points each. */
public interface ExperienceOrbRef extends EntityRef {
  int experience();

  void setExperience(int experience);

  int count();

  void setCount(int count);
}
