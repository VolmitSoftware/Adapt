package art.arcane.adapt.client.panel;

import art.arcane.adapt.platform.menu.ClickKind;

import java.util.ArrayList;
import java.util.List;

/** Records panel actions as "click session revision slot KIND", "input session revision slot text", "closed session". */
final class RecordingActions implements PanelActions {
    final List<String> sent = new ArrayList<>();

    @Override
    public void click(int sessionId, int revision, int slot, ClickKind kind) {
        sent.add("click " + sessionId + " " + revision + " " + slot + " " + kind);
    }

    @Override
    public void input(int sessionId, int revision, int slot, String text) {
        sent.add("input " + sessionId + " " + revision + " " + slot + " " + text);
    }

    @Override
    public void closed(int sessionId) {
        sent.add("closed " + sessionId);
    }
}
