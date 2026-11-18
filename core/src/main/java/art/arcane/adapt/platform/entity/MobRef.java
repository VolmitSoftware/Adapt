package art.arcane.adapt.platform.entity;

import art.arcane.adapt.platform.entity.ai.MobGoalsRef;
import art.arcane.adapt.platform.value.Loc;

/** Mob with AI: target, navigation and goals. Same rules as {@link EntityRef}. */
public interface MobRef extends LivingRef {
  LivingRef target();

  void setTarget(LivingRef target);

  boolean isAware();

  void setAware(boolean aware);

  /** Starts navigating to the location. Returns false when no path was found. */
  boolean pathTo(Loc destination, double speed);

  boolean hasPath();

  void stopPathing();

  void lookAt(Loc target);

  /** Performs this mob's own vanilla ranged attack at the target. False when the mob has no ranged attack. */
  boolean rangedAttack(LivingRef target);

  /** True for an iron golem a player built; false for every other mob. */
  boolean isPlayerCreated();

  /** Marks an iron golem as player-built (it then never attacks players); no-op for other mobs. */
  void setPlayerCreated(boolean created);

  /** Copper golem weathering stage, 0 unaffected to 3 oxidized; 0 for other mobs. */
  int weatheringStage();

  /** True for a waxed copper golem. */
  boolean isWaxed();

  /** Restores a copper golem's weathering stage (clamped 0 to 3) and, when {@code waxed}, its wax; no-op for other mobs. */
  void setWeathering(int stage, boolean waxed);

  /** Copper golem interaction state; IDLE for other mobs. */
  CopperGolemState copperGolemState();

  /** Sets the copper golem interaction state; no-op for other mobs. */
  void setCopperGolemState(CopperGolemState state);

  /** Whether an undead mob that burns in daylight (skeletons, zombies) does so; no-op for other mobs. */
  void setBurnsInDaylight(boolean burns);

  /** Goal and target selectors. Only valid while this handle is valid. */
  MobGoalsRef goals();
}
