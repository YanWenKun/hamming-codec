package fun.yanwk.playground.hamming;

import org.apache.commons.cli.*;

import java.io.*;

/*
 * 在学习汉明码后尝试写的编解码器（玩具级别）。
 * 在(7,4)汉明码的基础之上，增加一个全局校验位，即是(8,4)汉明码，具有“单错可纠错，双错可感知（SEC-DED）”的特性。
 * (8,4)汉明码又被称为 SEC-DED Hamming(7,4)。
 * 在 SEC-DED 的基础上，可将汉明码编码长度扩大，例如 (13,8)、(72,64)，代价是抗干扰能力下降（汉明码仅能纠错一个比特）。
 *
 * 汉明码的历史悠久，如今的应用已经不多了，但一些 ECC DRAM 仍在使用汉明码。
 * 在随机存储器中，比特翻转较为少见，而汉明码实现逻辑简单，纠错能力、编解码性能、冗余成本较为均衡。
 * 现实中的噪音常常是连续发生的（又叫脉冲干扰），因此现实中的汉明码，经常将编码后的数据打散（交织，interleaving）存储（又被称为扰码）。
 *
 * 作为演示，我将模仿 ECC DRAM，使用 (72,64) 编码，并每 8 组交织存储。
 * 即：每组汉明码（码块）长 9 字节，每 8 组（72 字节）为一套传输块，传输块内分成 8 组进行交织。
 * 对应到 ECC DRAM，就是一个内存颗粒存储一套数据的 1/9 （8 字节），一个内存 rank 有 9 个颗粒。
 * 这样，即使一个内存颗粒出现连续异常，只要其他八个颗粒能正常工作，就不会影响数据的准确性。
 *
 * 题外话：为了更好地对抗连续噪音，在通信、大容量存储领域更常采用 Reed-Solomon 编码（及其各种变体）。
 */

/**
 * 汉明码编解码器，CLI 主执行类
 *
 * @author YAN Wenkun
 * 常量：可指定不同的编码长度，可指定交织分块。
 */
public class HammingCodec {

    // 编码器编码大小
    // 令每组汉明码（码块）包含 8 字节（64 比特）有效数据，则程序通过公式计算得出：使用 (72,64) 汉明码进行编码
    private static final int ENCODER_DATA_PER_BLOCK = 8;
    // 编码器分组交织的分组数量
    // 每 8 组码块为一套传输块，进行分组交织
    private static final int ENCODER_GROUPS_PER_INTERLEAVING = 8;

    // 干扰器读写缓冲区大小
    private static final int JAMMER_BUFFER_SIZE = 512;
    // 干扰器噪音发生概率，0.01 即 1%
    private static final double JAMMER_NOISE_PROBABILITY = 0.01;

    /**
     * 利用 Apache Commons CLI 处理命令行输入参数的逻辑
     * 但是这个库实在太老了，只是拿来练练手，新的项目建议用： https://github.com/remkop/picocli
     */
    public static void main(String[] args) {

        // 阶段 1：定义参数
        Options options = new Options();
        OptionGroup modes = new OptionGroup(); // OptionGroup 中的选项是互斥的，适合用于模式选择

        Option encoderMode = Option.builder("e")
            .longOpt("encode")
            .numberOfArgs(2)
            .argName("inputFile> <outputFile")
            .desc("编码模式")
            .build();

        Option decoderMode = Option.builder("d")
            .longOpt("decode")
            .numberOfArgs(2)
            .argName("inputFile> <outputFile")
            .desc("解码模式")
            .build();

        Option distortionMode = Option.builder("x")
            .longOpt("distort")
            .numberOfArgs(2)
            .argName("inputFile> <outputFile")
            .desc("干扰模式")
            .build();

        modes.addOption(encoderMode)
            .addOption(decoderMode)
            .addOption(distortionMode);

        modes.setRequired(true);
        options.addOptionGroup(modes);

        options.addOption("h", "help", false, "显示本帮助");

        // 阶段 2：解析命令行
        CommandLine cmd;
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            formatter.printHelp("hamming-codec.jar", options);
            return;
        }

        // 阶段 3：分支判断
        if (cmd.hasOption("e") || cmd.hasOption("d") || cmd.hasOption("x")) {
            String mode = null;
            {
                if (cmd.hasOption("e")) mode = "e";
                else if (cmd.hasOption("d")) mode = "d";
                else if (cmd.hasOption("x")) mode = "x";
            }
            File fileIn = new File(cmd.getOptionValues(mode)[0]);
            File fileOut = new File(cmd.getOptionValues(mode)[1]);
            try (
                var in = new BufferedInputStream(new FileInputStream(fileIn));
                var out = new BufferedOutputStream(new FileOutputStream(fileOut))
            ) {

                if (cmd.hasOption("e")) {
                    System.out.println("Mode: Encode");
                    Encoder.encodeStream(in, out, ENCODER_DATA_PER_BLOCK, ENCODER_GROUPS_PER_INTERLEAVING);
                } else if (cmd.hasOption("d")) {
                    System.out.println("Mode: Decode");
                    Decoder.decodeStream(in, out, ENCODER_DATA_PER_BLOCK, ENCODER_GROUPS_PER_INTERLEAVING);
                } else if (cmd.hasOption("x")) {
                    System.out.println("Mode: Distort");
                    Jammer.distortStream(in, out, JAMMER_BUFFER_SIZE, JAMMER_NOISE_PROBABILITY);
                }

                in.close();
                out.close();
                printLimitedBinaryString(fileIn, 32);
                printLimitedBinaryString(fileOut, 32);
            } catch (StreamCorruptedException e) {
                System.out.println(e.getMessage());
            } catch (IOException e) {
                System.out.println("IO 错误！文件可能无法访问！");
                System.out.println(e.getMessage());
            }
        }

    }

    /**
     * 以二进制样式打印文件。
     * 每 4×8 个比特一行，每 4 行一块
     *
     * @param len 最多打印多少字节
     */
    protected static void printLimitedBinaryString(File file, int len) {
        StringBuilder sb = new StringBuilder();
        sb.append('\n').append(file.getName()).append(" :\n\n");

        try (var inputStream = new FileInputStream(file)) {
            byte[] raw = inputStream.readNBytes(len);
            for (int i = 0; i < raw.length; i++) {
                sb.append(String.format("%8s",
                    Integer.toBinaryString(Byte.toUnsignedInt(raw[i]))
                ).replace(' ', '0'));

                if (i == raw.length - 1) {
                    break;
                } else if ((i + 1) % 16 == 0) {
                    sb.append("\n\n");
                } else if ((i + 1) % 4 == 0) {
                    sb.append('\n');
                } else {
                    sb.append(' ');
                }
            }
            sb.append('\n');
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

        System.out.println(sb.toString());
    }

}
