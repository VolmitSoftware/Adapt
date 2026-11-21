package art.arcane.adapt.client.protocol;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Splits a frame that is larger than a transport's payload limit into ordered fragments and joins them again. A
 * fragment is {@code [0][count][index]} followed by a non-empty slice of the frame; opcode 0 is never a frame, so a
 * frame that fits travels unchanged. Fragments of one frame are sent back to back: anything else arriving before the
 * last one is a protocol violation.
 */
public final class FrameFragments {
  public static final int OPCODE = 0;
  public static final int HEADER_BYTES = 3;

  private FrameFragments() {
  }

  /** The payloads to send for {@code frame}: the frame itself when it fits in {@code maxPayloadBytes}. */
  public static List<byte[]> split(byte[] frame, int maxPayloadBytes) throws ClientProtocolException {
    Objects.requireNonNull(frame, "frame");
    if (frame.length <= maxPayloadBytes) {
      return List.of(frame);
    }
    int slice = maxPayloadBytes - HEADER_BYTES;
    if (slice <= 0) {
      throw new ClientProtocolException("payload limit " + maxPayloadBytes + " leaves no room for a fragment");
    }
    int count = (frame.length + slice - 1) / slice;
    if (count > FrameLimits.MAX_FRAGMENTS) {
      throw new ClientProtocolException("frame of " + frame.length + " bytes needs " + count + " fragments of "
          + maxPayloadBytes + " bytes, over " + FrameLimits.MAX_FRAGMENTS);
    }
    List<byte[]> fragments = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      int from = index * slice;
      int length = Math.min(slice, frame.length - from);
      byte[] fragment = new byte[HEADER_BYTES + length];
      fragment[0] = (byte) OPCODE;
      fragment[1] = (byte) count;
      fragment[2] = (byte) index;
      System.arraycopy(frame, from, fragment, HEADER_BYTES, length);
      fragments.add(fragment);
    }
    return fragments;
  }

  /** Joins the fragments of one direction of one connection. Not thread-safe; reset it when the connection ends. */
  public static final class Assembler {
    private final int maxFrameBytes;
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
    private int expected;
    private int next;

    public Assembler(int maxFrameBytes) {
      this.maxFrameBytes = maxFrameBytes;
    }

    /**
     * Takes one received payload. Returns the complete frame, or null while fragments are still missing. A violation
     * throws and discards the partial frame.
     */
    public byte[] accept(byte[] payload) throws ClientProtocolException {
      Objects.requireNonNull(payload, "payload");
      if (payload.length == 0 || payload[0] != OPCODE) {
        if (expected != 0) {
          fail("frame arrived between fragments " + next + " and " + (next + 1) + " of " + expected);
        }
        return payload;
      }
      if (payload.length <= HEADER_BYTES) {
        fail("empty fragment");
      }
      int count = payload[1] & 0xFF;
      int index = payload[2] & 0xFF;
      if (count < 2 || count > FrameLimits.MAX_FRAGMENTS) {
        fail("fragment count " + count + " outside 2.." + FrameLimits.MAX_FRAGMENTS);
      }
      if (expected == 0) {
        expected = count;
      }
      if (count != expected || index != next) {
        fail("fragment " + index + " of " + count + " where " + next + " of " + expected + " was due");
      }
      if (pending.size() + payload.length - HEADER_BYTES > maxFrameBytes) {
        fail("fragmented frame exceeds " + maxFrameBytes + " bytes");
      }
      pending.write(payload, HEADER_BYTES, payload.length - HEADER_BYTES);
      next++;
      if (next < expected) {
        return null;
      }
      byte[] frame = pending.toByteArray();
      reset();
      if (frame[0] == OPCODE) {
        throw new ClientProtocolException("fragment inside a fragmented frame");
      }
      return frame;
    }

    public void reset() {
      pending.reset();
      expected = 0;
      next = 0;
    }

    private void fail(String message) throws ClientProtocolException {
      reset();
      throw new ClientProtocolException(message);
    }
  }
}
