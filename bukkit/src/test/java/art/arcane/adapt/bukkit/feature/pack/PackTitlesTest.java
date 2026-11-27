package art.arcane.adapt.bukkit.feature.pack;

import art.arcane.adapt.pack.surface.TitleRun;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PackTitlesTest {
  @Test
  void plainTitlesKeepTheTextAndDropPackGlyphs() {
    List<TitleRun> runs = List.of(
        new TitleRun("adapt:ui/space", "\uE00F\uE00C\uE000", 0xFFFFFF),
        new TitleRun("adapt:ui/band", "\uE012", 0xFFFFFF),
        new TitleRun("adapt:ui/text/13", "Configure Adapt", 0x404040));

    assertThat(PackTitles.plain(runs)).isEqualTo("Configure Adapt");
  }
}
