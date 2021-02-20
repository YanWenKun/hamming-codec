package fun.yanwk.playground.hamming;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.IOException;

public class IntegrationTest {

    @Test
    @Disabled
    void testFileViaCLI() throws IOException {
        final String fileOriginal = this.getClass().getClassLoader().getResource("test_file.txt").getFile();
        final String fileEncoded = "target/test-classes/test_file.send";
        final String fileDistorted = "target/test-classes/test_file.recv";
        final String fileDecoded = "target/test-classes/test_file.recovered.txt";

        HammingCodec.main(new String[]{
            "--help"
        });
        HammingCodec.main(new String[]{
            "--encode", fileOriginal, fileEncoded
        });
        HammingCodec.main(new String[]{
            "--distort", fileEncoded, fileDistorted
        });
        HammingCodec.main(new String[]{
            "--decode", fileDistorted, fileDecoded
        });

        try (
            var fisOriginal = new FileInputStream(fileOriginal);
            var fisDecoded = new FileInputStream(fileDecoded)
        ) {
            var original = new String(fisOriginal.readAllBytes());
            var decoded = new String(fisDecoded.readAllBytes());
            Assertions.assertTrue(decoded.startsWith(original));
        }
    }

}
