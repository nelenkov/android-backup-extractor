package org.nick.abe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AndroidBackupTest {

    @TempDir
    Path tempDir;

    @Test
    void roundTripUnencryptedV1() throws Exception {
        byte[] payload = samplePayload();
        Path tarIn = tempDir.resolve("in.tar");
        Path abFile = tempDir.resolve("backup.ab");
        Path tarOut = tempDir.resolve("out.tar");
        Files.write(tarIn, payload);

        AndroidBackup.packTar(tarIn.toString(), abFile.toString(), null, false);
        AndroidBackup.extractAsTar(abFile.toString(), tarOut.toString(), null);

        assertArrayEquals(payload, Files.readAllBytes(tarOut));
    }

    @Test
    void roundTripUnencryptedV2() throws Exception {
        byte[] payload = samplePayload();
        Path tarIn = tempDir.resolve("in.tar");
        Path abFile = tempDir.resolve("backup-v2.ab");
        Path tarOut = tempDir.resolve("out.tar");
        Files.write(tarIn, payload);

        AndroidBackup.packTar(tarIn.toString(), abFile.toString(), "", true);
        AndroidBackup.extractAsTar(abFile.toString(), tarOut.toString(), null);

        assertArrayEquals(payload, Files.readAllBytes(tarOut));
    }

    @Test
    void roundTripEncryptedV1AsciiPassword() throws Exception {
        byte[] payload = samplePayload();
        Path tarIn = tempDir.resolve("in.tar");
        Path abFile = tempDir.resolve("enc-v1.ab");
        Path tarOut = tempDir.resolve("out.tar");
        Files.write(tarIn, payload);

        AndroidBackup.packTar(tarIn.toString(), abFile.toString(), "s3cr3tPass!", false);
        AndroidBackup.extractAsTar(abFile.toString(), tarOut.toString(), "s3cr3tPass!");

        assertArrayEquals(payload, Files.readAllBytes(tarOut));
    }

    @Test
    void roundTripEncryptedV2KitKatNonAsciiPassword() throws Exception {
        byte[] payload = samplePayload();
        Path tarIn = tempDir.resolve("in.tar");
        Path abFile = tempDir.resolve("enc-v2-utf8.ab");
        Path tarOut = tempDir.resolve("out.tar");
        Files.write(tarIn, payload);

        String nonAsciiPassword = "pässwörd-日本語-🔐";
        AndroidBackup.packTar(tarIn.toString(), abFile.toString(), nonAsciiPassword, true);
        AndroidBackup.extractAsTar(abFile.toString(), tarOut.toString(), nonAsciiPassword);

        assertArrayEquals(payload, Files.readAllBytes(tarOut));
    }

    @Test
    void roundTripEncryptedV1NonAsciiPasswordFallback() throws Exception {
        byte[] payload = samplePayload();
        Path tarIn = tempDir.resolve("in.tar");
        Path abFile = tempDir.resolve("enc-v1-nonascii.ab");
        Path tarOut = tempDir.resolve("out.tar");
        Files.write(tarIn, payload);

        String nonAsciiPassword = "pässwörd";
        AndroidBackup.packTar(tarIn.toString(), abFile.toString(), nonAsciiPassword, false);
        AndroidBackup.extractAsTar(abFile.toString(), tarOut.toString(), nonAsciiPassword);

        assertArrayEquals(payload, Files.readAllBytes(tarOut));
    }

    @Test
    void roundTripStdinAndStdout() throws Exception {
        byte[] payload = samplePayload();
        InputStream origIn = System.in;
        PrintStream origOut = System.out;
        try {
            ByteArrayOutputStream packedOut = new ByteArrayOutputStream();
            System.setIn(new ByteArrayInputStream(payload));
            System.setOut(new PrintStream(packedOut));
            AndroidBackup.packTar("-", "-", "stdin-pass", true);

            byte[] backupBytes = packedOut.toByteArray();
            assertTrue(backupBytes.length > 0);

            ByteArrayOutputStream unpackedOut = new ByteArrayOutputStream();
            System.setIn(new ByteArrayInputStream(backupBytes));
            System.setOut(new PrintStream(unpackedOut));
            AndroidBackup.extractAsTar("-", "-", "stdin-pass");

            assertArrayEquals(payload, unpackedOut.toByteArray());
        } finally {
            System.setIn(origIn);
            System.setOut(origOut);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void extractUncompressedVersionsV1ToV5(int version) throws Exception {
        byte[] payload = samplePayload();
        Path abFile = tempDir.resolve("v" + version + "-uncompressed.ab");
        Path tarOut = tempDir.resolve("v" + version + "-out.tar");

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        String header = "ANDROID BACKUP\n" + version + "\n0\nnone\n";
        buf.write(header.getBytes(StandardCharsets.UTF_8));
        buf.write(payload);
        Files.write(abFile, buf.toByteArray());

        AndroidBackup.extractAsTar(abFile.toString(), tarOut.toString(), null);
        assertArrayEquals(payload, Files.readAllBytes(tarOut));
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5})
    void extractCompressedVersionsV3ToV5(int version) throws Exception {
        byte[] payload = samplePayload();
        Path abFile = tempDir.resolve("v" + version + "-compressed.ab");
        Path tarOut = tempDir.resolve("v" + version + "-out.tar");

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        String header = "ANDROID BACKUP\n" + version + "\n1\nnone\n";
        buf.write(header.getBytes(StandardCharsets.UTF_8));
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        try (DeflaterOutputStream dos = new DeflaterOutputStream(buf, deflater)) {
            dos.write(payload);
        } finally {
            deflater.end();
        }
        Files.write(abFile, buf.toByteArray());

        AndroidBackup.extractAsTar(abFile.toString(), tarOut.toString(), null);
        assertArrayEquals(payload, Files.readAllBytes(tarOut));
    }

    @Test
    void wrongPasswordFailsExtraction() throws Exception {
        Path tarIn = tempDir.resolve("in.tar");
        Path abFile = tempDir.resolve("enc.ab");
        Path tarOut = tempDir.resolve("out.tar");
        Files.write(tarIn, samplePayload());

        AndroidBackup.packTar(tarIn.toString(), abFile.toString(), "correct-password", true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> AndroidBackup.extractAsTar(abFile.toString(), tarOut.toString(), "wrong-password"));
        assertTrue(ex.getMessage().contains("Invalid password"));
    }

    @Test
    void emptyFileThrowsIllegalStateException() throws Exception {
        Path emptyFile = tempDir.resolve("empty.ab");
        Path tarOut = tempDir.resolve("out.tar");
        Files.write(emptyFile, new byte[0]);

        assertThrows(IllegalStateException.class,
                () -> AndroidBackup.extractAsTar(emptyFile.toString(), tarOut.toString(), null));
    }

    @Test
    void emptyStdinThrowsIllegalStateException() {
        InputStream origIn = System.in;
        Path tarOut = tempDir.resolve("out.tar");
        try {
            System.setIn(new ByteArrayInputStream(new byte[0]));
            assertThrows(IllegalStateException.class,
                    () -> AndroidBackup.extractAsTar("-", tarOut.toString(), null));
        } finally {
            System.setIn(origIn);
        }
    }

    @Test
    void invalidMagicHeaderThrowsIllegalArgumentException() throws Exception {
        Path badFile = tempDir.resolve("bad-magic.ab");
        Path tarOut = tempDir.resolve("out.tar");
        Files.write(badFile, "NOT A BACKUP\n1\n0\nnone\nhello".getBytes(StandardCharsets.UTF_8));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AndroidBackup.extractAsTar(badFile.toString(), tarOut.toString(), null));
        assertTrue(ex.getMessage().contains("Invalid backup file header magic"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    void unsupportedVersionThrowsIllegalArgumentException(int version) throws Exception {
        Path badFile = tempDir.resolve("bad-version-" + version + ".ab");
        Path tarOut = tempDir.resolve("out.tar");
        String content = "ANDROID BACKUP\n" + version + "\n0\nnone\nhello";
        Files.write(badFile, content.getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class,
                () -> AndroidBackup.extractAsTar(badFile.toString(), tarOut.toString(), null));
    }

    @Test
    void invalidCompressionFlagThrowsIllegalArgumentException() throws Exception {
        Path badFile = tempDir.resolve("bad-comp.ab");
        Path tarOut = tempDir.resolve("out.tar");
        Files.write(badFile, "ANDROID BACKUP\n1\n2\nnone\nhello".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class,
                () -> AndroidBackup.extractAsTar(badFile.toString(), tarOut.toString(), null));
    }

    @Test
    void unsupportedEncryptionAlgorithmThrowsIllegalArgumentException() throws Exception {
        Path badFile = tempDir.resolve("bad-enc.ab");
        Path tarOut = tempDir.resolve("out.tar");
        Files.write(badFile, "ANDROID BACKUP\n1\n0\nAES-128\nhello".getBytes(StandardCharsets.UTF_8));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AndroidBackup.extractAsTar(badFile.toString(), tarOut.toString(), null));
        assertTrue(ex.getMessage().contains("Unsupported encryption algorithm"));
    }

    @Test
    void oversizedHeaderLineThrowsIllegalArgumentException() throws Exception {
        Path badFile = tempDir.resolve("huge-line.ab");
        Path tarOut = tempDir.resolve("out.tar");
        byte[] huge = new byte[20 * 1024];
        Arrays.fill(huge, (byte) 'A');
        Files.write(badFile, huge);

        assertThrows(IllegalArgumentException.class,
                () -> AndroidBackup.extractAsTar(badFile.toString(), tarOut.toString(), null));
    }

    @Test
    void malformedMasterKeyBlobIsRejectedCleanly() throws Exception {
        byte[] userSalt = new byte[64];
        byte[] ckSalt = new byte[64];
        int rounds = 100;
        String password = "test";

        SecretKey userKey = AndroidBackup.androidPBKDF2(password.toCharArray(), userSalt, rounds, true);
        // nosemgrep: java.lang.security.audit.cbc-padding-oracle.cbc-padding-oracle
        Cipher mkCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        mkCipher.init(Cipher.ENCRYPT_MODE, userKey);

        // Build a truncated blob claiming IV length = 200 (unsigned byte >= 128) with only 4 bytes
        ByteArrayOutputStream blob = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(blob);
        dos.writeByte(200);
        dos.write(new byte[] {1, 2, 3, 4});
        dos.flush();
        byte[] encryptedBlob = mkCipher.doFinal(blob.toByteArray());

        String header = "ANDROID BACKUP\n2\n0\nAES-256\n"
                + AndroidBackup.toHex(userSalt) + "\n"
                + AndroidBackup.toHex(ckSalt) + "\n"
                + rounds + "\n"
                + AndroidBackup.toHex(mkCipher.getIV()) + "\n"
                + AndroidBackup.toHex(encryptedBlob) + "\n";

        Path badFile = tempDir.resolve("bad-mkblob.ab");
        Path tarOut = tempDir.resolve("out.tar");
        Files.write(badFile, header.getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalStateException.class,
                () -> AndroidBackup.extractAsTar(badFile.toString(), tarOut.toString(), password));
    }

    @Test
    void hexEncodingAndDecodingRoundTrip() {
        byte[] raw = new byte[] {0x00, 0x0F, 0x10, (byte) 0xAB, (byte) 0xFF};
        String hex = AndroidBackup.toHex(raw);
        assertEquals("000F10ABFF", hex);
        assertArrayEquals(raw, AndroidBackup.hexToByteArray(hex));
        assertArrayEquals(raw, AndroidBackup.hexToByteArray(hex.toLowerCase()));
    }

    @Test
    void hexToByteArrayRejectsOddLength() {
        assertThrows(IllegalArgumentException.class, () -> AndroidBackup.hexToByteArray("ABC"));
    }

    @Test
    void makeKeyChecksumHandlesSignedBytesConsistently() {
        byte[] keyWithHighBits = new byte[] {(byte) 0x80, (byte) 0xFF, 0x01, 0x7F};
        byte[] salt = new byte[64];
        byte[] v1Checksum = AndroidBackup.makeKeyChecksum(keyWithHighBits, salt, 100, false);
        byte[] v2Checksum = AndroidBackup.makeKeyChecksum(keyWithHighBits, salt, 100, true);
        assertEquals(32, v1Checksum.length);
        assertEquals(32, v2Checksum.length);
        assertTrue(!Arrays.equals(v1Checksum, v2Checksum));
    }

    private static byte[] samplePayload() {
        byte[] data = new byte[12 * 1024];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) ((i * 31 + 7) & 0xFF);
        }
        return data;
    }
}
