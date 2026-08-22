package art.arcane.adapt.util.command;

import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.plugin.VolmitSender;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.Accessors;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

@Builder
@Data
@Accessors(chain = true, fluent = true)
public class Feedback {
  @Singular
  private List<SoundFeedback> sounds;
  @Singular
  private List<TextComponent> messages;

  public void send(CommandSender serverOrPlayer) {
    if (serverOrPlayer instanceof Player p) {
      for (SoundFeedback i : sounds) {
        i.play(p);
      }
    }

    LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();
    String prefix = C.GRAY + "[" + C.ADAPT + "Adapt" + C.GRAY + "] ";
    for (TextComponent i : messages) {
      serverOrPlayer.sendMessage(prefix + serializer.serialize(i));
    }
  }

  public void send(VolmitSender sender) {
    send(sender.getS());
  }
}
