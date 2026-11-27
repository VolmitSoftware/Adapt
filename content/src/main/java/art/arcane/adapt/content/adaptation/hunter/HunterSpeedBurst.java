package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.api.ability.VelocityBurst;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.hud.HudGlyph;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Vec3;
import java.util.Map;
import java.util.UUID;

/** The velocity burst Hunter's Speed grants when struck, registered once per owning adaptation. */
public final class HunterSpeedBurst {
  private final Adaptation<?> owner;
  private final Map<UUID, Long> burstUntil;
  private final VelocityBurst.Client bursts;

  public HunterSpeedBurst(Adaptation<?> owner, Map<UUID, Long> burstUntil) {
    this.owner = owner;
    this.burstUntil = burstUntil;
    this.bursts = VelocityBurst.register(owner, new BurstFeedback());
  }

  public boolean grant(PlayerRef p, int amplifier, int durationTicks, boolean overlap, VelocityBurst.Profile profile) {
    VelocityBurst.StartResult result = bursts.start(p,
        VelocityBurst.Request.of(amplifier, durationTicks, profile).overlapping(overlap));
    UUID id = p.uuid();
    if (result.accepted() && HudStatus.capable(id)) {
      long now = System.currentTimeMillis();
      Long until = burstUntil.get(id);
      long base = result == VelocityBurst.StartResult.EXTENDED && until != null && until > now ? until : now;
      long endsAt = base + (durationTicks * 50L);
      burstUntil.put(id, endsAt);
      HudStatus.duration(id, owner, now, endsAt - now);
    }
    return result.accepted();
  }

  public void stop(PlayerRef p) {
    bursts.stop(p);
  }

  public void unregister() {
    bursts.unregister();
  }

  private final class BurstFeedback implements VelocityBurst.Feedback {
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
      HudStatus.clear(ref.uuid(), owner, HudGlyph.DURATION);
      HunterSpeed.endedFx(owner, ref);
    }
  }
}
