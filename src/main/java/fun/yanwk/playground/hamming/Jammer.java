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
     * 一次读取若干字节进入缓冲区，并在缓冲区内进行“抽奖”，随机连续翻转比特。
     * 主要模拟连续干扰（burst error），不体现随机性。
     *
     * @param probability 总的比特翻转概率，小数形式（ 1% 写成 0.01 ）
     * @param maxBurst    最多连续出现多少位比特翻转，建议不超过分组交织数
     *
     * @return 一共翻转了多少位比特
     */
    public static long distortStream(
        InputStream in,
        OutputStream out,
        double probability,
        int maxBurst
    ) throws IOException {

        if (probability <= 0) {
            in.transferTo(out);
            return 0;
        }

        long flipCount = 0;
        maxBurst = (maxBurst < 2) ? 1 : maxBurst;
        // 实际发生的翻转概率
        final double rate = (maxBurst < 2) ? probability : probability / ((1 + maxBurst) / 2.0);
        // 缓冲区大小
        final int bufferSize = (int) Math.round(maxBurst / rate / 8.0);

        while (in.available() > 0) {
            byte[] buffer = new byte[bufferSize];
            int dataLength = in.read(buffer);
            if (dataLength == -1) {
                break;
            } else {

                // 每个缓冲区对应一个随机种子
                final Random random = new Random();

                for (int bitAddr = 0; bitAddr < dataLength * 8; bitAddr++) {

                    if (random.nextDouble() - rate < 0) { // nextDouble() 的返回区间为 [0.0, 1.0)

                        int burstToAddr = bitAddr + random.nextInt(maxBurst); // nextInt(n) 的返回区间为 [0, n)

                        while (bitAddr <= burstToAddr
                            && bitAddr < dataLength * 8) {
                            Utils.flipBitInArray(buffer, bitAddr);
                            flipCount++;
                            bitAddr++;
                        }
                    }
                }

                out.write(buffer, 0, dataLength); // 使输出流与输入流等宽，避免只输出缓冲区整数倍大小
            }
        }

        return flipCount;
    }

}
