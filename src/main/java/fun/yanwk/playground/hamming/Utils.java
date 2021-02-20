package fun.yanwk.playground.hamming;

public class Utils {
    /**
     * 判断一个整数是否为 2 的幂（包括 1 与 Integer.MIN_VALUE ）
     */
    public static boolean isPowerOf2(int i) {
        return Integer.bitCount(i) == 1; // 【2 的幂】等价于【只有一个比特为 1】
    }

    /**
     * 将指定位置的比特，赋值给另一个指定位置的比特，并返回复制后的结果
     */
    public static byte copyBit(byte source, int sourceIndex, byte target, int targetIndex) {
        final int sourceBit = 1 & (source >> sourceIndex);
        final int targetBit = 1 & (target >> targetIndex);
        if (sourceBit != targetBit) {
            return (byte) (target ^ (1 << targetIndex));
        } else {
            return target;
        }
    }

    /**
     * 从一个整型值中提取一个比特。
     * 例： getBit(0b10, 0) 返回 0;
     *
     * @param pos 比特所在位置，最右为 0
     * @return 该位置的比特（0 或 1）
     */
    public static int getBit(int i, int pos) {
        return 1 & (i >>> pos);
    }

    /**
     * 在一个字节型中，翻转一个特定位置的比特。
     *
     * @param pos 比特所在位置，最右为 0
     * @return 一个新的字节
     */
    public static byte flipBit(byte b, int pos) {
        return (byte) (b ^ (1 << pos));
    }

    /**
     * 翻转字节型数组中一个特定位置的比特。
     * 注意是直接操作数组元素，原地翻转，无返回值。
     *
     * @param bitAddr 该比特在字节数组中的全局地址。注意：视字节数组在内存中为连续地址，如： 0 表示第 0 字节的 7 号位（最左位）
     */
    public static void flipBitInArray(byte[] bytes, int bitAddr) {
        int byteIndex = bitAddr / 8;
        int bitIndex = 7 - (bitAddr % 8);
        bytes[byteIndex] = flipBit(bytes[byteIndex], bitIndex);
    }
}
