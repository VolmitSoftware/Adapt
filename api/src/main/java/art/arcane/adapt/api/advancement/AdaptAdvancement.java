package art.arcane.adapt.api.advancement;

import art.arcane.adapt.api.asset.Icon;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An advancement a skill or adaptation adds to its own advancement tree, with any children below it. Granting it
 * shows the vanilla toast, or Adapt's themed toast on a native client.
 *
 * <p>The key is unique within the addon and becomes the advancement {@code adapt:content/<namespace>/<key>}, so two
 * addons may use the same key; built-in content keeps {@code adapt:<key>}. A title or description left empty is
 * looked up as {@code <namespace>.advancement.<key>.title} and {@code .description}.
 *
 * <p>An advancement is granted by a {@link Milestone} that names its key, from code with {@code grantOnce}, or through
 * {@code PlayerProfile.grant(owner, key)}; all three take the key as written here.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class AdaptAdvancement {
  private final String key;
  private final Icon icon;
  private final Optional<Text> title;
  private final Optional<Text> description;
  private final AdvancementFrame frame;
  private final AdvancementVisibility visibility;
  private final boolean toast;
  private final boolean announce;
  private final List<AdaptAdvancement> children;

  private AdaptAdvancement(Builder builder) {
    this.key = Objects.requireNonNull(builder.key, "An advancement needs a key");
    if (!key.matches("[a-z0-9][a-z0-9_/-]*")) {
      throw new IllegalArgumentException("Invalid advancement key: '" + key + "'");
    }
    this.icon = builder.icon;
    this.title = Optional.ofNullable(builder.title);
    this.description = Optional.ofNullable(builder.description);
    this.frame = builder.frame;
    this.visibility = builder.visibility;
    this.toast = builder.toast;
    this.announce = builder.announce;
    this.children = List.copyOf(builder.children);
  }

  /** Starts an advancement with an emerald icon, a task frame and vanilla visibility, no toast and no announcement. */
  public static Builder builder() {
    return new Builder();
  }

  /** The key as written, lower case letters, digits, {@code _}, {@code /} and {@code -}. */
  public String key() {
    return key;
  }

  /** The icon shown on the advancement screen. */
  public Icon icon() {
    return icon;
  }

  /** The title; empty means the message key {@code <namespace>.advancement.<key>.title}. */
  public Optional<Text> title() {
    return title;
  }

  /** The description; empty means the message key {@code <namespace>.advancement.<key>.description}. */
  public Optional<Text> description() {
    return description;
  }

  /** The frame drawn around the icon. */
  public AdvancementFrame frame() {
    return frame;
  }

  /** When the advancement shows before it is granted. */
  public AdvancementVisibility visibility() {
    return visibility;
  }

  /** Whether the vanilla client shows a toast when it is granted. */
  public boolean toast() {
    return toast;
  }

  /** Whether granting it is announced in chat. */
  public boolean announce() {
    return announce;
  }

  /** The advancements directly below this one, in the order they were added. */
  public List<AdaptAdvancement> children() {
    return children;
  }

  /** Builds an {@link AdaptAdvancement}; only the key is required. */
  public static final class Builder {
    private String key;
    private Icon icon = Icon.item(Id.minecraft("emerald"));
    private Text title;
    private Text description;
    private AdvancementFrame frame = AdvancementFrame.TASK;
    private AdvancementVisibility visibility = AdvancementVisibility.VANILLA;
    private boolean toast;
    private boolean announce;
    private final List<AdaptAdvancement> children = new ArrayList<>();

    private Builder() {
    }

    /**
     * The key, unique within the addon, such as {@code chef/first_meal}. {@link #build()} throws when it is missing
     * or holds anything but lower case letters, digits, {@code _}, {@code /} and {@code -}.
     */
    public Builder key(String key) {
      this.key = key;
      return this;
    }

    /** The advancement screen and its toasts draw only the icon's item; its sprite and models are not used. */
    public Builder icon(Icon icon) {
      this.icon = Objects.requireNonNull(icon, "icon");
      return this;
    }

    /** Uses the item with id {@code item} as the icon. */
    public Builder icon(Id item) {
      return icon(Icon.item(item));
    }

    /** The title; null falls back to the message key {@code <namespace>.advancement.<key>.title}. */
    public Builder title(Text title) {
      this.title = title;
      return this;
    }

    /** The description; null falls back to the message key {@code <namespace>.advancement.<key>.description}. */
    public Builder description(Text description) {
      this.description = description;
      return this;
    }

    /** The frame drawn around the icon; {@link AdvancementFrame#TASK} by default. */
    public Builder frame(AdvancementFrame frame) {
      this.frame = Objects.requireNonNull(frame, "frame");
      return this;
    }

    /** When the advancement shows before it is granted; {@link AdvancementVisibility#VANILLA} by default. */
    public Builder visibility(AdvancementVisibility visibility) {
      this.visibility = Objects.requireNonNull(visibility, "visibility");
      return this;
    }

    /** Whether the vanilla client shows a toast when it is granted. Native clients always get Adapt's toast. */
    public Builder toast(boolean toast) {
      this.toast = toast;
      return this;
    }

    /** Whether granting it is announced in chat. */
    public Builder announce(boolean announce) {
      this.announce = announce;
      return this;
    }

    /** Adds an advancement below this one; call once per child. */
    public Builder child(AdaptAdvancement child) {
      children.add(Objects.requireNonNull(child, "child"));
      return this;
    }

    /**
     * The finished advancement.
     *
     * @throws NullPointerException     when no key was set
     * @throws IllegalArgumentException when the key is malformed
     */
    public AdaptAdvancement build() {
      return new AdaptAdvancement(this);
    }
  }
}
