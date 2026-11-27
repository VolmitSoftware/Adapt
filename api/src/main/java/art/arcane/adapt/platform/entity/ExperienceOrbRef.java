package art.arcane.adapt.platform.entity;

import org.jetbrains.annotations.ApiStatus;

/** An experience orb entity. Vanilla merges orbs: {@code count} orbs of {@code experience} points each. */
@ApiStatus.AvailableSince("1.0.0")
public interface ExperienceOrbRef extends EntityRef {
  /** Experience points in each merged orb. */
  int experience();

  /** Sets the experience points of each merged orb. */
  void setExperience(int experience);

  /** Number of orbs merged into this entity. */
  int count();

  /** Sets the number of merged orbs. */
  void setCount(int count);
}
