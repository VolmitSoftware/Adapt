package art.arcane.adapt.client.protocol;

public final class ClientProtocolException extends Exception {
  public ClientProtocolException(String message) {
    super(message);
  }

  public ClientProtocolException(String message, Throwable cause) {
    super(message, cause);
  }
}
