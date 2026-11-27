package art.arcane.adapt.api.insight;

import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The entity the viewer's Insight focuses, as the Adapt client draws it. Built with {@link #builder}; the limits below
 * are checked when the view is built, naming the field that broke one.
 *
 * <pre>{@code
 * InsightView view = InsightView.builder(wolf.entityId(), Id.minecraft("wolf"), InsightKind.PASSIVE)
 *     .health(wolf.health(), wolf.maxHealth())
 *     .bond(InsightBond.YOURS, "")
 *     .stat(InsightStat.of(Attributes.MOVEMENT_SPEED, 0.3D))
 *     .build();
 * }</pre>
 */
@ApiStatus.Experimental
public final class InsightView {
  /** The most effects one view carries. */
  public static final int MAX_EFFECTS = 32;
  /** The most stats one view carries. */
  public static final int MAX_STATS = 16;
  /** The most tags one view carries. */
  public static final int MAX_TAGS = 16;
  /** The longest name, in UTF-8 bytes. */
  public static final int MAX_NAME_BYTES = 8192;
  /** The longest owner name, in UTF-8 bytes. */
  public static final int MAX_OWNER_NAME_BYTES = 256;

  private final int entityId;
  private final Id typeId;
  private final String name;
  private final InsightKind kind;
  private final double health;
  private final double maxHealth;
  private final double absorption;
  private final double armor;
  private final InsightBond bond;
  private final String ownerName;
  private final Set<Id> tags;
  private final List<InsightStat> stats;
  private final List<InsightEffect> effects;

  private InsightView(Builder builder) {
    entityId = builder.entityId;
    typeId = builder.type;
    name = bounded(builder.name, MAX_NAME_BYTES, "name");
    kind = builder.kind;
    health = amount(builder.health, "health");
    maxHealth = amount(builder.maxHealth, "maxHealth");
    absorption = amount(builder.absorption, "absorption");
    armor = amount(builder.armor, "armor");
    bond = builder.bond;
    ownerName = bounded(builder.ownerName, MAX_OWNER_NAME_BYTES, "ownerName");
    tags = Collections.unmodifiableSet(new LinkedHashSet<>(counted(builder.tags, MAX_TAGS, "tags")));
    stats = List.copyOf(counted(builder.stats, MAX_STATS, "stats"));
    effects = List.copyOf(counted(builder.effects, MAX_EFFECTS, "effects"));
  }

  /**
   * Starts a view of one entity.
   *
   * @param entityId the network id, so the client follows the entity it already tracks
   * @param type     the entity type, such as {@code minecraft:wolf}
   */
  public static Builder builder(int entityId, Id type, InsightKind kind) {
    return new Builder(entityId, type, kind);
  }

  /** The network id of the entity. */
  public int entityId() {
    return entityId;
  }

  /** The entity type, such as {@code minecraft:wolf}. */
  public Id typeId() {
    return typeId;
  }

  /** The custom or player name, empty for the type's own name. */
  public String name() {
    return name;
  }

  /** What the entity is. */
  public InsightKind kind() {
    return kind;
  }

  /** Current health. */
  public double health() {
    return health;
  }

  /** Maximum health. */
  public double maxHealth() {
    return maxHealth;
  }

  /** Absorption hearts. */
  public double absorption() {
    return absorption;
  }

  /** Armor points. */
  public double armor() {
    return armor;
  }

  /** Who owns the entity. */
  public InsightBond bond() {
    return bond;
  }

  /** The owner's name, empty when unknown or offline. */
  public String ownerName() {
    return ownerName;
  }

  /**
   * Markers a skill put on the entity, in the order added, such as {@code adapt:stable_hand} for Taming's Stable Hand. The Adapt client
   * draws the tags it knows and skips the others.
   */
  public Set<Id> tags() {
    return tags;
  }

  /** The attribute chips shown, at most {@link #MAX_STATS}. */
  public List<InsightStat> stats() {
    return stats;
  }

  /** The active effects shown, at most {@link #MAX_EFFECTS}. */
  public List<InsightEffect> effects() {
    return effects;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof InsightView that && entityId == that.entityId && typeId.equals(that.typeId)
        && name.equals(that.name) && kind == that.kind && Double.compare(health, that.health) == 0
        && Double.compare(maxHealth, that.maxHealth) == 0 && Double.compare(absorption, that.absorption) == 0
        && Double.compare(armor, that.armor) == 0 && bond == that.bond && ownerName.equals(that.ownerName)
        && tags.equals(that.tags) && stats.equals(that.stats) && effects.equals(that.effects);
  }

  @Override
  public int hashCode() {
    return Objects.hash(entityId, typeId, name, kind, health, maxHealth, absorption, armor, bond, ownerName, tags, stats,
        effects);
  }

  @Override
  public String toString() {
    return "InsightView[" + entityId + " " + typeId + " " + kind + " " + health + "/" + maxHealth + "]";
  }

  private static String bounded(String value, int maxBytes, String field) {
    Objects.requireNonNull(value, field);
    int bytes = value.getBytes(StandardCharsets.UTF_8).length;
    if (bytes > maxBytes) {
      throw new IllegalArgumentException(field + " is " + bytes + " UTF-8 bytes, more than " + maxBytes);
    }
    return value;
  }

  private static double amount(double value, String field) {
    if (!Double.isFinite(value) || value < 0D) {
      throw new IllegalArgumentException(field + " must be finite and not negative, was " + value);
    }
    return value;
  }

  private static <T> Collection<T> counted(Collection<T> values, int max, String field) {
    if (values.size() > max) {
      throw new IllegalArgumentException(field + " holds " + values.size() + " entries, more than " + max);
    }
    return values;
  }

  /** Collects a view; {@link #build} checks the limits. */
  public static final class Builder {
    private final int entityId;
    private final Id type;
    private final InsightKind kind;
    private String name = "";
    private double health;
    private double maxHealth;
    private double absorption;
    private double armor;
    private InsightBond bond = InsightBond.NONE;
    private String ownerName = "";
    private final Set<Id> tags = new LinkedHashSet<>();
    private final List<InsightStat> stats = new ArrayList<>();
    private final List<InsightEffect> effects = new ArrayList<>();

    private Builder(int entityId, Id type, InsightKind kind) {
      this.entityId = entityId;
      this.type = Objects.requireNonNull(type, "type");
      this.kind = Objects.requireNonNull(kind, "kind");
    }

    /** The custom or player name; empty, the default, shows the type's own name. */
    public Builder name(String value) {
      name = Objects.requireNonNull(value, "name");
      return this;
    }

    /** Current and maximum health, both finite and not negative. */
    public Builder health(double current, double maximum) {
      health = current;
      maxHealth = maximum;
      return this;
    }

    /** Absorption hearts, finite and not negative. */
    public Builder absorption(double value) {
      absorption = value;
      return this;
    }

    /** Armor points, finite and not negative. */
    public Builder armor(double value) {
      armor = value;
      return this;
    }

    /** Who owns the entity, and the owner's name or empty when unknown; the default is {@link InsightBond#NONE}. */
    public Builder bond(InsightBond value, String owner) {
      bond = Objects.requireNonNull(value, "bond");
      ownerName = Objects.requireNonNull(owner, "ownerName");
      return this;
    }

    /** Adds a tag; see {@link InsightView#tags()}. */
    public Builder tag(Id tag) {
      tags.add(Objects.requireNonNull(tag, "tag"));
      return this;
    }

    /** Adds an attribute chip. */
    public Builder stat(InsightStat stat) {
      stats.add(Objects.requireNonNull(stat, "stat"));
      return this;
    }

    /** Adds attribute chips in order. */
    public Builder stats(List<InsightStat> values) {
      for (InsightStat stat : values) {
        stat(stat);
      }
      return this;
    }

    /** Adds an active effect. */
    public Builder effect(InsightEffect effect) {
      effects.add(Objects.requireNonNull(effect, "effect"));
      return this;
    }

    /** Adds active effects in order. */
    public Builder effects(List<InsightEffect> values) {
      for (InsightEffect effect : values) {
        effect(effect);
      }
      return this;
    }

    /**
     * The view.
     *
     * @throws IllegalArgumentException naming the field, when a list or name is over its limit or an amount is
     *                                  negative or not finite
     */
    public InsightView build() {
      return new InsightView(this);
    }
  }
}
