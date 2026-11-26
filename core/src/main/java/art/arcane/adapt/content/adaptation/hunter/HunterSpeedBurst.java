package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.VelocityBurstRuntime;
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Vec3;
import java.util.Map;
import java.util.UUID;

/** The velocity burst Hunter's Speed grants when struck, registered once per owning adaptation. */
public final class HunterSpeedBurst {
  private final Adaptation<?> owner;
  private final Map<UUID, Long> burstUntil;
  private final VelocityBurstRuntime.Client bursts;

  public HunterSpeedBurst(Adaptation<?> owner, Map<UUID, Long> burstUntil) {
    this.owner = owner;
    this.burstUntil = burstUntil;
    this.bursts = VelocityBurstRuntime.register(owner, new BurstFeedback());
  }

  public boolean grant(PlayerRef p, int amplifier, int durationTicks, boolean overlap, VelocityBurstRuntime.Profile profile) {
    VelocityBurstRuntime.StartResult result = bursts.start(p,
        new VelocityBurstRuntime.BurstRequest(amplifier, durationTicks, overlap, profile));
    UUID id = p.uuid();
    if (result.accepted() && AbilityGlyphs.capable(id)) {
      long now = System.currentTimeMillis();
      Long until = burstUntil.get(id);
      long base = result == VelocityBurstRuntime.StartResult.EXTENDED && until != null && until > now ? until : now;
      long endsAt = base + (durationTicks * 50L);
      burstUntil.put(id, endsAt);
      AbilityGlyphs.duration(id, owner, now, endsAt - now);
    }
    return result.accepted();
  }

  public void stop(PlayerRef p) {
    bursts.stop(p);
  }

  public void unregister() {
    bursts.unregister();
  }

  private final class BurstFeedback implements VelocityBurstRuntime.Feedback {
    @Override
    public void onStarted(PlayerRef ref) {
      HunterSpeed.startedFx(owner, ref);
    }

    @Override
    public void onTrail(PlayerRef ref, Vec3 horizontal, int updateIndex) {
      if ((updateIndex % 3) == 0) {
        HunterSpeed.trailFx(owner, ref, horizontal);
      }
    }

    @Override
    public void onBraked(PlayerRef ref) {
      HunterSpeed.brakedFx(owner, ref);
    }

    @Override
    public void onEnded(PlayerRef ref) {
      burstUntil.remove(ref.uuid());
      AbilityGlyphs.clear(ref.uuid(), owner, HudGlyph.DURATION);
      HunterSpeed.endedFx(owner, ref);
    }
  }
}
