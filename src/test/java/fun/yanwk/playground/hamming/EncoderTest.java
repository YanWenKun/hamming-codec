package fun.yanwk.playground.hamming;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class EncoderTest {

    @Test
    void testEncoder() throws IOException {
        var sample = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789he".getBytes(); // 64 bytes
        var encoded = new byte[72];
        for (int i = 0; i < 8; i++) {
            byte[] buffer = new byte[8];
            System.arraycopy(sample, i * 8, buffer, 0, 8);
            byte[] block = Encoder.hammingEncodeOneBlock(buffer);
            System.arraycopy(block, 0, encoded, i * 9, 9);
        }
        var interleaved = Encoder.blockInterleave(encoded, 8);

        var bis = new ByteArrayInputStream(sample);
        var bos = new ByteArrayOutputStream();
        Encoder.encodeStream(bis, bos, 8, 8);

        var expected = new byte[]{
            -114, 12, 68, 0, -119, -2, -15, 51,
            42, 84, 15, 14, -2, 0, -2, -15,
            -14, 55, 90, 1, -16, 1, 0, -2,
            -31, 39, 74, 1, -31, -2, 0, -2,
            -1, -31, 39, 74, -31, 31, 1, 0,
            -4, -29, 39, 73, -32, 28, -4, 0,
            -4, -29, 39, 73, -4, -32, 3, 0,
            -3, -29, 38, 73, -4, -30, -4, 0,
            -48, -3, -29, 102, -88, 29, 30, 3};

        assertArrayEquals(expected, interleaved);
        assertArrayEquals(expected, bos.toByteArray());
    }

    @Test
    void testEncodeStream() throws IOException {
        var sample = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789hello_world!".getBytes();
        var expected = new byte[]{
            -114, 12, 68, 0, -119, -2, -15, 51,
            42, 84, 15, 14, -2, 0, -2, -15,
            -14, 55, 90, 1, -16, 1, 0, -2,
            -31, 39, 74, 1, -31, -2, 0, -2,
            -1, -31, 39, 74, -31, 31, 1, 0,
            -4, -29, 39, 73, -32, 28, -4, 0,
            -4, -29, 39, 73, -4, -32, 3, 0,
            -3, -29, 38, 73, -4, -30, -4, 0,
            -48, -3, -29, 102, -88, 29, 30, 3,
            0, -64, -64, 0, 0, -64, -64, 0,
            0, -128, -64, 0, 0, 0, -128, -64,
            -64, 0, -128, -128, 0, 64, 0, -128,
            -128, 0, -128, -128, -128, -128, 0, -128,
            -128, 0, -128, -128, -128, -128, -128, 0,
            -128, -128, -128, 0, -128, -128, -128, 0,
            -128, -128, 0, -128, -128, -128, -128, 0,
            -128, -128, -128, 0, 0, -128, 0, 0,
            0, -128, -128, 0, -128, -128, 0, 0};

        var bis = new ByteArrayInputStream(sample);
        var bos = new ByteArrayOutputStream();
        Encoder.encodeStream(bis, bos, 8, 8);
        assertArrayEquals(expected, bos.toByteArray());
    }

    @Test
    void testHammingEncodeOneBlock() {
        /*
        输入 -1 （ 1111_1111）
        填入数据块： 0001_0111 0111_1000
        填入校验位： 0111_0111 0111_1000
        填入扩展校验位： 0111_0111 0111_1000
         */
        var one = Encoder.hammingEncodeOneBlock(new byte[]{(byte) 0b1111_1111});
        var oneExpected = new byte[]{0b0111_0111, 0b0111_1000};
        assertArrayEquals(oneExpected, one);

        /*
        输入： -1, 0, -128, 127, 1, 64, 99, 57
        填入数据块： 23, 120, 2, 1, 126, 2, -128, -58, 57
        填入校验位： 119, 120, -126, 1, 126, 2, -128, -58, 57
        填入扩展校验位： -9, 120, -126, 1, 126, 2, -128, -58, 57
         */
        var eight = Encoder.hammingEncodeOneBlock(new byte[]{-1, 0, -128, 127, 1, 64, 99, 57});
        var eightExpected = new byte[]{-9, 120, -126, 1, 126, 2, -128, -58, 57};
        assertArrayEquals(eightExpected, eight);
    }

    @Test
    void testBlockInterleave() {
        var one = Encoder.blockInterleave(new byte[]{0b0001_1000}, 2);
        var oneExpected = new byte[]{0b0100_0010};
        assertArrayEquals(oneExpected, one);

        var two = Encoder.blockInterleave(new byte[]{0b0000_0000, (byte) 0b1111_1111}, 2);
        var twoExpected = new byte[]{0b0101_0101, 0b0101_0101};
        assertArrayEquals(twoExpected, two);

        byte[] seventyTwoBytes = "123456789223456789323456789423456789523456789623456789723456789823456789".getBytes();
        var seventyTwo = Encoder.blockInterleave(seventyTwoBytes, 8);
        var seventyTwoExpected = new byte[]{
            0, 0, -1, -1, 1, 30, 102, -86, 0,
            0, -1, -1, 0, 0, -1, 0, 0, 0,
            -1, -1, 0, 0, -1, -1, 0, 0, -1,
            -1, 0, -1, 0, 0, 0, 0, -1, -1,
            0, -1, 0, -1, 0, 0, -1, -1, 0,
            -1, -1, 0, 0, 0, -1, -1, 0, -1,
            -1, -1, 0, 0, -1, -1, -1, 0, 0,
            0, 0, 0, -1, -1, -1, 0, 0, -1};
        assertArrayEquals(seventyTwoExpected, seventyTwo);
    }
}
