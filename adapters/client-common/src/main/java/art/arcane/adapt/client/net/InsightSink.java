package art.arcane.adapt.client.net;

import art.arcane.adapt.client.protocol.ServerFrame;

import java.util.List;

/** Shows the Insight target and marks. Called on the client thread. */
public interface InsightSink {
    InsightSink NONE = new InsightSink() {
        @Override
        public void target(ServerFrame.InsightTarget view) {
        }

        @Override
        public void vitals(int entityId, double health, double absorption) {
        }

        @Override
        public void marks(List<ServerFrame.InsightMarks.Mark> marks) {
        }

        @Override
        public void clear() {
        }

        @Override
        public void reset() {
        }
    };

    void target(ServerFrame.InsightTarget view);

    void vitals(int entityId, double health, double absorption);

    void marks(List<ServerFrame.InsightMarks.Mark> marks);

    /** The server lost the target: drop it and every mark. */
    void clear();

    /** The session ended. */
    void reset();
}
