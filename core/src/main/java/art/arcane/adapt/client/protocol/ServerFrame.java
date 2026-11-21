package art.arcane.adapt.client.protocol;

import art.arcane.adapt.api.notification.HudSlot;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.InsightMark;
import art.arcane.adapt.client.view.InsightView;
import art.arcane.adapt.client.view.PlayerDelta;
import art.arcane.adapt.client.view.PlayerView;

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

  record Toast(ToastKind kind, IconRef icon, String title, String body) implements ServerFrame {
    public Toast {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(icon, "icon");
      Objects.requireNonNull(title, "title");
      Objects.requireNonNull(body, "body");
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

  /** Insight's focused target, sent whole when it is new or changes more than its vitals. */
  record InsightTarget(InsightView view) implements ServerFrame {
    public InsightTarget {
      Objects.requireNonNull(view, "view");
    }
  }

  /** Health and absorption of the focused target, when nothing else about it changed. */
  record InsightVitals(int entityId, double health, double absorption) implements ServerFrame {
  }

  /** Nearby entities Insight tracks without focus; an empty list removes them. */
  record InsightMarks(List<InsightMark> marks) implements ServerFrame {
    public InsightMarks {
      marks = List.copyOf(marks);
    }
  }

  /** Drops the focused target and every mark. */
  record InsightClear() implements ServerFrame {
  }
}
