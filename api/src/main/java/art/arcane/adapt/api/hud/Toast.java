package art.arcane.adapt.api.hud;

import art.arcane.adapt.api.asset.Icon;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.Optional;

/** A toast: a themed card on a native client, a vanilla advancement toast elsewhere. Start from {@link #of}. */
@ApiStatus.AvailableSince("1.0.0")
public final class Toast {
  private final Kind kind;
  private final Icon icon;
  private final Text title;
  private final Text body;
  private final Optional<Id> skill;

  private Toast(Kind kind, Icon icon, Text title, Text body, Optional<Id> skill) {
    this.kind = Objects.requireNonNull(kind, "kind");
    this.icon = Objects.requireNonNull(icon, "icon");
    this.title = Objects.requireNonNull(title, "title");
    this.body = Objects.requireNonNull(body, "body");
    this.skill = Objects.requireNonNull(skill, "skill");
  }

  /** A {@link Kind#GENERIC} toast without a theme. */
  public static Toast of(Icon icon, Text title, Text body) {
    return new Toast(Kind.GENERIC, icon, title, body, Optional.empty());
  }

  /** A copy of this toast with another kind. */
  public Toast kind(Kind kind) {
    return new Toast(kind, icon, title, body, skill);
  }

  /** A copy of this toast framed in the theme of {@code skill}. */
  public Toast themed(Id skill) {
    return new Toast(kind, icon, title, body, Optional.of(skill));
  }

  /** Which card look a native client uses. */
  public Kind kind() {
    return kind;
  }

  /** The icon on the card. */
  public Icon icon() {
    return icon;
  }

  /** The first line, resolved in the viewer's language. */
  public Text title() {
    return title;
  }

  /** The second line, resolved in the viewer's language. */
  public Text body() {
    return body;
  }

  /** The skill whose theme frames the card; empty for the plain look. */
  public Optional<Id> skill() {
    return skill;
  }

  /** Shows the toast to a player, in their language. */
  public void show(PlayerRef player) {
    Engine.bridge().hud().toast(player, this);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Toast toast && kind == toast.kind && icon.equals(toast.icon) && title.equals(toast.title)
        && body.equals(toast.body) && skill.equals(toast.skill);
  }

  @Override
  public int hashCode() {
    return Objects.hash(kind, icon, title, body, skill);
  }

  @Override
  public String toString() {
    return "Toast[kind=" + kind + ", icon=" + icon + ", title=" + title + ", body=" + body + ", skill=" + skill + "]";
  }

  /**
   * The card look a native client gives a toast. Each names the moment Adapt's own toasts use it for.
   * A {@code switch} over this enum needs a default branch: minor releases add constants.
   */
  public enum Kind {
    /** A skill reached a new level. */
    LEVEL_UP,
    /** Knowledge was gained. */
    KNOWLEDGE,
    /** An adaptation or mutation was unlocked. */
    UNLOCK,
    /** A skill advancement was earned. */
    DISCOVERY,
    /** Anything else; the look of {@link Toast#of}. */
    GENERIC,
    /** A challenge advancement was earned. */
    CHALLENGE,
    /** A signature adaptation was unlocked. */
    SIGNATURE
  }
}
