package art.arcane.adapt.api.content;

import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextKeys;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * One way to earn XP in a skill, declared by the skill with {@code xpSource("harvest")} or
 * {@code xpSource("harvest", guide)}.
 *
 * <p>Declaring sources is what fills the skill's "Earning XP" guide: each source contributes its {@link #guide()}
 * line, and sources whose guide texts are equal share one line. Every award made through a source carries its
 * {@linkplain #rewardKey() reward key}, which anti-farm bookkeeping and the XP event's source read.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class XpSource {
  private final SimpleSkill<?> skill;
  private final String key;
  private final Text guide;

  XpSource(SimpleSkill<?> skill, String key, @Nullable Text guide) {
    this.skill = skill;
    this.key = Objects.requireNonNull(key, "key");
    if (!key.matches("[a-z0-9][a-z0-9_-]*")) {
      throw new IllegalArgumentException("Invalid XP source key: '" + key + "'");
    }
    this.guide = guide != null ? guide : Text.key(TextKeys.xpSource(skill.getId(), key));
  }

  /** The source's name within its skill, such as {@code harvest}; matches {@code [a-z0-9][a-z0-9_-]*}. */
  public String key() {
    return key;
  }

  /** The skill that declared this source and receives its XP. */
  public Skill<?> skill() {
    return skill;
  }

  /**
   * The guide line that tells players how this source pays: the text given at declaration, or
   * {@code <skill key>.xp.<key>} when none was given.
   */
  public Text guide() {
    return guide;
  }

  /** {@code <skill id>:<key>}, such as {@code alchemist:alchemy:brewing}. */
  public String rewardKey() {
    return skill.getId() + ":" + key;
  }

  /**
   * {@code <skill id>:<key>:<detail>}, so awards of one source can be told apart by what paid them, such as the
   * mob killed. A null or blank detail gives {@link #rewardKey()}.
   */
  public String rewardKey(@Nullable String detail) {
    return detail == null || detail.isBlank() ? rewardKey() : rewardKey() + ":" + detail.trim();
  }

  /** Grants XP at the player's position. */
  public void award(PlayerRef player, double amount) {
    skill.xp(player, null, amount, rewardKey());
  }

  /** Grants XP at the player's position under {@link #rewardKey(String)}. */
  public void award(PlayerRef player, double amount, @Nullable String detail) {
    skill.xp(player, null, amount, rewardKey(detail));
  }

  /** Grants XP earned at {@code at}. */
  public void award(PlayerRef player, Loc at, double amount) {
    skill.xp(player, at, amount, rewardKey());
  }

  /** Grants XP earned at {@code at} under {@link #rewardKey(String)}. */
  public void award(PlayerRef player, Loc at, double amount, @Nullable String detail) {
    skill.xp(player, at, amount, rewardKey(detail));
  }

  /** Grants XP without the ticker line, keeping the floating number at {@code at}. */
  public void awardSilent(PlayerRef player, Loc at, double amount) {
    skill.xpS(player, at, amount, rewardKey());
  }

  /** Grants XP with nothing shown. */
  public void awardSilent(PlayerRef player, double amount) {
    skill.xpSilent(player, amount, rewardKey());
  }

  /** Grants XP with nothing shown, under {@link #rewardKey(String)}. */
  public void awardSilent(PlayerRef player, double amount, @Nullable String detail) {
    skill.xpSilent(player, amount, rewardKey(detail));
  }
}
