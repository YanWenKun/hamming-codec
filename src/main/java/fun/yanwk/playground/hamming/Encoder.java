package fun.yanwk.playground.hamming;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 汉明码编码器
 *
 * @author YAN Wenkun
 * 业余爱好者级别的玩具
 * 特点：
 * 1. 可变入长，从 (13,8) 到 (72,64) 再到 (8388633,8388608) 均可编码.
 * 2. SEC-DED 编码，拥有扩展校验位：单错可纠错，双错可感知。
 * 3. 编码后的数据通过分组交织存储。
 */
class Encoder {

    /**
     * 使用 SEC-DED 汉明码编码输入数据，并进行分组交织。
     *
     * @param in                    指定输入流
     * @param out                   指定输出流。建议将输出流的缓冲区大小设为传输块大小的整数倍
     * @param dataPerBlock          每组码块（编码后的汉明码）中信息（有效数据）的长度，单位字节，例如：【8】对应 (72,64) 汉明码
     * @param groupsPerInterleaving 交织组数量，例如：【8】对应以 8 组码块为一套传输块，进行分组交织
     */
    public static void encodeStream(
        InputStream in,
        OutputStream out,
        int dataPerBlock,
        int groupsPerInterleaving
    ) throws IOException {

        /* 计算编码后的字节长度
         *
         * 1、计算所需校验码的比特长度
         * int parityBits = (int) Math.floor(Math.log(dataPerBlock * 8 + 1) / Math.log(2)) + 1;
         * 优化后：
         * int parityBits = Integer.SIZE - Integer.numberOfLeadingZeros(dataPerBlock) + 3;
         *
         * 2、计算加上有效数据、扩展校验位后的总比特长度
         * int totalBits = dataPerBlock * 8 + parityBits + 1;
         *
         * 3、将总比特长度换算为字节长度（向上取整）
         * int codeSize = (int) Math.ceil(totalBits / 8.0)
         */

        // 化简后的计算式
        final int codeSize = (int) Math.ceil(4.5 + dataPerBlock - Integer.numberOfLeadingZeros(dataPerBlock) / 8.0);
        final int writeSize = codeSize * groupsPerInterleaving;

        while (in.available() > 0) {
            byte[] encoded = new byte[writeSize];

            for (int m = 0; m < groupsPerInterleaving; m++) {
                byte[] buffer = new byte[dataPerBlock];
                int i = in.read(buffer);
                if (i == -1) {
                    break;
                } else {
                    byte[] block = hammingEncodeOneBlock(buffer);
                    System.arraycopy(block, 0, encoded, m * codeSize, block.length);
                }
            }

            out.write(blockInterleave(encoded, groupsPerInterleaving));
        }
    }

    /**
     * 使用 SEC-DED 汉明码，编码一段数据（若干字节）。
     * 根据输入数据长度，自动调整输出数据块大小。
     * 最小编码为 (13,8) 汉明码（输出占用两字节）。
     */
    protected static byte[] hammingEncodeOneBlock(byte[] data) {

        final int parityBits = Integer.SIZE - Integer.numberOfLeadingZeros(data.length) + 3;
        final int totalBits = data.length * 8 + parityBits + 1; // 含0号位的扩展校验位，故+1

        byte[] result = new byte[(int) Math.ceil(totalBits / 8.0)]; // 此处隐含了：长度不对齐的部分将以 0 填充

        // 1、填满输出序列中的数据部分
        // 直接从 3 开始，因为 0b00 0b01 0b10 这三个必然是校验位，且最小编码长度也是 (4,1)
        int sourceBitAddr = 0;
        for (int targetBitAddr = 3; targetBitAddr < totalBits; targetBitAddr++) {
            if (!Utils.isPowerOf2(targetBitAddr)) {
                byte sourceByte = data[sourceBitAddr / 8];
                int sourceBitIndex = 7 - (sourceBitAddr % 8);
                byte targetByte = result[targetBitAddr / 8];
                int targetBitIndex = 7 - (targetBitAddr % 8);
                result[targetBitAddr / 8] = Utils.copyBit(sourceByte, sourceBitIndex, targetByte, targetBitIndex);
                sourceBitAddr++;
            }
        }
        // 2、依次完成奇偶校验（异或运算），填入校验位
        // Addr 表示“在整个序列中的地址”
        // bitwise 表示“要进行奇偶校验的第几个比特位”。
        for (int bitwise = 0; bitwise < parityBits; bitwise++) {
            int parity = 0;

            for (int targetBitAddr = 3; targetBitAddr < totalBits; targetBitAddr++) {
                // 当 bitwise=0, targetBitAddr 取“第0位为1，且非2的幂” => 0b0011, 0b0101, 0b0111, 0b1001 ...
                if (!Utils.isPowerOf2(targetBitAddr) &&
                    Utils.getBit(targetBitAddr, bitwise) == 1) {
                    byte targetByte = result[targetBitAddr / 8];
                    int targetBitIndex = 7 - (targetBitAddr % 8);
                    int targetBit = Utils.getBit(targetByte, targetBitIndex);
                    parity ^= targetBit;
                }
            }

            if (parity == 1) { // 如果校验和为偶（0），则略过操作
                int parityBitAddr = 1 << bitwise; // 校验位的地址： 0b0001, 0b0010, 0b0100 ...
                int parityAtByte = parityBitAddr / 8;
                int parityBitIndex = 7 - (parityBitAddr % 8);
                result[parityAtByte] ^= 1 << parityBitIndex;
            }
        }
        // 3、填入扩展校验位
        int extParity = 0;
        for (int targetBitAddr = 1; targetBitAddr < totalBits; targetBitAddr++) {
            byte targetByte = result[targetBitAddr / 8];
            int targetBitIndex = 7 - (targetBitAddr % 8);
            int targetBit = Utils.getBit(targetByte, targetBitIndex);
            extParity ^= targetBit;
        }
        result[0] ^= (1 & extParity) << 7;

        return result;
    }

    /**
     * 使用分组交织（又叫块交织、矩阵交织），将数据在比特层面分组，交织（交错）存储在分组中，并返回。
     *
     * @param groups 分组数量，须可被（输入数据的比特长度）整除。
     *               通常，输入数据中含有多少组码块，就分多少。
     *               等价于“输出数据中，多少个比特为一组”。
     */
    // 注意此处用 groups 一词，仅为与程序代码中的 block（汉明码的码块）区分，勿混淆概念。
    // 参考： https://en.wikipedia.org/wiki/Burst_error-correcting_code#Interleaved_codes
    // 参考： https://www.mathworks.com/help/comm/ug/interleaving.html
    protected static byte[] blockInterleave(final byte[] source, int groups) {
        if ((source.length * 8) % groups != 0) {
            throw new IndexOutOfBoundsException("分组数量与数据长度不对齐！请确保整除关系！");
        }

        final int oneBlockSize = (source.length * 8) / groups;

        // 分组交织 等价于 矩阵转置
        byte[] result = new byte[source.length];
        for (int i = 0; i < groups; i++) {
            for (int j = 0; j < oneBlockSize; j++) {
                int sourceBitAddr = i * oneBlockSize + j;
                byte sourceByte = source[sourceBitAddr / 8];
                int sourceBitIndex = 7 - (sourceBitAddr % 8);

                int targetBitAddr = j * groups + i;
                byte targetByte = result[targetBitAddr / 8];
                int targetBitIndex = 7 - (targetBitAddr % 8);

                result[targetBitAddr / 8] = Utils.copyBit(sourceByte, sourceBitIndex, targetByte, targetBitIndex);
            }
        }

        return result;
    }

}
