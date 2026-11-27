package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.theme.PageTheme;
import art.arcane.adapt.client.theme.ThemeKit;
import art.arcane.adapt.client.theme.ThemeLibrary;
import art.arcane.adapt.client.ui.UiResources;
import art.arcane.adapt.client.ui.UiSpriteCache;
import art.arcane.adapt.client.ui.UiSprites;
import art.arcane.adapt.client.ui.kit.UiContrast;
import art.arcane.adapt.client.ui.kit.UiRamp;
import art.arcane.adapt.client.ui.kit.UiTome;
import art.arcane.adapt.client.view.IconRef;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToastLookTest {
    private static final UiSprites SPRITES = new UiSpriteCache(new UiResources() {
        @Override
        public InputStream open(String id) {
            return ToastLookTest.class.getResourceAsStream("/assets/" + id.replace(':', '/'));
        }

        @Override
        public int revision() {
            return 0;
        }
    });
    private static final ThemeLibrary LIBRARY = new ThemeLibrary();
    private static final HudText TEXT = new HudText((String key) -> HudFakes.ENGLISH.getOrDefault(key, key));
    private static final List<String> SKILLS = List.of("swords", "axes", "unarmed", "ranged", "blocking", "stealth", "hunter",
            "tragoul", "agility", "kinetics", "rift", "chronos", "pickaxe", "excavation", "herbalism", "architect", "crafting",
            "enchanting", "brewing", "discovery", "taming", "golemancy", "seaborne", "nether");

    private static PageTheme theme(String skillId) {
        PageTheme theme = LIBRARY.theme(SPRITES, skillId);
        assertNotNull(theme, skillId);
        return theme;
    }

    private static int[] stud(ThemeKit kit) {
        return new int[] {kit.color('k'), kit.color('b'), kit.color('c'), kit.color('p')};
    }

    @Test
    void aToastWithoutASkillWearsTheBooksLeather() {
        ToastLook look = ToastLook.of(ToastKind.KNOWLEDGE, null);
        assertEquals(HudPaint.PAPER, look.paper);
        assertEquals(HudPaint.PAPER_EDGE, look.edge);
        assertSame(look.leftStud, look.rightStud);
        assertEquals(UiTome.Token.PLAIN, look.token);
        assertFalse(look.gilt || look.sheen || look.divider);
    }

    @Test
    void aSkillToastTakesItsKitColours() {
        for (String skill : SKILLS) {
            PageTheme theme = theme(skill);
            ToastLook look = ToastLook.of(ToastKind.UNLOCK, theme);
            assertEquals(theme.lead().color('k'), look.edge, skill);
            assertEquals(theme.lead().color('w'), look.well, skill);
            assertArrayEquals(stud(theme.lead()), look.leftStud, skill);
            assertArrayEquals(stud(theme.lead()), look.rightStud, skill);
            assertEquals(look.leftAccent, look.rightAccent, skill);
            assertEquals(HudPaint.PAPER >>> 24, look.paper >>> 24, skill);
            assertTrue(UiContrast.ratio(HudPaint.INK_MUTED, HudPaint.opaque(look.paper)) >= UiContrast.TEXT, skill);
            assertTrue(UiContrast.ratio(HudPaint.INK, HudPaint.opaque(look.paper)) >= UiContrast.TEXT, skill);
            assertTrue(UiContrast.ratio(look.leftAccent, HudPaint.opaque(look.paper)) >= UiContrast.GRAPHIC, skill);
            assertEquals(UiTome.Token.LEARNED, look.token);
        }
    }

    @Test
    void aMutatedSkillToastSplitsItsLeadAndPartner() {
        PageTheme theme = theme("agility+pickaxe");
        assertTrue(theme.mutated());
        ToastLook look = ToastLook.of(ToastKind.LEVEL_UP, theme);
        assertArrayEquals(stud(theme.lead()), look.leftStud);
        assertArrayEquals(stud(theme.partner()), look.rightStud);
        assertNotEquals(look.leftStud[0], look.rightStud[0]);
        assertEquals(theme.lead().color('k'), look.edge);
        int leadOnly = ToastLook.of(ToastKind.LEVEL_UP, theme("agility")).paper;
        int partnerOnly = ToastLook.of(ToastKind.LEVEL_UP, theme("pickaxe")).paper;
        assertNotEquals(leadOnly, look.paper);
        assertNotEquals(partnerOnly, look.paper);
        assertTrue(look.sheen);
        assertEquals(UiTome.Token.LEARNED, look.token);
    }

    @Test
    void eachKindHasItsOwnMark() {
        PageTheme theme = theme("ranged");
        ToastLook signature = ToastLook.of(ToastKind.SIGNATURE, theme);
        assertTrue(signature.gilt);
        assertEquals(UiRamp.GOLD[0], signature.edge);
        assertEquals(UiRamp.GOLD[0], signature.title);
        assertEquals(UiTome.Token.MAXED, signature.token);
        ToastLook discovery = ToastLook.of(ToastKind.DISCOVERY, theme);
        assertTrue(discovery.divider && !discovery.gilt && !discovery.sheen);
        ToastLook challenge = ToastLook.of(ToastKind.CHALLENGE, theme);
        assertEquals(ToastLook.CHALLENGE_PAPER, challenge.paper);
        assertEquals(ToastLook.CHALLENGE_TITLE, challenge.title);
        ToastLook knowledge = ToastLook.of(ToastKind.KNOWLEDGE, theme);
        assertEquals(UiTome.Token.PLAIN, knowledge.token);
        assertEquals(HudPaint.INK, knowledge.title);
        assertTrue(UiContrast.ratio(UiRamp.GOLD[0], HudPaint.opaque(signature.paper)) >= UiContrast.TEXT);
    }

    @Test
    void aSkillWithoutATheme() {
        assertNull(LIBRARY.theme(SPRITES, "no-such-skill"));
        assertNull(LIBRARY.theme(SPRITES, "agility+no-such-skill"));
    }

    @Test
    void theWidgetDressesEachToastByItsSkill() {
        HudFakes.Clock clock = new HudFakes.Clock();
        HudModel model = new HudModel(clock);
        model.toast(new ServerFrame.Toast(ToastKind.LEVEL_UP, IconRef.item("minecraft:feather"), "Agility 3", "", "agility"));
        model.toast(new ServerFrame.Toast(ToastKind.KNOWLEDGE, IconRef.item("minecraft:book"), "+3", "", ""));
        model.toast(new ServerFrame.Toast(ToastKind.UNLOCK, IconRef.item("minecraft:bow"), "Called Shot", "",
                "ranged+stealth"));
        HudRenderer renderer = new HudRenderer(model, TEXT);
        HudFakes.Draw draw = new HudFakes.Draw(960, 540, 2.0, false);
        draw.sprites = SPRITES;
        renderer.render(draw, ToastWidgetTest.layout(HudAnchor.TOP_CENTER, 3));
        List<HudToast> shown = model.toasts().shown();
        assertEquals(3, shown.size());
        assertNotNull(shown.get(0).look);
        assertEquals(theme("agility").lead().color('k'), shown.get(0).look.edge);
        assertEquals(HudPaint.PAPER_EDGE, shown.get(1).look.edge);
        assertEquals("ranged", shown.get(2).lookTheme.lead().skill());
        assertEquals("stealth", shown.get(2).lookTheme.partner().skill());
        assertArrayEquals(stud(theme("stealth").lead()), shown.get(2).look.rightStud);
    }
}
