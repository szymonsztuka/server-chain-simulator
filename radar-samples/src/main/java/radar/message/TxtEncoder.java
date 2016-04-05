package radar.message;

import java.nio.ByteBuffer;

public class TxtEncoder implements Encoder<String> {
    @Override
    public boolean write(String input, ByteBuffer output) {
        byte[] x = input.getBytes();
        output.put(x);
        return true;
    }
}
