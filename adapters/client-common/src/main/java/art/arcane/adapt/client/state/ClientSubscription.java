package art.arcane.adapt.client.state;

/** Removes a listener added to {@link ClientState}. */
@FunctionalInterface
public interface ClientSubscription {
    void cancel();
}
