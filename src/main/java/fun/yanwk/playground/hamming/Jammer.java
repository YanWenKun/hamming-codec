package fun.yanwk.playground.hamming;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

/**
 * 比特级干扰器
 *
 * @author YAN Wenkun
 * 用于模拟通信中出现的噪音。
 * 人为制造干扰效果，随机翻转数据流中的部分比特。
 */
class Jammer {

    /**
     * 一次读取若干字节，并逐位“抽奖”，随机翻转比特。
     * 效率较低，但确保随机性。
     *
     * @param bufferSize  一次读、写的字节长度，建议按性能需要取 512 或 4096 或 65536
     * @param probability 比特翻转的概率，小数形式（ 1% 写成 0.01 ）
     */
    public static void distortStream(
        InputStream in,
        OutputStream out,
        int bufferSize,
        double probability
    ) throws IOException {
        final Random random = new Random(); // 每个缓冲区对应一个随机种子

        while (in.available() > 0) {
            byte[] buffer = new byte[bufferSize];
            int dataLength = in.read(buffer);
            if (dataLength == -1) {
                break;
            } else {
                byte[] output = new byte[dataLength]; // 使输出流与输入流等宽，避免只输出缓冲区整数倍大小
                for (int byteIndex = 0; byteIndex < output.length; byteIndex++) {
                    // 处理单个字节的随机比特翻转
                    for (int bitIndex = 0; bitIndex < 8; bitIndex++) {
                        if (random.nextDouble() - probability < 0) { // nextDouble() 的返回区间为 [0.0, 1.0)
                            buffer[byteIndex] = Utils.flipBit(buffer[byteIndex], bitIndex);
                        }
                    }
                    output[byteIndex] = buffer[byteIndex];
                }
                out.write(output);
            }
        }
    }

}
