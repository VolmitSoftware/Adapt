package art.arcane.adapt.client.net;

import art.arcane.adapt.client.view.InsightMark;
import art.arcane.adapt.client.view.InsightView;

import java.util.List;

/** Shows the Insight target and marks. Called on the client thread. */
public interface InsightSink {
    InsightSink NONE = new InsightSink() {
        @Override
        public void target(InsightView view) {
        }

        @Override
        public void vitals(int entityId, double health, double absorption) {
        }

        @Override
        public void marks(List<InsightMark> marks) {
        }

        @Override
        public void clear() {
        }

        @Override
        public void reset() {
        }
    };

    void target(InsightView view);

    void vitals(int entityId, double health, double absorption);

    void marks(List<InsightMark> marks);

    /** The server lost the target: drop it and every mark. */
    void clear();

    /** The session ended. */
    void reset();
}
