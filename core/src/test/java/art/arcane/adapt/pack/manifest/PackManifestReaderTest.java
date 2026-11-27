package art.arcane.adapt.pack.manifest;

import art.arcane.adapt.pack.PackSamples;
import art.arcane.adapt.client.view.IconSprites;
import art.arcane.adapt.pack.surface.PackSlot;
import art.arcane.adapt.pack.text.RowFont;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import art.arcane.adapt.pack.layout.PackLayout;
import art.arcane.adapt.pack.layout.PackLayoutReader;

import java.io.StringReader;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reads a schema 2 manifest the bake wrote for 26.3 (trimmed to two kits and a few icons). */
class PackManifestReaderTest {
  private final PackManifest manifest = PackSamples.manifest();

  @Test
  void spacesBecomeTheShiftFont() {
    assertThat(manifest.shift().font()).isEqualTo("adapt:ui/space");
    assertThat(manifest.shift().glyphs()).hasSize(22).containsEntry(1, Character.toString(57344))
        .containsEntry(-1024, Character.toString(57365));
  }

  @Test
  void textFontsBecomeRowsBelowTheTitleLine() {
    assertThat(manifest.title()).isEqualTo(new RowFont("minecraft:default", 6, false));
    assertThat(manifest.row(5)).isEqualTo(new RowFont("adapt:ui/text/m1", 5, true));
    assertThat(manifest.row(18).font()).isEqualTo("adapt:ui/text/12");
    assertThat(manifest.rows()).allMatch(RowFont::shifted);
  }

  @Test
  void pagesCarryTheirGlyphsAndDressingHosts() {
    PackManifest.PageArt single = manifest.page("adapt:agility", "single");
    assertThat(single.tiles()).hasSize(2);
    assertThat(single.tiles().get(0).font()).isEqualTo("adapt:page/agility/single");
    assertThat(single.tiles().get(0).x()).isEqualTo(-30);
    assertThat(single.hosts()).extracting(PackManifest.Host::slot)
        .containsExactly(PackSlot.top(22), PackSlot.top(31), PackSlot.top(40), PackSlot.lower(31));
    assertThat(single.hosts().get(0).art().model()).isEqualTo(Id.adapt("adapt_ui/page/agility/single/s22"));
    assertThat(manifest.page("adapt:agility", "fallback").hosts()).extracting(PackManifest.Host::slot).contains(PackSlot.top(46));
    assertThat(manifest.page("adapt:herbalism", "ledger-r3")).isEqualTo(manifest.page("adapt:tome", "ledger-r3"));
    assertThat(manifest.page("adapt:tome", "ledger-r3").tiles().get(0).x()).isEqualTo(-10);
    assertThat(manifest.page("adapt:tome", "missing")).isNull();
  }

  @Test
  void theLayoutsArtSlotsAreTheBakesHostSlots() {
    PackLayout layout = PackLayoutReader.builtIn();
    for (String screen : List.of("skill", "adaptation.levels", "adaptation.recipes", "adaptation.settings", "mutate")) {
      assertThat(layout.screen(screen, true).slots("art")).as(screen).isEqualTo(manifest.hostSlots().get("A"));
      assertThat(layout.screen(screen, false).slots("art")).as(screen).isEqualTo(manifest.hostSlots().get("Afallback"));
    }
    assertThat(layout.screen("skills", true).slots("art")).isEmpty();
  }

  @Test
  void hostSlotsPastTheChestArePlayerSlots() {
    assertThat(PackManifestReader.windowSlot(53)).isEqualTo(PackSlot.top(53));
    assertThat(PackManifestReader.windowSlot(54)).isEqualTo(PackSlot.lower(0));
    assertThat(PackManifestReader.windowSlot(89)).isEqualTo(PackSlot.lower(35));
  }

  @Test
  void artIconsAndKitsResolve() {
    assertThat(manifest.item("ctl/back")).isEqualTo(new PackManifest.ItemArt(Id.adapt("adapt_ui/art/ctl/back")));
    assertThat(manifest.sprite("adapt:gui/adaptation/agility-air-dash", false).model())
        .isEqualTo(Id.adapt("adapt_ui/gui/adaptation/agility-air-dash"));
    assertThat(manifest.sprite(IconSprites.adaptation(Id.adapt("agility/air_dash")), false))
        .isEqualTo(manifest.sprite("adapt:gui/adaptation/agility-air-dash", false));
    assertThat(manifest.sprite(IconSprites.skill(Id.adapt("agility")), true).model())
        .isEqualTo(Id.adapt("adapt_ui/gui/skill/agility_dim"));
    assertThat(manifest.sprite(IconSprites.stack(IconSprites.skill(Id.adapt("agility")),
        IconSprites.trim(Id.adapt("rift"))), false).model()).isEqualTo(Id.adapt("adapt_ui/gui/skill/agility"));
    assertThat(manifest.sprite(IconSprites.skill(Id.adapt("no_such_skill")), false)).isNull();
  }

  @Test
  void foreignNamespaceIconsResolveInTheirOwnNamespace() {
    assertThat(manifest.sprite(IconSprites.skill(Id.of("orbit", "orbit")), false).model()).isEqualTo(Id.of("orbit", "adapt_ui/gui/skill/orbit"));
    assertThat(manifest.sprite(IconSprites.skill(Id.of("orbit", "orbit")), true).model()).isEqualTo(Id.of("orbit", "adapt_ui/gui/skill/orbit"));
    assertThat(manifest.sprite(IconSprites.adaptation(Id.of("orbit", "orbit/drift")), false).model())
        .isEqualTo(Id.of("orbit", "adapt_ui/gui/adaptation/orbit/drift"));
    assertThat(manifest.sprite("orbit:gui/skill/orbit", false)).isEqualTo(manifest.sprite(IconSprites.skill(Id.of("orbit", "orbit")), false));
    assertThat(manifest.sprite(IconSprites.stack(IconSprites.tinted(IconSprites.skill(Id.of("orbit", "orbit")), 0x23406E),
        IconSprites.trim(Id.of("orbit", "orbit"))), false).model()).isEqualTo(Id.of("orbit", "adapt_ui/gui/skill/orbit"));
    assertThat(manifest.sprite(IconSprites.skill(Id.of("orbit", "agility")), false)).isNull();
    assertThat(manifest.sprite(IconSprites.skill(Id.adapt("orbit")), false)).isNull();
  }

  @Test
  void tooltipsResolve() {
    assertThat(manifest.tooltip("adapt:agility")).isEqualTo(new PackManifest.TooltipArt("adapt:agility", 0));
    assertThat(manifest.tooltip("adapt:ledger")).isEqualTo(new PackManifest.TooltipArt("adapt:tome", 0));
  }

  @Test
  void otherSchemasAreRefused() {
    assertThatThrownBy(() -> PackManifestReader.read(new StringReader("{\"schema\": 1}")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
