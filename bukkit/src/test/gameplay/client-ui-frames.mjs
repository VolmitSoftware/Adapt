const FRAGMENT = 0
const HEADER = 3
const MAX_FRAGMENTS = 64
const MAX_FRAME_BYTES = 1048576

/**
 * Joins adapt:ui payloads into frames. A frame larger than the server's plugin message limit arrives as back-to-back
 * fragments [0][count][index][slice]; anything else is a whole frame. Throws on a fragment out of order, a frame
 * between fragments, or a joined frame over the protocol limit.
 */
export function frameAssembler() {
  let parts = []
  let expected = 0
  let size = 0
  const reset = () => {
    parts = []
    expected = 0
    size = 0
  }
  const fail = message => {
    reset()
    throw new Error(`adapt:ui fragment error: ${message}`)
  }
  return {
    fragments: 0,
    push(payload) {
      const data = Buffer.from(payload)
      if (data.length === 0 || data[0] !== FRAGMENT) {
        if (expected !== 0) fail(`frame between fragments ${parts.length} of ${expected}`)
        return data
      }
      this.fragments++
      if (data.length <= HEADER) fail('empty fragment')
      const count = data[1]
      const index = data[2]
      if (count < 2 || count > MAX_FRAGMENTS) fail(`fragment count ${count}`)
      if (expected === 0) expected = count
      if (count !== expected || index !== parts.length) fail(`fragment ${index} of ${count}, expected ${parts.length} of ${expected}`)
      size += data.length - HEADER
      if (size > MAX_FRAME_BYTES) fail(`frame over ${MAX_FRAME_BYTES} bytes`)
      parts.push(data.subarray(HEADER))
      if (parts.length < expected) return null
      const frame = Buffer.concat(parts)
      reset()
      return frame
    }
  }
}
