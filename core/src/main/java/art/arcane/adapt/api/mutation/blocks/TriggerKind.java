package art.arcane.adapt.api.mutation.blocks;

/**
 * What a skill's Trigger is: a fight action, a work action, or one that can be either. An either trigger uses the
 * fight form of a Strike only while a hostile is within 6 blocks.
 */
public enum TriggerKind {
  FIGHT,
  WORK,
  EITHER
}
