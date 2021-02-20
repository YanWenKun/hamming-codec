package fun.yanwk.playground.hamming;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StreamCorruptedException;

/**
 * 汉明码解码器
 *
 * @author YAN Wenkun
 * 业余爱好者级别的玩具
 * 工作流程：
 * 1、反交织（分选、解交织，de-interleave）
 * 2、纠错
 * 3、解码并输出
 */
class Decoder {

    /**
     * 对输入数据进行分组交织的反交织，并使用 SEC-DED 汉明码进行纠错、解码。
     *
     * @param in                           指定输入流
     * @param out                          指定输出流。建议将输出流的缓冲区大小设为有效数据长度的整数倍
     * @param encoderDataPerBlock          编码器中设置的每组汉明码的有效数据长度，单位字节，例如：【8】对应 (72,64) 汉明码
     * @param encoderGroupsPerInterleaving 编码器中设置的交织组数量
     */
    public static void decodeStream(
        InputStream in,
        OutputStream out,
        int encoderDataPerBlock,
        int encoderGroupsPerInterleaving
    ) throws IOException {

        // 解码器码块大小
        // 如使用 (72,64) 汉明码，则码块大小为 9 字节。详细计算公式见 Encoder 类
        final int codeSize = (int) Math.ceil(4.5 + encoderDataPerBlock - Integer.numberOfLeadingZeros(encoderDataPerBlock) / 8.0);
        // 解码器反交织（分选、解交织，de-interleave）的分组数量
        // 因为分组交织等价于矩阵转置，再转置一次即得到原矩阵，因此反交织的过程就是再交织一次，但是分组数量取（一套传输块的比特大小÷编码时的分组数量）
        // 如一套传输块为 72比特×8交织组=576比特，则分 576÷8=72组 进行反交织（等价于矩阵转置），化简后为：
        final int groupsPerDeinterleaving = codeSize * 8;
        // 一套传输块的大小，也是一次从输入流读取到缓冲区的大小
        final int readSize = codeSize * encoderGroupsPerInterleaving;

        while (in.available() > 0) {
            // 注意这里不考虑数据长度不对齐的情况，这种情况本来也无法正常解码。
            byte[] buffer = new byte[readSize];
            int i = in.read(buffer);
            if (i == -1) {
                break;
            }
            // 得到反交织后的一套传输块（例如 72 字节）
            byte[] deinterleaved = Encoder.blockInterleave(buffer, groupsPerDeinterleaving);

            // 取传输块中的码块（一组汉明码，例如 9 字节）进行纠错、解码
            for (int j = 0; j < deinterleaved.length; j += codeSize) {
                byte[] block = new byte[codeSize];
                System.arraycopy(deinterleaved, j, block, 0, block.length);

                // 因为调用者已使用 BufferedOutputStream，这里不再重复使用缓冲区
                out.write(
                    hammingDecodeOneBlock(
                        hammingCorrectOneBlock(block), encoderDataPerBlock
                    )
                );
            }
        }
    }

    /**
     * 汉明码纠错
     * 0 比特翻转：返回输入
     * 1 比特翻转：返回纠错后的正确数据
     * 2 比特翻转：报错，抛出异常
     * 3 或更多奇数个比特翻转：无法正确感知，会“乱纠错”（翻转不确定的一位，返回错误的结果）
     * 4 或更多偶数个比特翻转：行为不确定，可能报错、抛出异常，也可能被视为无出错
     */
    protected static byte[] hammingCorrectOneBlock(byte[] block) throws StreamCorruptedException {
        /* 纠错原理
         * 方法 A：将所有值为 1 的比特（无论数据位还是校验位）取其地址，全部异或，得出的值即为出错地址。
         * 方法 B：逐个验证校验位，如有不符则为 1，将所有校验结果拼合为一个地址，即为出错位（0 表示无错）。这里不采用该方法。
         * 由于引入了扩展校验位（用于 SEC-DED），这里先判断出错数量，不急于纠错。
         *
         * 判断原理
         * 将所有值（包括扩展校验位）异或，与出错位一起进行分类讨论。
         */

        int bitLength = block.length * 8;

        // 计算“所有值为 1 的比特”的地址的异或
        int _errorBitAddr = 0;
        // 将所有值（包括扩展校验位）进行异或
        int _parityCheck = Utils.getBit(block[0], 7); // 初始值取扩展校验位

        for (int sourceBitAddr = 1; sourceBitAddr < bitLength; sourceBitAddr++) { // 0 ^ n 恒等于 n，因此从 1 开始。
            byte targetByte = block[sourceBitAddr / 8];
            int targetBitIndex = 7 - (sourceBitAddr % 8);
            int targetBit = Utils.getBit(targetByte, targetBitIndex);
            if (targetBit == 1) {
                _errorBitAddr ^= sourceBitAddr;
                _parityCheck ^= 1;
            }
        }

        // 判断出错情况
        final int errorBitAddr = _errorBitAddr;
        final int parityCheck = _parityCheck;
        // 1. 无出错
        if (errorBitAddr == 0 && parityCheck == 0) {
            return block;
        }
        // 2. 扩展校验位出错，无影响
        if (errorBitAddr == 0 && parityCheck == 1) {
            return block;
        }
        // 3. 一个比特出错，返回纠错后的正确数据
        if (errorBitAddr != 0 && parityCheck == 1) {
            byte[] result = block.clone();
            byte targetByte = result[errorBitAddr / 8];
            int targetBitIndex = 7 - (errorBitAddr % 8);
            result[errorBitAddr / 8] = Utils.flipBit(targetByte, targetBitIndex);
            return result; // 这里没有必要验算，再次计算校验值必然是通过的。≥ 3 个且为奇数个的错误是无法感知的。
        }
        // 4. 两个比特出错，无法纠错，抛出异常
        if (errorBitAddr != 0 && parityCheck == 0) {
            throw new StreamCorruptedException("在一组编码中出现两位比特错误，无法纠错！");
        }

        throw new IllegalArgumentException("输入数据可能不是汉明码！");
    }

    /**
     * 汉明码解码
     *
     * @param dataSize 编码器中设置的每组汉明码的有效数据长度，单位字节，例如：【8】对应 (72,64) 汉明码
     */
    protected static byte[] hammingDecodeOneBlock(byte[] block, int dataSize) {
        byte[] result = new byte[dataSize];
        int targetBitAddr = 0;
        // 取汉明码中的数据部分
        // 直接从 3 开始，因为 0b00 0b01 0b10 这三个必然是校验位，且最小编码长度也是 (4,1)
        for (int sourceBitAddr = 3;
             sourceBitAddr < block.length * 8 && targetBitAddr < result.length * 8; // 避免越界
             sourceBitAddr++) {
            if (!Utils.isPowerOf2(sourceBitAddr)) {
                byte sourceByte = block[sourceBitAddr / 8];
                int sourceBitIndex = 7 - (sourceBitAddr % 8);
                byte targetByte = result[targetBitAddr / 8];
                int targetBitIndex = 7 - (targetBitAddr % 8);
                result[targetBitAddr / 8] = Utils.copyBit(sourceByte, sourceBitIndex, targetByte, targetBitIndex);
                targetBitAddr++;
            }
        }
        return result;
    }

}
