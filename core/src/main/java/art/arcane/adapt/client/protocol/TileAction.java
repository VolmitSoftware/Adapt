package art.arcane.adapt.client.protocol;

import art.arcane.adapt.platform.menu.ClickKind;

import java.util.Objects;

/** A click the tile answers to and what it does; an empty label means the server did not name it. */
public record TileAction(ClickKind click, String label) {
  public TileAction {
    Objects.requireNonNull(click, "click");
    Objects.requireNonNull(label, "label");
  }
}
