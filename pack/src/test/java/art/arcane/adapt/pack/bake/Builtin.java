package art.arcane.adapt.pack.bake;

import art.arcane.adapt.engine.asset.SkillLook;
import art.arcane.adapt.engine.asset.ThemeAssets;
import art.arcane.adapt.engine.asset.ThemeGenerator;
import art.arcane.adapt.pack.TestCatalogs;
import art.arcane.adapt.platform.value.Id;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The built-in kits from client-common's resources, which the build hands the tests as {@code adapt.pack.assets}, and
 * the kits generated for addon skills that ship none: crystal and organic, framing a skill icon and the generated emblem.
 */
final class Builtin {
    private static List<ThemeAssets> kits;
    private static List<ThemeAssets> generated;

    private Builtin() {
    }

    static Path root() {
        return TestCatalogs.builtinRoot();
    }

    static synchronized List<ThemeAssets> kits() {
        if (kits == null) {
            kits = PackBake.themes(TestCatalogs.builtin());
        }
        return kits;
    }

    static synchronized List<ThemeAssets> generated() {
        if (generated == null) {
            try {
                byte[] icon = Files.readAllBytes(root().resolve("assets/adapt/textures/gui/skill/pickaxe.png"));
                generated = List.of(
                        ThemeGenerator.generate(new SkillLook(Id.of("example", "geology"), 0x8A6F4E, Id.minecraft("tuff")), icon),
                        ThemeGenerator.generate(new SkillLook(Id.of("orbit", "comet"), 0x2E5FD0, Id.minecraft("paper")), null),
                        ThemeGenerator.generate(new SkillLook(Id.of("garden", "grove"), 0x3FAF4A, Id.minecraft("oak_sapling")), icon),
                        ThemeGenerator.generate(new SkillLook(Id.of("alchemist", "alchemy"), 0xF2E14C, Id.minecraft("brewing_stand")), null));
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
        return generated;
    }

    /** {@link #kits()} and then {@link #generated()}. */
    static List<ThemeAssets> withGenerated() {
        List<ThemeAssets> all = new ArrayList<>(kits());
        all.addAll(generated());
        return all;
    }

    static ThemeAssets kit(String skill) {
        return kits().stream().filter((ThemeAssets kit) -> kit.skill().path().equals(skill)).findFirst().orElseThrow();
    }
}
