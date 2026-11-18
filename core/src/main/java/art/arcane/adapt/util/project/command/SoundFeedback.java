package art.arcane.adapt.util.command;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.fx.SoundCategory;
import art.arcane.adapt.platform.value.Id;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

@Builder
@Data
@Accessors(chain = true, fluent = true)
public class SoundFeedback {
  private Id sound;
  @Builder.Default
  private float volume = 1f;
  @Builder.Default
  private float pitch = 1f;

  public void play(PlayerRef p) {
    p.playSound(p.location(), sound, SoundCategory.MASTER, volume, pitch);
  }
}
