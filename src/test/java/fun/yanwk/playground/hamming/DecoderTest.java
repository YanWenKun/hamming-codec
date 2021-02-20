package fun.yanwk.playground.hamming;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StreamCorruptedException;

import static org.junit.jupiter.api.Assertions.*;

public class DecoderTest {


    @Test
    void testDecodeStream1() throws IOException {
        var sample = new byte[]{
            -114, 12, 68, 0, -119, -2, -15, 51,
            42, 84, 15, 14, -2, 0, -2, -15,
            -14, 55, 90, 1, -16, 1, 0, -2,
            -31, 39, 74, 1, -31, -2, 0, -2,
            -1, -31, 39, 74, -31, 31, 1, 0,
            -4, -29, 39, 73, -32, 28, -4, 0,
            -4, -29, 39, 73, -4, -32, 3, 0,
            -3, -29, 38, 73, -4, -30, -4, 0,
            -48, -3, -29, 102, -88, 29, 30, 3};
        var expected = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789he".getBytes();

        var bis = new ByteArrayInputStream(sample);
        var bos = new ByteArrayOutputStream();
        Decoder.decodeStream(bis, bos, 8, 8);
        assertArrayEquals(expected, bos.toByteArray());
    }

    @Test
    void testDecodeStream2() throws IOException {
        var sample = new byte[]{
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
        // 注意数据长度不对齐的情况下需要填充 0
        var expectedShort = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789hello_world!".getBytes();
        var expected = new byte[128];
        System.arraycopy(expectedShort, 0, expected, 0, expectedShort.length);

        var bis = new ByteArrayInputStream(sample);
        var bos = new ByteArrayOutputStream();
        Decoder.decodeStream(bis, bos, 8, 8);
        assertArrayEquals(expected, bos.toByteArray());
    }

    @Test
    void testHammingCorrectOneBlock() throws IOException {
        var AExpected = new byte[]{0b0111_0111, 0b0111_1000};
        var AWithOneError = new byte[]{0b0111_0111, 0b0111_1001};
        var ACorrected = Decoder.hammingCorrectOneBlock(AWithOneError);
        assertArrayEquals(AExpected, ACorrected);

        var AWithTwoError = new byte[]{0b0111_0100, 0b0111_1000};
        Exception exceptionA = assertThrows(StreamCorruptedException.class, () -> {
            Decoder.hammingCorrectOneBlock(AWithTwoError);
        });
        var expectedMessageA = "在一组编码中出现两位比特错误，无法纠错！";
        var actualMessageA = exceptionA.getMessage();
        assertTrue(actualMessageA.contains(expectedMessageA));

        var BExpected = new byte[]{-9, 120, -126, 1, 126, 2, -128, -58, 57};
        var BWithOneError = new byte[]{-9, 120, -126, 0, 126, 2, -128, -58, 57};
        var BCorrected = Decoder.hammingCorrectOneBlock(BWithOneError);
        assertArrayEquals(BExpected, BCorrected);

        var BWithTwoError = new byte[]{-9, 120, -126, 0, 126, 3, -128, -58, 57};
        Exception exceptionB = assertThrows(StreamCorruptedException.class, () -> {
            Decoder.hammingCorrectOneBlock(BWithTwoError);
        });
        var expectedMessageB = "在一组编码中出现两位比特错误，无法纠错！";
        var actualMessageB = exceptionB.getMessage();
        assertTrue(actualMessageB.contains(expectedMessageB));
    }

    @Test
    void testHammingDecodeOneBlock() {
        // 取编码器的测试用例，交换即可
        var two = Decoder.hammingDecodeOneBlock(new byte[]{0b0111_0111, 0b0111_1000}, 1);
        var twoExpected = new byte[]{(byte) 0b1111_1111};
        assertArrayEquals(twoExpected, two);

        var nine = Decoder.hammingDecodeOneBlock(new byte[]{-9, 120, -126, 1, 126, 2, -128, -58, 57}, 8);
        var nineExpected = new byte[]{-1, 0, -128, 127, 1, 64, 99, 57};
        assertArrayEquals(nineExpected, nine);
    }
}
