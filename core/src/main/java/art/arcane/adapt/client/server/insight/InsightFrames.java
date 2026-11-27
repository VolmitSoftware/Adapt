package art.arcane.adapt.client.server.insight;

import art.arcane.adapt.api.insight.InsightFeed;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.server.hud.ClientHud;
import art.arcane.adapt.client.server.hud.HudLink;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.api.insight.InsightEffect;
import art.arcane.adapt.api.insight.InsightMark;
import art.arcane.adapt.api.insight.InsightStat;
import art.arcane.adapt.api.insight.InsightView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Insight's target and marks for viewers whose client negotiated {@link ClientProtocol#INSIGHT}. Sends the whole target
 * only when it is new or changed shape, a vitals frame when only health moved or a second passed (the client drops a
 * target it has not heard of for a while), and marks when they change by more than a twentieth of health.
 */
public final class InsightFrames implements InsightFeed {
  static final long KEEPALIVE_MILLIS = 1000L;
  private static final double MARK_STEP = 20D;
  private static final HudLink CLIENT_HUD = new HudLink() {
    @Override
    public boolean capable(UUID playerId, int capabilityBit) {
      return ClientHud.capable(playerId, capabilityBit);
    }

    @Override
    public boolean send(UUID playerId, ServerFrame frame) {
      return ClientHud.send(playerId, ClientProtocol.INSIGHT, frame);
    }
  };

  private final HudLink link;
  private final LongSupplier clock;
  private final Map<UUID, Sent> sent = new ConcurrentHashMap<>();

  public InsightFrames(HudLink link, LongSupplier clock) {
    this.link = Objects.requireNonNull(link, "link");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Frames through the installed client session. */
  public static InsightFrames standard() {
    return new InsightFrames(CLIENT_HUD, System::currentTimeMillis);
  }

  @Override
  public boolean capable(UUID viewerId) {
    return link.capable(viewerId, ClientProtocol.INSIGHT);
  }

  /** Brings the viewer's client up to date; false when the viewer has no Insight client or the send failed. */
  @Override
  public boolean show(UUID viewerId, InsightView view, List<InsightMark> marks) {
    Objects.requireNonNull(view, "view");
    if (marks.size() > MAX_MARKS) {
      throw new IllegalArgumentException("marks holds " + marks.size() + " entries, more than " + MAX_MARKS);
    }
    if (!capable(viewerId)) {
      sent.remove(viewerId);
      return false;
    }
    long now = clock.getAsLong();
    ServerFrame.InsightTarget target = target(view);
    List<ServerFrame.InsightMarks.Mark> rounded = round(marks);
    Sent previous = sent.get(viewerId);
    boolean delivered = true;
    boolean targetSent = false;
    if (previous == null || !previous.target().sameShape(target)) {
      delivered = link.send(viewerId, target);
      targetSent = true;
    } else if (Double.compare(previous.target().health(), target.health()) != 0
        || Double.compare(previous.target().absorption(), target.absorption()) != 0
        || now - previous.sentAt() >= KEEPALIVE_MILLIS) {
      delivered = link.send(viewerId, new ServerFrame.InsightVitals(target.entityId(), target.health(),
          target.absorption()));
      targetSent = true;
    }
    List<ServerFrame.InsightMarks.Mark> previousMarks = previous == null ? List.of() : previous.marks();
    if (delivered && !rounded.equals(previousMarks)) {
      delivered = link.send(viewerId, new ServerFrame.InsightMarks(rounded));
    }
    if (!delivered) {
      sent.remove(viewerId);
      return false;
    }
    long sentAt = targetSent || previous == null ? now : previous.sentAt();
    sent.put(viewerId, new Sent(target, rounded, sentAt));
    return true;
  }

  /** Tells the viewer's client the target is gone, when it was shown one. */
  @Override
  public void clear(UUID viewerId) {
    if (sent.remove(viewerId) != null) {
      link.send(viewerId, new ServerFrame.InsightClear());
    }
  }

  /** Forgets the viewer without sending, as when they leave. */
  @Override
  public void forget(UUID viewerId) {
    sent.remove(viewerId);
  }

  @Override
  public void forgetAll() {
    sent.clear();
  }

  static ServerFrame.InsightTarget target(InsightView view) {
    List<String> tags = new ArrayList<>(view.tags().size());
    for (Id tag : view.tags()) {
      tags.add(tag.toString());
    }
    List<ServerFrame.InsightTarget.Stat> stats = new ArrayList<>(view.stats().size());
    for (InsightStat stat : view.stats()) {
      stats.add(new ServerFrame.InsightTarget.Stat(stat.attribute().toString(), stat.value()));
    }
    List<ServerFrame.InsightTarget.Effect> effects = new ArrayList<>(view.effects().size());
    for (InsightEffect effect : view.effects()) {
      effects.add(new ServerFrame.InsightTarget.Effect(effect.effectId().toString(), effect.amplifier(),
          effect.durationTicks(), effect.harmful()));
    }
    return new ServerFrame.InsightTarget(view.entityId(), view.typeId().toString(), view.name(), view.kind(),
        view.health(), view.maxHealth(), view.absorption(), view.armor(), view.bond(), view.ownerName(), tags, stats,
        effects);
  }

  static List<ServerFrame.InsightMarks.Mark> round(List<InsightMark> marks) {
    List<ServerFrame.InsightMarks.Mark> rounded = new ArrayList<>(marks.size());
    for (InsightMark mark : marks) {
      rounded.add(new ServerFrame.InsightMarks.Mark(mark.entityId(), mark.kind(),
          Math.round(mark.health() * MARK_STEP) / MARK_STEP));
    }
    return List.copyOf(rounded);
  }

  private record Sent(ServerFrame.InsightTarget target, List<ServerFrame.InsightMarks.Mark> marks, long sentAt) {
  }
}
