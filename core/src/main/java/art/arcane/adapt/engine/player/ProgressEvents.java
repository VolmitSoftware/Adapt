package art.arcane.adapt.engine.player;

import art.arcane.adapt.api.event.AdaptationLearnEvent;
import art.arcane.adapt.api.event.AdaptationUnlearnEvent;
import art.arcane.adapt.api.event.KnowledgeEvent;
import art.arcane.adapt.api.event.SkillLevelUpEvent;
import art.arcane.adapt.api.event.SkillMutateEvent;
import art.arcane.adapt.api.event.SkillUnmutateEvent;
import art.arcane.adapt.api.event.SkillXpEvent;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.engine.mutation.PairKeys;
import art.arcane.adapt.api.xp.XpAward;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

/**
 * Builds and posts the progress events of {@code api.event}. Each engine sink that changes player state calls one of
 * these before it changes anything; a player of null (data with no online owner) changes without an event.
 */
public final class ProgressEvents {
  private ProgressEvents() {
  }

  /** The skill a saved line reports as: its id, or the lead of a mutated line. Null for a key that is neither. */
  public static Id lineSkill(String line) {
    if (line == null) {
      return null;
    }
    SkillPair mutation = PairKeys.parse(line);
    return mutation != null ? mutation.lead() : Id.tryParseAdapt(line);
  }

  /** The XP to grant after listeners: 0 when cancelled. */
  public static double xp(PlayerRef player, String line, double amount, String source, XpAward.Kind kind) {
    Id skill = lineSkill(line);
    if (player == null || skill == null || !PlayerApi.listening(SkillXpEvent.class)) {
      return amount;
    }
    SkillXpEvent event = PlayerApi.post(new SkillXpEvent(player, skill, amount, source, kind));
    return event.isCancelled() ? 0D : event.amount();
  }

  public static void levelUp(PlayerRef player, String line, int level, long knowledge) {
    Id skill = lineSkill(line);
    if (player != null && skill != null && PlayerApi.listening(SkillLevelUpEvent.class)) {
      PlayerApi.post(new SkillLevelUpEvent(player, skill, level, knowledge));
    }
  }

  /** The knowledge to add after listeners: 0 when cancelled. */
  public static long knowledge(PlayerRef player, String line, long amount) {
    Id skill = lineSkill(line);
    if (player == null || skill == null || !PlayerApi.listening(KnowledgeEvent.class)) {
      return amount;
    }
    KnowledgeEvent event = PlayerApi.post(new KnowledgeEvent(player, skill, amount));
    return event.isCancelled() ? 0L : event.amount();
  }

  /** False when a listener cancelled the learn. */
  public static boolean learn(PlayerRef player, Id adaptation, String line, int fromLevel, int level, long cost,
                              boolean forced) {
    Id skill = lineSkill(line);
    if (player == null || skill == null || !PlayerApi.listening(AdaptationLearnEvent.class)) {
      return true;
    }
    return !PlayerApi.post(new AdaptationLearnEvent(player, adaptation, skill, fromLevel, level, cost, forced))
        .isCancelled();
  }

  /** False when a listener cancelled the unlearn. */
  public static boolean unlearn(PlayerRef player, Id adaptation, String line, int fromLevel, int level, long refund,
                                boolean forced) {
    Id skill = lineSkill(line);
    if (player == null || skill == null || !PlayerApi.listening(AdaptationUnlearnEvent.class)) {
      return true;
    }
    return !PlayerApi.post(new AdaptationUnlearnEvent(player, adaptation, skill, fromLevel, level, refund, forced))
        .isCancelled();
  }

  /** False when a listener cancelled the merge. */
  public static boolean mutate(PlayerRef player, SkillPair mutation) {
    if (player == null || !PlayerApi.listening(SkillMutateEvent.class)) {
      return true;
    }
    return !PlayerApi.post(new SkillMutateEvent(player, mutation)).isCancelled();
  }

  /** False when a listener cancelled the split. */
  public static boolean unmutate(PlayerRef player, SkillPair mutation) {
    if (player == null || !PlayerApi.listening(SkillUnmutateEvent.class)) {
      return true;
    }
    return !PlayerApi.post(new SkillUnmutateEvent(player, mutation)).isCancelled();
  }

  public static XpAward.Kind kind(art.arcane.adapt.api.xp.XpAward.Kind kind) {
    return switch (kind) {
      case GAMEPLAY -> XpAward.Kind.GAMEPLAY;
      case CONSUMABLE -> XpAward.Kind.CONSUMABLE;
      case ADMINISTRATIVE -> XpAward.Kind.ADMINISTRATIVE;
    };
  }
}
