package art.arcane.adapt.pack.runtime;

import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.CatalogViews;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.engine.asset.AdaptAssetCatalog;
import art.arcane.adapt.engine.asset.PngImages;
import art.arcane.adapt.engine.asset.SkillLook;
import art.arcane.adapt.pack.TestCatalogs;
import art.arcane.adapt.pack.bake.BakeRequest;
import art.arcane.adapt.pack.bake.PackBake;
import art.arcane.adapt.pack.bake.PackManifest;
import art.arcane.adapt.pack.bake.PackValidator;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.pack.PackResources;
import art.arcane.adapt.pack.layout.PackArt;
import art.arcane.adapt.pack.layout.PackLayoutReader;
import art.arcane.adapt.pack.manifest.PackManifestReader;
import art.arcane.adapt.pack.text.PackFontMetrics;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An addon's bundle in its own namespace reaches the pack through the engine catalog alone. */
class ThirdPartyPackTest {
    private static final String NS = "orbit";
    private static final Id COMET = Id.of(NS, "comet");
    private static final Id NOVA = Id.of(NS, "nova");

    private static final Id ALCHEMY = Id.of("alchemist", "alchemy");

    @TempDir
    Path cache;

    @TempDir
    Path data;

    @Test
    void addonBundleBakesIntoItsNamespaceAndEachRevisionRebuildsOnce() throws IOException {
        AdaptAssetCatalog catalog = TestCatalogs.builtin(List.of(new SkillLook(NOVA, 0x3366CC, Id.minecraft("amethyst_shard"))), Set.of());
        Map<String, byte[]> files = addon();
        catalog.install(TestCatalogs.memory(NS, files), NS);
        assertTrue(catalog.theme(NOVA).orElseThrow().generated());
        assertEquals(List.of(NOVA, COMET), catalog.themes());

        AtomicInteger bakes = new AtomicInteger();
        PackBuilds builds = new PackBuilds(catalog, "3.0.0", cache, new PackDrops(cache.resolve("drops")), Runnable::run, 4, (BakeRequest request) -> {
            bakes.incrementAndGet();
            return PackBake.build(request);
        });
        builds.start();
        byte[] zip = builds.source().current().orElseThrow().open().readAllBytes();
        Map<String, byte[]> pack = PackCache.unzip(zip);

        assertEquals(1, bakes.get());
        assertEquals(List.of(), PackValidator.check(pack));
        for (Id skill : List.of(COMET, NOVA)) {
            String path = skill.path();
            assertTrue(any(pack, "assets/orbit/font/page/" + path + "/"), "page font of " + skill);
            assertTrue(any(pack, "assets/orbit/textures/font/page/" + path + "/"), "page tiles of " + skill);
            assertTrue(any(pack, "assets/orbit/items/adapt_ui/page/" + path + "/"), "host items of " + skill);
            assertTrue(pack.containsKey("assets/orbit/textures/gui/sprites/tooltip/" + path + "_background.png"), "tooltip of " + skill);
            assertTrue(pack.containsKey("assets/orbit/textures/gui/sprites/tooltip/" + path + "_frame.png"), "tooltip frame of " + skill);
            assertTrue(pack.containsKey("assets/orbit/items/adapt_ui/toast/" + path + ".json"), "toast of " + skill);
            assertTrue(pack.containsKey("assets/orbit/textures/item/adapt_ui/toast/" + path + ".png"), "toast corners of " + skill);
        }
        for (String icon : List.of("gui/skill/comet", "gui/skill/nova", "gui/adaptation/comet_dash")) {
            assertTrue(pack.containsKey("assets/orbit/items/adapt_ui/" + icon + ".json"), "icon item " + icon);
            assertTrue(pack.containsKey("assets/orbit/textures/" + icon + ".png"), "icon texture " + icon);
        }
        String manifest = new String(pack.get(PackManifest.PATH), StandardCharsets.UTF_8);
        assertTrue(manifest.contains("\"orbit:comet\"") && manifest.contains("\"orbit:nova\""), "manifest names both kits");

        String first = builds.source().current().orElseThrow().sha1Hex();
        files.put("textures/gui/adaptation/nova_burst.png", icon(0xFFE0A030));
        catalog.install(TestCatalogs.memory(NS, files), NS);

        assertEquals(2, bakes.get());
        String second = builds.source().current().orElseThrow().sha1Hex();
        assertNotEquals(first, second);
        assertTrue(PackCache.unzip(builds.source().current().orElseThrow().open().readAllBytes())
                .containsKey("assets/orbit/items/adapt_ui/gui/adaptation/nova_burst.json"));
        builds.stop();
    }

    @Test
    void foreignSkillPageAndIconsResolveFromItsViews() throws IOException {
        try (TestAdapt adapt = TestAdapt.start(TestPlatform.builder(data).build(), List.of(Alchemy.class))) {
            CatalogView views = CatalogViews.build(adapt.core().getAdaptServer().getSkillRegistry(), UUID.randomUUID(), List.of(), false);
            SkillView skill = views.skills().stream().filter((SkillView view) -> view.id().equals(ALCHEMY.toString())).findFirst().orElseThrow();
            AdaptationView transmute = skill.adaptations().get(0);

            AdaptAssetCatalog catalog = TestCatalogs.builtin(List.of(new SkillLook(ALCHEMY, 0x7A3FA0, Id.minecraft("brewing_stand"))),
                    Set.of("swords"));
            Map<String, byte[]> files = new TreeMap<>();
            files.put("textures/gui/skill/alchemy.png", icon(0xFF7A3FA0));
            files.put("textures/gui/adaptation/alchemy/transmute.png", icon(0xFFC0A040));
            catalog.install(TestCatalogs.memory(ALCHEMY.namespace(), files), ALCHEMY.namespace());
            PackBuilds builds = new PackBuilds(catalog, "3.0.0", cache, new PackDrops(cache.resolve("drops")), Runnable::run, 4, PackBake::build);
            builds.start();
            Map<String, byte[]> pack = PackCache.unzip(builds.source().current().orElseThrow().open().readAllBytes());
            builds.stop();
            assertEquals(List.of(), PackValidator.check(pack));
            PackResources resources = new PackResources(PackManifestReader.read(new StringReader(
                    new String(pack.get(PackManifest.PATH), StandardCharsets.UTF_8))), PackLayoutReader.builtIn(), PackFontMetrics.vanilla());

            PackResources.PageKit kit = PackResources.kit(skill.id(), 0);
            assertEquals("alchemist:alchemy", kit.kit());
            art.arcane.adapt.pack.manifest.PackManifest.PageArt page = resources.manifest().page(kit.kit(), PackArt.SINGLE);
            assertNotEquals(resources.manifest().page(PackArt.TOME, PackArt.SINGLE), page);
            assertTrue(page.hosts().stream().allMatch((art.arcane.adapt.pack.manifest.PackManifest.Host host) ->
                    host.art().model().namespace().equals("alchemist") && host.art().model().path().startsWith("adapt_ui/page/alchemy/single/")));
            assertTrue(pack.containsKey("assets/alchemist/font/page/alchemy/single.json"));
            assertEquals(Id.of("alchemist", "adapt_ui/gui/skill/alchemy"), resources.icon(skill.icon(), kit.kit(), false).itemModel());
            assertEquals(Id.of("alchemist", "adapt_ui/gui/adaptation/alchemy/transmute"),
                    resources.icon(transmute.icon(), kit.kit(), false).itemModel());
            assertTrue(pack.containsKey("assets/alchemist/items/adapt_ui/gui/adaptation/alchemy/transmute.json"));
            assertNotEquals(null, resources.manifest().tooltip(kit.kit()).style());

            String mutated = new SkillPair(ALCHEMY, Id.adapt("swords")).key();
            assertEquals(new PackResources.PageKit("alchemist:alchemy", false), PackResources.kit(mutated, 0));
            assertEquals(new PackResources.PageKit("adapt:swords", true), PackResources.kit(mutated, 1));
            assertNotEquals(resources.manifest().page(PackArt.TOME, PackArt.PARTNER), resources.manifest().page("adapt:swords", PackArt.PARTNER));
        }
    }

    /** One authored kit (comet, the herbalism kit and atlas), a generated theme for the registered nova, three icons. */
    private static Map<String, byte[]> addon() throws IOException {
        Path builtin = TestCatalogs.builtinRoot().resolve("assets").resolve(Id.ADAPT);
        String kit = Files.readString(builtin.resolve("themes/herbalism.json"), StandardCharsets.UTF_8)
                .replace("\"skill\":\"herbalism\"", "\"skill\":\"comet\"");
        Map<String, byte[]> files = new TreeMap<>();
        files.put("themes/comet.json", kit.getBytes(StandardCharsets.UTF_8));
        files.put("textures/gui/theme/comet.png", Files.readAllBytes(builtin.resolve("textures/gui/theme/herbalism.png")));
        files.put("textures/gui/skill/comet.png", icon(0xFF30A0E0));
        files.put("textures/gui/skill/nova.png", icon(0xFF8040C0));
        files.put("textures/gui/adaptation/comet_dash.png", icon(0xFF40C080));
        return files;
    }

    public static final class Alchemy extends SimpleSkill<SkillConfig> {
        public static final Id ID = ALCHEMY;

        public Alchemy() {
            super(ALCHEMY);
            registerConfiguration(SkillConfig.class);
            registerAdaptation(Transmute.ID, Transmute::new);
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }

    public static final class Transmute extends SimpleAdaptation<AdaptationConfig> {
        public static final Id ID = Id.of(ALCHEMY.namespace(), ALCHEMY.path() + "/transmute");

        public Transmute() {
            super(ID);
            registerConfiguration(AdaptationConfig.class);
            setLocalizationKey("pickaxe.chisel");
        }

        @Override
        public void addStats(int level, StatLines lines) {
        }
    }

    private static byte[] icon(int argb) {
        int[] pixels = new int[16 * 16];
        Arrays.fill(pixels, argb);
        return PngImages.encode(16, 16, pixels);
    }

    private static boolean any(Map<String, byte[]> pack, String prefix) {
        return pack.keySet().stream().anyMatch((String path) -> path.startsWith(prefix));
    }
}
