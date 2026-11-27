package art.arcane.adapt.engine.advancement;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.MutationMessages;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

/**
 * The one-liner under a discovery or unlock toast. The full description stays on the skill page and in the advancement
 * tab; the toast only says hello.
 */
public final class ToastQuips {
  public static final List<TextKey> UPGRADE = List.of(MutationMessages.UPGRADE_QUIP_1, MutationMessages.UPGRADE_QUIP_2);
  public static final List<TextKey> MUTATED = List.of(MutationMessages.MUTATED_QUIP_1, MutationMessages.MUTATED_QUIP_2);
  public static final List<TextKey> SIGNATURE = List.of(MutationMessages.SIGNATURE_QUIP_1, MutationMessages.SIGNATURE_QUIP_2);

  private ToastQuips() {
  }

  /**
   * The quip for learning {@code adaptation}: a signature's, an upgrade's, a mutated skill's own, or one of its skill's
   * unlock quips, picked by the adaptation's id so the same adaptation always reads the same. Null without a skill.
   */
  public static TextKey unlocked(Adaptation<?> adaptation) {
    if (adaptation instanceof Signature<?>) {
      return pick(SIGNATURE, adaptation.getId().toString());
    }
    if (adaptation instanceof SkillUpgrade<?>) {
      return pick(UPGRADE, adaptation.getId().toString());
    }
    Skill<?> skill = adaptation.getSkill();
    if (skill instanceof MutatedSkill) {
      return pick(MUTATED, adaptation.getId().toString());
    }
    return null;
  }

  /** The unlock quip for {@code adaptation} in the current language, empty without one. */
  public static String unlockLine(Adaptation<?> adaptation) {
    TextKey quip = unlocked(adaptation);
    if (quip != null) {
      return AdaptLanguage.text(quip);
    }
    List<String> quips = SkillHost.of(adaptation.getSkill()).unlockQuips();
    return quips.isEmpty() ? "" : quips.get(Math.floorMod(adaptation.getId().toString().hashCode(), quips.size()));
  }

  private static TextKey pick(List<TextKey> quips, String id) {
    return quips.get(Math.floorMod(id.hashCode(), quips.size()));
  }
}
