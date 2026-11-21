package art.arcane.adapt.client.net;

import art.arcane.adapt.client.protocol.FeedbackKind;

/** Shows the server's answer to a client action. Called on the client thread. */
public interface FeedbackSink {
    FeedbackSink NONE = (FeedbackKind kind, String text) -> {
    };

    void feedback(FeedbackKind kind, String text);
}
