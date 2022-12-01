package net.sf.freecol.common.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

public final class CharsetCompat {
  public static final CharBuffer decode(Charset cs, ByteBuffer bb) {
    return cs.decode(bb);
  }

  public static final java.nio.ByteBuffer encode(Charset cs, CharBuffer cb) {
    return cs.encode(cb);
  }
}
