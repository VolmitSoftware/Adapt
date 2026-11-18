package art.arcane.adapt.util.command;

import art.arcane.adapt.command.CommandSource;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.common.format.C;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Optional;

@Builder
@Data
@Accessors(chain = true, fluent = true)
public class Feedback {
  @Singular
  private List<SoundFeedback> sounds;
  @Singular
  private List<String> messages;

  public void send(CommandSource serverOrPlayer) {
    Optional<PlayerRef> player = serverOrPlayer.player();
    if (player.isPresent()) {
      for (SoundFeedback i : sounds) {
        i.play(player.get());
      }
    }

    String prefix = C.GRAY + "[" + C.ADAPT + "Adapt" + C.GRAY + "] ";
    for (String i : messages) {
      serverOrPlayer.sendSection(prefix + i);
    }
  }
}
