package art.arcane.adapt.client.net;

import art.arcane.adapt.client.protocol.PanelView;

/** Shows server menus as native panels. Called on the client thread. */
public interface PanelSink {
    PanelSink NONE = new PanelSink() {
        @Override
        public void open(PanelView panel) {
        }

        @Override
        public void update(PanelView panel) {
        }

        @Override
        public void close(int sessionId) {
        }

        @Override
        public void reset() {
        }
    };

    void open(PanelView panel);

    void update(PanelView panel);

    void close(int sessionId);

    /** The session ended: drop every open panel without answering the server. */
    void reset();
}
