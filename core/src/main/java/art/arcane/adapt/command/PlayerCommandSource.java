package art.arcane.adapt.command;

import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.Objects;
import java.util.Optional;

/** A player addressed by a command it did not issue, such as the target of a reset; messages go through Adapt markup. */
public record PlayerCommandSource(PlayerRef target) implements CommandSource {
  public PlayerCommandSource {
    Objects.requireNonNull(target, "target");
  }

  @Override
  public String name() {
    return target.name();
  }

  @Override
  public Optional<PlayerRef> player() {
    return Optional.of(target);
  }

  @Override
  public boolean hasPermission(String node) {
    return target.hasPermission(node);
  }

  @Override
  public boolean isOp() {
    return false;
  }

  @Override
  public void sendSection(String sectionText) {
    target.sendMessage(sectionText);
  }

  @Override
  public void sendLiteral(String plainText) {
    target.sendMessage(plainText.replace("\\", "\\\\").replace("<", "\\<"));
  }

  @Override
  public void sendMarkup(String markup) {
    target.sendMessage(markup);
  }
}
