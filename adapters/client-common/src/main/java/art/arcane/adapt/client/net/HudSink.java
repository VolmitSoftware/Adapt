package art.arcane.adapt.client.net;

import art.arcane.adapt.client.protocol.ServerFrame;

/** Draws server HUD segments, statuses, xp gains and toasts. Called on the client thread. */
public interface HudSink {
    HudSink NONE = new HudSink() {
        @Override
        public void publish(ServerFrame.HudPublish segment) {
        }

        @Override
        public void clear(String purpose) {
        }

        @Override
        public void status(ServerFrame.HudStatus status) {
        }

        @Override
        public void xp(ServerFrame.XpEarned earned) {
        }

        @Override
        public void toast(ServerFrame.Toast toast) {
        }

        @Override
        public void clearAll() {
        }

        @Override
        public void reset() {
        }
    };

    void publish(ServerFrame.HudPublish segment);

    void clear(String purpose);

    void status(ServerFrame.HudStatus status);

    void xp(ServerFrame.XpEarned earned);

    void toast(ServerFrame.Toast toast);

    /** The server dropped its whole HUD: clear every segment and status, keep the catalog and player view. */
    void clearAll();

    /** The session ended: drop every segment, status and pending toast. */
    void reset();
}
