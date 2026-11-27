package art.arcane.adapt.content.golem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GolemTextNameTest {
  private static final List<String> NAMES = List.of("Steve's Iron Golem", "a<b>&=c \"q\" \\ é ☃", "Tab\there", "");

  @Test
  void boundNamesAreStoredExactlyAsAdventureWritesAPlainTextComponent() {
    for (String name : NAMES) {
      assertThat(GolemSnapshot.textName(name)).isEqualTo(GsonComponentSerializer.gson().serialize(Component.text(name)));
    }
  }

  @Test
  void aShownBoundNameReadsBackFromTheEntityAsTheStoredString() {
    for (String name : NAMES) {
      String stored = GolemSnapshot.textName(name);
      Component shown = GsonComponentSerializer.gson().deserialize(stored);

      assertThat(shown).isEqualTo(Component.text(name));
      assertThat(GsonComponentSerializer.gson().serialize(shown)).isEqualTo(stored);
    }
  }
}
