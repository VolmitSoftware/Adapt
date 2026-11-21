package art.arcane.adapt.client.qa;

import art.arcane.adapt.client.ui.kit.Button;
import art.arcane.adapt.client.ui.kit.KitDraw;
import art.arcane.adapt.client.ui.kit.Label;
import art.arcane.adapt.client.ui.kit.ScrollArea;
import art.arcane.adapt.client.ui.kit.UiGroup;
import art.arcane.adapt.client.ui.kit.UiLayoutPolicy;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiProbeTest {
    private final KitDraw draw = new KitDraw();

    @Test
    void sceneJsonListsVisibleWidgetsWithBoundsStateAndEscapedText() {
        UiGroup root = new UiGroup("root");
        Button button = root.add(new Button("go", "Say \"hi\"\n", () -> {
        }));
        Label hidden = root.add(new Label("hidden", "nope"));
        hidden.setVisible(false);
        button.place(new UiRect(10, 20, 30, 15), draw);
        Map<String, String> state = new LinkedHashMap<>();
        state.put("sessionId", "4");
        String json = UiProbe.json(new UiProbe.Scene("panel", "T\\itle", state, 427, 240, 2.0, root, List.of(), button, null));
        assertEquals("{\"screen\":\"panel\",\"title\":\"T\\\\itle\",\"width\":427,\"height\":240,\"guiScale\":2.0,\"focused\":\"go\","
                + "\"hovered\":null,\"state\":{\"sessionId\":\"4\"},\"widgets\":[{\"id\":\"go\",\"kind\":\"button\","
                + "\"label\":\"Say \\\"hi\\\"\\n\",\"layer\":\"screen\",\"x\":10,\"y\":20,\"w\":30,\"h\":15,\"enabled\":true,"
                + "\"focused\":true,\"hovered\":false,\"state\":{\"style\":\"NORMAL\"}}]}", json);
    }

    @Test
    void widgetsScrolledOutOfTheirClipAreLeftOut() {
        UiGroup content = new UiGroup("content", UiLayoutPolicy.column(0));
        for (int index = 0; index < 10; index++) {
            content.add(new Button("b" + index, "B" + index, () -> {
            }));
        }
        ScrollArea scroll = new ScrollArea("scroll", content);
        UiGroup root = new UiGroup("root");
        root.add(scroll);
        scroll.place(new UiRect(0, 0, 100, 28), draw);
        String json = UiProbe.json(new UiProbe.Scene("kit", "t", Map.of(), 427, 240, 1.0, root, List.<UiNode>of(), null, null));
        assertTrue(json.contains("\"id\":\"b1\""), json);
        assertFalse(json.contains("\"id\":\"b2\""), json);
        assertTrue(json.contains("\"maxScroll\":\"112\""), json);
    }

    @Test
    void snapshotReturnsTheLatestPublishAndWithdrawOnlyClearsItsOwner() {
        UiProbe.hud(null);
        Object first = new Object();
        Object second = new Object();
        UiProbe.publish(first, "{\"screen\":\"a\"}");
        UiProbe.publish(second, "{\"screen\":\"b\"}");
        UiProbe.withdraw(first);
        assertEquals("{\"screen\":\"b\"}", UiProbe.snapshot());
        assertTrue(UiProbe.watched());
        UiProbe.withdraw(second);
        assertEquals(UiProbe.NO_SCREEN, UiProbe.snapshot());
    }

    @Test
    void theHudLayoutRidesAlongWithOrWithoutAScreen() {
        Object owner = new Object();
        UiProbe.hud(null);
        UiProbe.publish(owner, "{\"screen\":\"a\"}");
        try {
            assertEquals("{\"screen\":\"a\"}", UiProbe.snapshot());
            UiProbe.hud("{\"widgets\":[]}");
            assertEquals("{\"screen\":\"a\",\"hud\":{\"widgets\":[]}}", UiProbe.snapshot());
            UiProbe.withdraw(owner);
            assertEquals("{\"screen\":null,\"hud\":{\"widgets\":[]}}", UiProbe.snapshot());
        } finally {
            UiProbe.withdraw(owner);
            UiProbe.hud(null);
        }
    }
}
