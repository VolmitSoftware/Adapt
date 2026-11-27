package art.arcane.adapt.engine.asset;

import art.arcane.adapt.api.asset.AssetEntry;
import art.arcane.adapt.api.asset.AssetKind;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptAssetCatalogTest {
  private static final Id ALCHEMY = Id.of("alchemist", "alchemy");
  private static final byte[] ICON = MemoryBundle.png(16, 16, 0xFF336699);

  private final List<String> errors = new ArrayList<>();
  private final AdaptAssetCatalog catalog = new AdaptAssetCatalog(new SkillLooks() {
    @Override
    public List<Id> ids() {
      return List.of(ALCHEMY);
    }

    @Override
    public Optional<SkillLook> look(Id skill) {
      return skill.equals(ALCHEMY) ? Optional.of(new SkillLook(ALCHEMY, 0x7A3FD1, Id.minecraft("brewing_stand"))) : Optional.empty();
    }
  }, errors::add);

  @Test
  void theAdaptBundleComesFirstAndTheRestInInstallOrder() {
    catalog.install(new MemoryBundle("alchemist").file("textures/gui/skill/alchemy.png", ICON), "Alchemist");
    catalog.install(new MemoryBundle("forge_arts").file("textures/gui/skill/smithing.png", ICON), "ForgeArts");
    catalog.install(new MemoryBundle("adapt").file("textures/gui/skill/agility.png", ICON), "adapt");

    assertThat(catalog.bundles()).extracting(InstalledBundle::namespace).containsExactly("adapt", "alchemist", "forge_arts");
    assertThat(catalog.bundles()).extracting(InstalledBundle::addon).containsExactly("adapt", "Alchemist", "ForgeArts");
  }

  @Test
  void reinstallingANamespaceReplacesItInPlace() {
    catalog.install(new MemoryBundle("alchemist").file("textures/gui/skill/alchemy.png", ICON), "Alchemist");
    catalog.install(new MemoryBundle("forge_arts").file("textures/gui/skill/smithing.png", ICON), "ForgeArts");
    catalog.install(new MemoryBundle("alchemist").file("textures/gui/skill/brewing.png", ICON), "Alchemist");

    assertThat(catalog.bundles()).extracting(InstalledBundle::namespace).containsExactly("alchemist", "forge_arts");
    assertThat(catalog.sprite(Id.of("alchemist", "gui/skill/alchemy"))).isEmpty();
    assertThat(catalog.sprite(Id.of("alchemist", "gui/skill/brewing"))).isPresent();
  }

  @Test
  void entriesAndBundlesAreHashedWithSha1() {
    byte[] other = MemoryBundle.png(16, 16, 0xFF000000);
    InstalledBundle first = catalog.install(new MemoryBundle("alchemist").file("textures/gui/skill/alchemy.png", ICON), "A");

    assertThat(catalog.files().get("alchemist:textures/gui/skill/alchemy.png").hash()).isEqualTo(AssetHashes.sha1(ICON))
        .hasSize(40);
    assertThat(first.hash()).hasSize(40);
    InstalledBundle same = catalog.install(new MemoryBundle("alchemist").file("textures/gui/skill/alchemy.png", ICON), "A");
    InstalledBundle changed = catalog.install(new MemoryBundle("alchemist").file("textures/gui/skill/alchemy.png", other), "A");
    assertThat(same.hash()).isEqualTo(first.hash());
    assertThat(changed.hash()).isNotEqualTo(first.hash());
  }

  @Test
  void everyChangeRaisesTheRevisionAndTellsListenersUntilTheyUnsubscribe() {
    List<Long> seen = new ArrayList<>();
    AssetSubscription subscription = catalog.subscribe((AssetCatalog changed) -> seen.add(changed.revision()));
    long start = catalog.revision();

    catalog.install(new MemoryBundle("alchemist").file("textures/gui/skill/alchemy.png", ICON), "A");
    assertThat(catalog.uninstall("alchemist")).isTrue();
    assertThat(catalog.uninstall("alchemist")).isFalse();
    subscription.cancel();
    catalog.install(new MemoryBundle("alchemist"), "A");

    assertThat(seen).containsExactly(start + 1, start + 2);
    assertThat(catalog.revision()).isEqualTo(start + 3);
  }

  @Test
  void uninstallRemovesTheBundleAndItsFiles() {
    catalog.install(new MemoryBundle("alchemist").file("textures/gui/skill/alchemy.png", ICON), "A");
    catalog.uninstall("alchemist");

    assertThat(catalog.bundles()).isEmpty();
    assertThat(catalog.files()).isEmpty();
    assertThat(catalog.sprite(Id.of("alchemist", "gui/skill/alchemy"))).isEmpty();
  }

  @Test
  void aBadEntryIsSkippedWithOneErrorAndTheRestInstalls() {
    InstalledBundle installed = catalog.install(new MemoryBundle("alchemist")
        .file("textures/gui/skill/alchemy.png", ICON)
        .file("textures/gui/adaptation/alchemy/big.png", MemoryBundle.png(32, 32, 0xFFFFFFFF))
        .file("textures/gui/emblem/alchemy.png", "not a png".getBytes(StandardCharsets.UTF_8))
        .file("themes/alchemy.json", "{}".getBytes(StandardCharsets.UTF_8))
        .file("textures/gui/theme/alchemy.png", MemoryBundle.png(64, 32, 0xFF112233)), "A");

    assertThat(installed.entries()).extracting(AssetEntry::path)
        .containsExactly("textures/gui/skill/alchemy.png", "textures/gui/theme/alchemy.png");
    assertThat(errors).hasSize(3);
    assertThat(errors.get(0)).contains("alchemist").contains("textures/gui/adaptation/alchemy/big.png").contains("16x16");
    assertThat(errors.get(1)).contains("alchemist").contains("textures/gui/emblem/alchemy.png");
    assertThat(errors.get(2)).contains("alchemist").contains("themes/alchemy.json");
  }

  @Test
  void spritesAreFoundByTheirTextureId() {
    catalog.install(new MemoryBundle("alchemist").file("textures/gui/skill/alchemy.png", ICON), "A");

    assertThat(catalog.sprite(Id.of("alchemist", "gui/skill/alchemy"))).hasValueSatisfying(
        (byte[] bytes) -> assertThat(bytes).isEqualTo(ICON));
    assertThat(catalog.sprite(Id.of("alchemist", "gui/skill/missing"))).isEmpty();
  }

  @Test
  void anAuthoredThemeWinsAndARegisteredSkillWithoutOneGetsAGeneratedTheme() {
    ThemeAssets generated = catalog.theme(ALCHEMY).orElseThrow();
    assertThat(generated.generated()).isTrue();
    assertThat(generated.hash()).isEqualTo(AssetHashes.sha1(generated.kitJson(), generated.atlasPng()));
    assertThat(catalog.theme(Id.of("alchemist", "unknown"))).isEmpty();

    ThemeAssets source = ThemeGenerator.generate(new SkillLook(ALCHEMY, 0x22AA44, Id.minecraft("oak_sapling")));
    catalog.install(new MemoryBundle("alchemist").file("themes/alchemy.json", source.kitJson())
        .file("textures/gui/theme/alchemy.png", source.atlasPng()), "A");

    ThemeAssets authored = catalog.theme(ALCHEMY).orElseThrow();
    assertThat(authored.generated()).isFalse();
    assertThat(authored.kitJson()).isEqualTo(source.kitJson());
    assertThat(catalog.bundles().get(0).entries()).extracting(AssetEntry::kind)
        .containsExactly(AssetKind.THEME_KIT, AssetKind.THEME_ATLAS);
  }

  @Test
  void themesListsRegisteredSkillsThenSkillsWithOnlyAnAuthoredKit() {
    ThemeAssets source = ThemeGenerator.generate(new SkillLook(Id.of("alchemist", "brewing"), 0x22AA44, Id.minecraft("potion")));
    catalog.install(new MemoryBundle("alchemist").file("themes/brewing.json", source.kitJson())
        .file("textures/gui/theme/brewing.png", source.atlasPng()), "A");

    assertThat(catalog.themes()).containsExactly(ALCHEMY, Id.of("alchemist", "brewing"));
    assertThat(catalog.themes()).allMatch((Id skill) -> catalog.theme(skill).isPresent());
  }

  @Test
  void filesAreListedByResourceIdInBundleOrder() {
    catalog.install(new MemoryBundle("zeta").file("textures/gui/skill/z.png", ICON), "Z");
    catalog.install(new MemoryBundle("adapt").file("textures/gui/skill/agility.png", ICON), "adapt");

    Map<String, AdaptAssetCatalog.StoredFile> files = catalog.files();
    assertThat(files.keySet()).containsExactly("adapt:textures/gui/skill/agility.png", "zeta:textures/gui/skill/z.png");
  }
}
