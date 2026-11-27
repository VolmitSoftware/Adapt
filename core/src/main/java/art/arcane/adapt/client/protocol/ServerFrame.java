package art.arcane.adapt.client.protocol;

import art.arcane.adapt.engine.notification.HudSlot;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.api.insight.InsightBond;
import art.arcane.adapt.api.insight.InsightKind;
import art.arcane.adapt.client.view.KeptView;
import art.arcane.adapt.client.view.MutationSpreadView;
import art.arcane.adapt.client.view.SpendView;
import art.arcane.adapt.client.view.GroupLabelView;
import art.arcane.adapt.client.view.ShortNameView;
import art.arcane.adapt.client.view.XpGuideView;
import art.arcane.adapt.client.view.PlayerDelta;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.RecipeView;
import art.arcane.adapt.client.view.ToolView;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Frames the server sends on {@link ClientProtocol#CHANNEL}. */
public sealed interface ServerFrame {
  record Accept(int version, ServerInfo server) implements ServerFrame {
    public Accept {
      Objects.requireNonNull(server, "server");
    }
  }

  record Catalog(CatalogView catalog) implements ServerFrame {
    public Catalog {
      Objects.requireNonNull(catalog, "catalog");
    }
  }

  record Player(PlayerView player) implements ServerFrame {
    public Player {
      Objects.requireNonNull(player, "player");
    }
  }

  record PlayerUpdate(PlayerDelta delta) implements ServerFrame {
    public PlayerUpdate {
      Objects.requireNonNull(delta, "delta");
    }
  }

  record OpenRoute(Route route) implements ServerFrame {
    public OpenRoute {
      Objects.requireNonNull(route, "route");
    }
  }

  record PanelOpen(PanelView panel) implements ServerFrame {
    public PanelOpen {
      Objects.requireNonNull(panel, "panel");
    }
  }

  record PanelUpdate(PanelView panel) implements ServerFrame {
    public PanelUpdate {
      Objects.requireNonNull(panel, "panel");
    }
  }

  record PanelClose(int sessionId) implements ServerFrame {
  }

  /** Text segment for the HUD, mirroring core {@code HudSegment}; text is legacy section text. */
  record HudPublish(String purpose, int priority, long ttlMillis, List<HudSlot> slots, String text)
      implements ServerFrame {
    public HudPublish {
      Objects.requireNonNull(purpose, "purpose");
      Objects.requireNonNull(text, "text");
      slots = List.copyOf(slots);
    }
  }

  /** Removes the text segment or status with this purpose. */
  record HudClear(String purpose) implements ServerFrame {
    public HudClear {
      Objects.requireNonNull(purpose, "purpose");
    }
  }

  /** Drops every text segment and status the client holds, as when the server's HUD shuts down. */
  record HudClearAll() implements ServerFrame {
  }

  /**
   * Structured ability status drawn as a {@link HudGlyph}: timed from {@code startedAtMillis} (epoch) for
   * {@code durationMillis}, 0 for an open-ended state. {@code maxStacks} is 0 when the stacks have no cap.
   * {@code skillId} (empty when none) groups glyphs and picks their accent colour; {@code lit} is a toggle's state.
   */
  record HudStatus(String purpose, HudGlyph glyph, String skillId, IconRef icon, String label, long startedAtMillis,
                   long durationMillis, int stacks, int maxStacks, boolean lit) implements ServerFrame {
    public HudStatus {
      Objects.requireNonNull(purpose, "purpose");
      Objects.requireNonNull(glyph, "glyph");
      Objects.requireNonNull(skillId, "skillId");
      Objects.requireNonNull(icon, "icon");
      Objects.requireNonNull(label, "label");
    }
  }

  /** One xp award as it happens; {@code progress} is 0..1 toward the next level after the award. */
  record XpEarned(String skillId, double amount, double totalXp, int level, double progress) implements ServerFrame {
    public XpEarned {
      Objects.requireNonNull(skillId, "skillId");
    }
  }

  /**
   * A toast card. {@code skillId} names the skill whose look the card takes (a mutated skill's id for its merged look),
   * empty for none. A toast with a skill or of kind {@link ToastKind#SIGNATURE} needs {@link ClientProtocol#SKILL_TOASTS};
   * other clients get {@link #plain()}.
   */
  record Toast(ToastKind kind, IconRef icon, String title, String body, String skillId) implements ServerFrame {
    public Toast {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(icon, "icon");
      Objects.requireNonNull(title, "title");
      Objects.requireNonNull(body, "body");
      Objects.requireNonNull(skillId, "skillId");
    }

    /** This toast as a client without {@link ClientProtocol#SKILL_TOASTS} reads it: no skill, a signature as an unlock. */
    public Toast plain() {
      if (skillId.isEmpty() && kind != ToastKind.SIGNATURE) {
        return this;
      }
      return new Toast(kind == ToastKind.SIGNATURE ? ToastKind.UNLOCK : kind, icon, title, body, "");
    }
  }

  record CustomItems(List<ItemDescriptor> items) implements ServerFrame {
    public CustomItems {
      items = List.copyOf(items);
    }
  }

  record Feedback(FeedbackKind kind, String text) implements ServerFrame {
    public Feedback {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(text, "text");
    }
  }

  /**
   * Insight's focused target, sent whole when it is new or changes more than its vitals. The wire form of
   * {@link art.arcane.adapt.api.insight.InsightView}: ids travel as strings and {@code tags} lists what the client may
   * draw as markers.
   */
  record InsightTarget(int entityId, String typeId, String name, InsightKind kind, double health, double maxHealth,
                       double absorption, double armor, InsightBond bond, String ownerName, List<String> tags,
                       List<Stat> stats, List<Effect> effects) implements ServerFrame {
    public InsightTarget {
      Objects.requireNonNull(typeId, "typeId");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(bond, "bond");
      Objects.requireNonNull(ownerName, "ownerName");
      tags = List.copyOf(tags);
      stats = List.copyOf(stats);
      effects = List.copyOf(effects);
    }

    /** True when only health, absorption or effect durations differ, so a vitals frame is enough. */
    public boolean sameShape(InsightTarget other) {
      if (other == null || entityId != other.entityId || effects.size() != other.effects.size()) {
        return false;
      }
      for (int index = 0; index < effects.size(); index++) {
        Effect mine = effects.get(index);
        Effect theirs = other.effects.get(index);
        if (!mine.effectId().equals(theirs.effectId()) || mine.amplifier() != theirs.amplifier()
            || mine.harmful() != theirs.harmful()) {
          return false;
        }
      }
      return typeId.equals(other.typeId) && name.equals(other.name) && kind == other.kind
          && Double.compare(maxHealth, other.maxHealth) == 0 && Double.compare(armor, other.armor) == 0
          && bond == other.bond && ownerName.equals(other.ownerName) && tags.equals(other.tags)
          && stats.equals(other.stats);
    }

    /** One attribute chip, keyed by the attribute id. */
    public record Stat(String attribute, double value) {
      public Stat {
        Objects.requireNonNull(attribute, "attribute");
      }
    }

    /** One active effect; {@code durationTicks} is -1 for one that never runs out. */
    public record Effect(String effectId, int amplifier, int durationTicks, boolean harmful) {
      public Effect {
        Objects.requireNonNull(effectId, "effectId");
      }
    }
  }

  /** Health and absorption of the focused target, when nothing else about it changed. */
  record InsightVitals(int entityId, double health, double absorption) implements ServerFrame {
  }

  /** Nearby entities Insight tracks without focus; an empty list removes them. */
  record InsightMarks(List<Mark> marks) implements ServerFrame {
    public InsightMarks {
      marks = List.copyOf(marks);
    }

    /** One tracked entity; {@code health} is a fraction of its maximum. */
    public record Mark(int entityId, InsightKind kind, double health) {
      public Mark {
        Objects.requireNonNull(kind, "kind");
      }
    }
  }

  /** The skills whose page offers Mutate now; sent whole whenever it changes, only under {@link ClientProtocol#MUTATE}. */
  record MutateOffers(List<String> skillIds) implements ServerFrame {
    public MutateOffers {
      skillIds = List.copyOf(skillIds);
    }
  }

  /** Drops the focused target and every mark. */
  record InsightClear() implements ServerFrame {
  }

  /** The answer to a {@link ClientFrame.MutationRequest}; only under {@link ClientProtocol#MUTATE_SPREAD}. */
  record MutationSpread(MutationSpreadView spread) implements ServerFrame {
    public MutationSpread {
      Objects.requireNonNull(spread, "spread");
    }
  }

  /**
   * The originals every mutated skill keeps without learning, under the heading {@code title}; sent whole whenever it
   * changes, only under {@link ClientProtocol#KEPT}.
   */
  record MutationKept(String title, List<KeptView> kept) implements ServerFrame {
    public MutationKept {
      Objects.requireNonNull(title, "title");
      kept = List.copyOf(kept);
    }
  }

  /**
   * The mutation tools every adaptation in the catalog hands out, with their first recipe; sent whole whenever it
   * changes, only under {@link ClientProtocol#TOOLS}.
   */
  record MutationTools(List<ToolView> tools) implements ServerFrame {
    public MutationTools {
      tools = List.copyOf(tools);
    }
  }

  /**
   * How each skill in the catalog earns XP, under the heading {@code title}; sent whole with every new catalog, only under
   * {@link ClientProtocol#XP_GUIDE}.
   */
  record XpGuides(String title, List<XpGuideView> guides) implements ServerFrame {
    public XpGuides {
      Objects.requireNonNull(title, "title");
      guides = List.copyOf(guides);
    }
  }

  /**
   * What the player spends knowledge and power on, sent whole whenever it changes, and whether the server lets inactive
   * skills show on the skills page; only under {@link ClientProtocol#SPENDING}.
   */
  record Spending(boolean inactiveSkills, List<SpendView> spends) implements ServerFrame {
    public Spending {
      spends = List.copyOf(spends);
    }
  }

  /**
   * The names of each grouped skill's adaptation groups in the viewer's language, sent whole with every new catalog,
   * only under {@link ClientProtocol#GROUP_LABELS}.
   */
  record GroupLabels(List<GroupLabelView> skills) implements ServerFrame {
    public GroupLabels {
      skills = List.copyOf(skills);
    }
  }

  /**
   * The names adaptations show on their own skill's pages where they differ from the catalog's, which leave out the
   * skill they repeat; sent whole with every new catalog, only under {@link ClientProtocol#SHORT_NAMES}.
   */
  record ShortNames(List<ShortNameView> names) implements ServerFrame {
    public ShortNames {
      names = List.copyOf(names);
    }
  }

  /**
   * The recipes every adaptation in the catalog unlocks, sent whole whenever the catalog or the viewer's language
   * changes, only under {@link ClientProtocol#RECIPES}.
   */
  record AdaptationRecipes(List<RecipeView> recipes) implements ServerFrame {
    public AdaptationRecipes {
      recipes = List.copyOf(recipes);
    }
  }

  /**
   * Every file of art the server holds, as resource id and SHA-1 hex hash; sent after negotiation and again whenever the
   * server's art changes, only under {@link ClientProtocol#ASSETS}.
   */
  record AssetManifest(List<AssetRef> assets) implements ServerFrame {
    public AssetManifest {
      assets = List.copyOf(assets);
    }
  }

  /** One file a client asked for with {@link ClientFrame.AssetRequest}; only under {@link ClientProtocol#ASSETS}. */
  record Asset(String resource, String hash, byte[] data) implements ServerFrame {
    public Asset {
      Objects.requireNonNull(resource, "resource");
      Objects.requireNonNull(hash, "hash");
      Objects.requireNonNull(data, "data");
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Asset that && resource.equals(that.resource) && hash.equals(that.hash)
          && Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
      return Objects.hash(resource, hash, Arrays.hashCode(data));
    }

    @Override
    public String toString() {
      return "Asset[" + resource + ", " + hash + ", " + data.length + " bytes]";
    }
  }
}
