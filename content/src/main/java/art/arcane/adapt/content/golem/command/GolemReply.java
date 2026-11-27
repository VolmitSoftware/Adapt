package art.arcane.adapt.content.golem.command;

import art.arcane.adapt.api.command.CommandCaller;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.fx.SoundCategory;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A command answer in Adapt's chat style: prefixed lines in the tone's colour, and the tone's sounds for players. */
record GolemReply(List<String> lines, Tone tone) {
  private static final String PREFIX = TextFormat.GRAY + "[" + TextFormat.DARK_RED + "Adapt" + TextFormat.GRAY + "] ";

  static GolemReply error(String message) {
    return new GolemReply(List.of(TextFormat.RED + message), Tone.ERROR);
  }

  static GolemReply success(String message) {
    return new GolemReply(List.of(TextFormat.GREEN + message), Tone.SUCCESS);
  }

  static GolemReply info(String message) {
    return new GolemReply(List.of(TextFormat.WHITE + message), Tone.INFO);
  }

  /** Several info lines sent together with no sound. */
  static GolemReply report(List<String> messages) {
    List<String> lines = new ArrayList<>(messages.size());
    for (String message : messages) {
      lines.add(TextFormat.WHITE + message);
    }
    return new GolemReply(List.copyOf(lines), Tone.SILENT);
  }

  /** Sends now; for a player on the player's own thread. */
  void send(CommandCaller caller) {
    Optional<PlayerRef> player = caller.player();
    if (player.isPresent()) {
      GolemCommand.onTargetThread(player.get(), () -> deliver(caller, player.get()));
      return;
    }
    deliver(caller, null);
  }

  private void deliver(CommandCaller caller, PlayerRef player) {
    if (player != null) {
      for (Sound sound : tone.sounds) {
        player.playSound(player.location(), sound.id(), SoundCategory.MASTER, sound.volume(), sound.pitch());
      }
    }
    for (String line : lines) {
      caller.send(Text.literal(PREFIX + line));
    }
  }

  enum Tone {
    ERROR(List.of(new Sound(Sounds.BLOCK_DEEPSLATE_BREAK, 1F, 0.5F))),
    SUCCESS(List.of(new Sound(Sounds.BLOCK_AMETHYST_BLOCK_PLACE, 1F, 1.5F), new Sound(Sounds.ITEM_ARMOR_EQUIP_ELYTRA, 1F, 1.1F))),
    INFO(List.of(new Sound(Sounds.ITEM_ARMOR_EQUIP_LEATHER, 1F, 1.1F))),
    SILENT(List.of());

    private final List<Sound> sounds;

    Tone(List<Sound> sounds) {
      this.sounds = sounds;
    }
  }

  private record Sound(Id id, float volume, float pitch) {
  }
}
