package art.arcane.adapt.api.internal;

import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

/** Resolves {@link Text} to a string in a language. */
@ApiStatus.Internal
public interface TextRuntime {
  /** In the language of the player being rendered for, or the server's outside a render. */
  String resolve(Text text);

  String resolve(Text text, PlayerRef viewer);

  String resolve(Text text, String locale);

  <T> T renderFor(@Nullable UUID viewer, Supplier<T> action);

  String markupLegacy(String markup);

  String markupEscape(String text);

  String markupFromJson(String json);
}
