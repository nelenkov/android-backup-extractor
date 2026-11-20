package org.nick.abe;

import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Key;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.PBEParametersGenerator;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.params.KeyParameter;

// mostly lifted off com.android.server.BackupManagerService.java
public class AndroidBackup {

    private static final String BACKUP_FILE_HEADER_MAGIC_LINE = "ANDROID BACKUP";
    private static final String BACKUP_FILE_HEADER_MAGIC = BACKUP_FILE_HEADER_MAGIC_LINE + "\n";
    private static final int BACKUP_FILE_V1 = 1;
    private static final int BACKUP_FILE_V2 = 2;
    private static final int BACKUP_FILE_V3 = 3;
    private static final int BACKUP_FILE_V4 = 4;
    private static final int BACKUP_FILE_V5 = 5;

    private static final String ENCRYPTION_MECHANISM = "AES/CBC/PKCS5Padding";
    private static final int PBKDF2_HASH_ROUNDS = 10000;
    private static final int MAX_PBKDF2_ROUNDS = 10_000_000;
    private static final int MAX_HEADER_LINE_LENGTH = 16 * 1024;
    private static final int PBKDF2_KEY_SIZE = 256; // bits
    private static final int MASTER_KEY_SIZE = 256; // bits
    private static final int PBKDF2_SALT_SIZE = 512; // bits
    private static final String ENCRYPTION_ALGORITHM_NAME = "AES-256";
    private static final String ENCRYPTION_ALGORITHM_NONE = "none";

    private static final boolean DEBUG = false;

    private static final SecureRandom random = new SecureRandom();

    private AndroidBackup() {
    }

    public static void extractAsTar(String backupFilename, String filename,
            String password) {
        try {
            boolean isStdin = "-".equals(backupFilename);
            // To prevent the NumberFormatException when trying to figure out the backup version
            if (!isStdin && Files.size(Paths.get(backupFilename)) == 0) {
                throw new IllegalStateException("File too small in size");
            }

            try (InputStream rawInStream = getInputStream(backupFilename)) {
                CipherInputStream cipherStream = null;

                String magic = readRequiredHeaderLine(rawInStream, "magic"); // 1
                if (DEBUG) {
                    System.err.println("Magic: " + magic);
                }
                if (!BACKUP_FILE_HEADER_MAGIC_LINE.equals(magic)) {
                    throw new IllegalArgumentException(
                            "Invalid backup file header magic: " + magic);
                }

                String versionStr = readRequiredHeaderLine(rawInStream, "version"); // 2
                if (DEBUG) {
                    System.err.println("Version: " + versionStr);
                }
                int version = Integer.parseInt(versionStr);
                if (version < BACKUP_FILE_V1 || version > BACKUP_FILE_V5) {
                    throw new IllegalArgumentException(
                            "Don't know how to process version " + versionStr);
                }

                String compressed = readRequiredHeaderLine(rawInStream, "compression flag"); // 3
                if (!"0".equals(compressed) && !"1".equals(compressed)) {
                    throw new IllegalArgumentException(
                            "Invalid compression flag: " + compressed);
                }
                boolean isCompressed = "1".equals(compressed);
                if (DEBUG) {
                    System.err.println("Compressed: " + compressed);
                }

                String encryptionAlg = readRequiredHeaderLine(rawInStream, "encryption algorithm"); // 4
                if (DEBUG) {
                    System.err.println("Algorithm: " + encryptionAlg);
                }
                boolean isEncrypted;
                if (ENCRYPTION_ALGORITHM_NAME.equals(encryptionAlg)) {
                    isEncrypted = true;
                } else if (ENCRYPTION_ALGORITHM_NONE.equals(encryptionAlg)) {
                    isEncrypted = false;
                } else {
                    throw new IllegalArgumentException(
                            "Unsupported encryption algorithm: " + encryptionAlg);
                }

                if (isEncrypted) {
                    if (Cipher.getMaxAllowedKeyLength("AES") < MASTER_KEY_SIZE) {
                        System.err.println("WARNING: Maximum allowed key-length seems smaller than needed. "
                                + "Please check that unlimited strength cryptography is available, see README.md for details");
                    }

                    if (password == null || "".equals(password)) {
                        Console console = System.console();
                        if (console != null) {
                            System.err.println("This backup is encrypted, please provide the password");
                            char[] passwordChars = console.readPassword("Password: ");
                            if (passwordChars == null || passwordChars.length == 0) {
                                throw new IllegalArgumentException(
                                        "Backup encrypted but password not specified");
                            }
                            password = new String(passwordChars);
                            Arrays.fill(passwordChars, '\0');
                        } else {
                            throw new IllegalArgumentException(
                                    "Backup encrypted but password not specified");
                        }
                    }

                    String userSaltHex = readRequiredHeaderLine(rawInStream, "user salt"); // 5
                    byte[] userSalt = hexToByteArray(userSaltHex);
                    if (userSalt.length != PBKDF2_SALT_SIZE / 8) {
                        throw new IllegalArgumentException("Invalid salt length: "
                                + userSalt.length);
                    }

                    String ckSaltHex = readRequiredHeaderLine(rawInStream, "checksum salt"); // 6
                    byte[] ckSalt = hexToByteArray(ckSaltHex);

                    int rounds = Integer.parseInt(readRequiredHeaderLine(rawInStream, "PBKDF2 rounds")); // 7
                    if (rounds <= 0 || rounds > MAX_PBKDF2_ROUNDS) {
                        throw new IllegalArgumentException("Invalid PBKDF2 rounds: " + rounds);
                    }

                    String userIvHex = readRequiredHeaderLine(rawInStream, "user IV"); // 8
                    byte[] userIv = hexToByteArray(userIvHex);

                    String masterKeyBlobHex = readRequiredHeaderLine(rawInStream, "master key blob"); // 9
                    byte[] mkCipher = hexToByteArray(masterKeyBlobHex);

                    boolean preferUtf8 = version >= BACKUP_FILE_V2;
                    MasterKeyMaterial keyMaterial = tryDecryptMasterKey(
                            password, userSalt, ckSalt, rounds, userIv, mkCipher, preferUtf8, preferUtf8);
                    if (keyMaterial == null && !isAscii(password)) {
                        keyMaterial = tryDecryptMasterKey(
                                password, userSalt, ckSalt, rounds, userIv, mkCipher, !preferUtf8, preferUtf8);
                    }

                    if (keyMaterial != null) {
                        try {
                            Cipher c = newAesCipher();
                            IvParameterSpec ivSpec = new IvParameterSpec(keyMaterial.iv);
                            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyMaterial.masterKey, "AES"), ivSpec);
                            // Only if all of the above worked properly will 'cipherStream' be assigned
                            cipherStream = new CipherInputStream(rawInStream, c);
                        } finally {
                            keyMaterial.clear();
                        }
                    }
                }

                if (isEncrypted && cipherStream == null) {
                    throw new IllegalStateException(
                            "Invalid password or master key checksum.");
                }

                // Get input file size for percentage printing when reading from a regular file
                double fileSize = isStdin ? -1 : new File(backupFilename).length();
                double percentDone = -1;

                InputStream baseStream = isEncrypted ? cipherStream : rawInStream;
                Inflater inf = isCompressed ? new Inflater() : null;
                try {
                    InputStream in = isCompressed
                            ? new InflaterInputStream(baseStream, inf)
                            : baseStream;

                    try (InputStream dataIn = in; OutputStream out = getOutputStream(filename)) {
                        byte[] buff = new byte[10 * 1024];
                        int read;
                        long totalRead = 0; // of the input file decompressed
                        while ((read = dataIn.read(buff)) != -1) {
                            if (read == 0) {
                                continue;
                            }
                            out.write(buff, 0, read);
                            totalRead += read;
                            if (DEBUG && (totalRead % (100 * 1024) == 0)) {
                                System.err.printf("%d bytes read\n", totalRead);
                            }
                            // Log the percentage extracted when total file size is known
                            if (fileSize > 0) {
                                long bytesRead = inf == null ? totalRead : inf.getBytesRead();
                                double currentPercent = Math.round(bytesRead / fileSize * 100);
                                if (currentPercent != percentDone) {
                                    System.err.printf("%.0f%% ", currentPercent);
                                    percentDone = currentPercent;
                                }
                            }
                        }
                        out.flush();
                        System.err.printf("\n%d bytes written to %s.\n",
                                totalRead, filename);
                    }
                } finally {
                    if (inf != null) {
                        inf.end();
                    }
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static MasterKeyMaterial tryDecryptMasterKey(String password, byte[] userSalt,
            byte[] ckSalt, int rounds, byte[] userIv, byte[] mkCipher,
            boolean useUtfForUserKey, boolean preferUtfForChecksum) throws Exception {
        Cipher c = newAesCipher();
        SecretKey userKey = buildPasswordKey(password, userSalt, rounds, useUtfForUserKey);
        byte[] userKeyBytes = userKey.getEncoded();
        byte[] mkBlob = null;
        try {
            IvParameterSpec ivSpec = new IvParameterSpec(userIv);
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(userKeyBytes, "AES"), ivSpec);
            try {
                mkBlob = c.doFinal(mkCipher);
            } catch (BadPaddingException e) {
                return null;
            }

            // Parse decrypted master key blob with unsigned lengths and strict bounds checks
            int offset = 0;
            if (offset >= mkBlob.length) {
                return null;
            }
            int ivLen = mkBlob[offset++] & 0xFF;
            if (offset + ivLen > mkBlob.length) {
                return null;
            }
            byte[] iv = Arrays.copyOfRange(mkBlob, offset, offset + ivLen);
            if (DEBUG) {
                System.err.println("IV: " + toHex(iv));
            }
            offset += ivLen;

            if (offset >= mkBlob.length) {
                return null;
            }
            int mkLen = mkBlob[offset++] & 0xFF;
            if (offset + mkLen > mkBlob.length) {
                return null;
            }
            byte[] mk = Arrays.copyOfRange(mkBlob, offset, offset + mkLen);
            if (DEBUG) {
                System.err.println("MK: " + toHex(mk));
            }
            offset += mkLen;

            if (offset >= mkBlob.length) {
                Arrays.fill(mk, (byte) 0);
                return null;
            }
            int ckLen = mkBlob[offset++] & 0xFF;
            if (offset + ckLen > mkBlob.length) {
                Arrays.fill(mk, (byte) 0);
                return null;
            }
            byte[] mkChecksum = Arrays.copyOfRange(mkBlob, offset, offset + ckLen);
            if (DEBUG) {
                System.err.println("MK checksum: " + toHex(mkChecksum));
            }

            // Validate the decrypted master key against the checksum:
            // first try the algorithm matching the archive version
            byte[] calculatedCk = makeKeyChecksum(mk, ckSalt, rounds, preferUtfForChecksum);
            System.err.printf("Calculated MK checksum (use UTF-8: %s): %s\n",
                    preferUtfForChecksum, toHex(calculatedCk));
            if (!MessageDigest.isEqual(calculatedCk, mkChecksum)) {
                System.err.println("Checksum does not match.");
                // try the reverse
                boolean fallbackUtf = !preferUtfForChecksum;
                calculatedCk = makeKeyChecksum(mk, ckSalt, rounds, fallbackUtf);
                System.err.printf("Calculated MK checksum (use UTF-8: %s): %s\n",
                        fallbackUtf, toHex(calculatedCk));
            }

            if (MessageDigest.isEqual(calculatedCk, mkChecksum)) {
                return new MasterKeyMaterial(iv, mk);
            }

            Arrays.fill(mk, (byte) 0);
            return null;
        } finally {
            Arrays.fill(userKeyBytes, (byte) 0);
            if (mkBlob != null) {
                Arrays.fill(mkBlob, (byte) 0);
            }
        }
    }

    public static void packTar(String tarFilename, String backupFilename,
            String password, boolean isKitKat) {
        boolean encrypting = password != null && !"".equals(password);
        boolean compressing = true;

        StringBuilder headerbuf = new StringBuilder(1024);

        headerbuf.append(BACKUP_FILE_HEADER_MAGIC);
        // integer, no trailing \n
        headerbuf.append(isKitKat ? BACKUP_FILE_V2 : BACKUP_FILE_V1);
        headerbuf.append(compressing ? "\n1\n" : "\n0\n");

        Deflater deflater = compressing ? new Deflater(Deflater.BEST_COMPRESSION) : null;
        try (InputStream in = getInputStream(tarFilename);
                OutputStream ofstream = getOutputStream(backupFilename)) {
            OutputStream finalOutput = ofstream;
            // Set up the encryption stage if appropriate, and emit the correct header
            if (encrypting) {
                finalOutput = emitAesBackupHeader(headerbuf, finalOutput,
                        password, isKitKat);
            } else {
                headerbuf.append(ENCRYPTION_ALGORITHM_NONE).append('\n');
            }

            byte[] header = headerbuf.toString().getBytes(StandardCharsets.UTF_8);
            ofstream.write(header);

            // Set up the compression stage feeding into the encryption stage (if any)
            if (compressing) {
                // requires Java 7
                finalOutput = new DeflaterOutputStream(finalOutput, deflater, true);
            }

            try (OutputStream out = finalOutput) {
                byte[] buff = new byte[10 * 1024];
                int read;
                long totalRead = 0;
                while ((read = in.read(buff)) != -1) {
                    if (read == 0) {
                        continue;
                    }
                    out.write(buff, 0, read);
                    totalRead += read;
                    if (DEBUG && (totalRead % (100 * 1024) == 0)) {
                        System.err.printf("%d bytes written\n", totalRead);
                    }
                }
                out.flush();
                System.err.printf("%d bytes written to %s.\n", totalRead,
                        backupFilename);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (deflater != null) {
                deflater.end();
            }
        }
    }

    private static InputStream getInputStream(String filename) throws IOException {
        if ("-".equals(filename)) {
            return new NonClosingInputStream(System.in);
        }
        return new FileInputStream(filename);
    }

    private static OutputStream getOutputStream(String filename) throws IOException {
        if ("-".equals(filename)) {
            return new NonClosingOutputStream(System.out);
        }
        return new FileOutputStream(filename);
    }

    private static byte[] randomBytes(int bits) {
        byte[] array = new byte[bits / 8];
        random.nextBytes(array);

        return array;
    }

    private static OutputStream emitAesBackupHeader(StringBuilder headerbuf,
            OutputStream ofstream, String encryptionPassword, boolean useUtf8) throws Exception {
        // User key will be used to encrypt the master key.
        byte[] newUserSalt = randomBytes(PBKDF2_SALT_SIZE);
        SecretKey userKey = buildPasswordKey(encryptionPassword, newUserSalt,
                PBKDF2_HASH_ROUNDS, useUtf8);

        // the master key is random for each backup
        byte[] masterPw = new byte[MASTER_KEY_SIZE / 8];
        random.nextBytes(masterPw);
        byte[] checksumSalt = randomBytes(PBKDF2_SALT_SIZE);

        byte[] mk = null;
        byte[] blobBytes = null;
        try {
            // primary encryption of the datastream with the random key
            Cipher c = newAesCipher();
            SecretKeySpec masterKeySpec = new SecretKeySpec(masterPw, "AES");
            c.init(Cipher.ENCRYPT_MODE, masterKeySpec);
            OutputStream finalOutput = new CipherOutputStream(ofstream, c);

            // line 4: name of encryption algorithm
            headerbuf.append(ENCRYPTION_ALGORITHM_NAME);
            headerbuf.append('\n');
            // line 5: user password salt [hex]
            headerbuf.append(toHex(newUserSalt));
            headerbuf.append('\n');
            // line 6: master key checksum salt [hex]
            headerbuf.append(toHex(checksumSalt));
            headerbuf.append('\n');
            // line 7: number of PBKDF2 rounds used [decimal]
            headerbuf.append(PBKDF2_HASH_ROUNDS);
            headerbuf.append('\n');

            // line 8: IV of the user key [hex]
            Cipher mkC = newAesCipher();
            mkC.init(Cipher.ENCRYPT_MODE, userKey);

            byte[] IV = mkC.getIV();
            headerbuf.append(toHex(IV));
            headerbuf.append('\n');

            // line 9: master IV + key blob, encrypted by the user key [hex]. Blob
            // format:
            // [byte] IV length = Niv
            // [array of Niv bytes] IV itself
            // [byte] master key length = Nmk
            // [array of Nmk bytes] master key itself
            // [byte] MK checksum hash length = Nck
            // [array of Nck bytes] master key checksum hash
            //
            // The checksum is the (master key + checksum salt), run through the
            // stated number of PBKDF2 rounds
            IV = c.getIV();
            mk = masterKeySpec.getEncoded();
            byte[] checksum = makeKeyChecksum(mk,
                    checksumSalt, PBKDF2_HASH_ROUNDS, useUtf8);

            ByteArrayOutputStream blob = new ByteArrayOutputStream(IV.length
                    + mk.length + checksum.length + 3);
            DataOutputStream mkOut = new DataOutputStream(blob);
            mkOut.writeByte(IV.length);
            mkOut.write(IV);
            mkOut.writeByte(mk.length);
            mkOut.write(mk);
            mkOut.writeByte(checksum.length);
            mkOut.write(checksum);
            mkOut.flush();
            blobBytes = blob.toByteArray();
            byte[] encryptedMk = mkC.doFinal(blobBytes);
            headerbuf.append(toHex(encryptedMk));
            headerbuf.append('\n');

            return finalOutput;
        } finally {
            Arrays.fill(masterPw, (byte) 0);
            if (mk != null) {
                Arrays.fill(mk, (byte) 0);
            }
            if (blobBytes != null) {
                Arrays.fill(blobBytes, (byte) 0);
            }
        }
    }

    public static String toHex(byte[] bytes) {
        StringBuilder buff = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            buff.append(String.format("%02X", b));
        }

        return buff.toString();
    }

    private static String readRequiredHeaderLine(InputStream in, String fieldName) throws IOException {
        String line = readHeaderLine(in);
        if (line == null) {
            throw new IllegalStateException("Unexpected end of stream while reading " + fieldName);
        }
        return line;
    }

    private static String readHeaderLine(InputStream in) throws IOException {
        int c;
        boolean readAny = false;
        StringBuilder buffer = new StringBuilder(80);
        while ((c = in.read()) >= 0) {
            readAny = true;
            if (c == '\n') {
                break; // consume and discard the newlines
            }
            if (buffer.length() >= MAX_HEADER_LINE_LENGTH) {
                throw new IllegalArgumentException("Backup header line exceeds maximum allowed length");
            }
            buffer.append((char) c);
        }
        return readAny ? buffer.toString() : null;
    }

    public static byte[] hexToByteArray(String digits) {
        final int bytes = digits.length() / 2;
        if (2 * bytes != digits.length()) {
            throw new IllegalArgumentException(
                    "Hex string must have an even number of digits");
        }

        byte[] result = new byte[bytes];
        for (int i = 0; i < digits.length(); i += 2) {
            result[i / 2] = (byte) Integer.parseInt(digits.substring(i, i + 2),
                    16);
        }
        return result;
    }

    public static byte[] makeKeyChecksum(byte[] pwBytes, byte[] salt, int rounds, boolean useUtf8) {
        if (DEBUG) {
            System.err.println("key bytes: " + toHex(pwBytes));
            System.err.println("salt bytes: " + toHex(salt));
        }

        // Intentional sign extension ((char) byte) to match AOSP BackupManagerService's
        // key-to-char conversion quirk across both pre-KitKat and KitKat+ backups.
        char[] mkAsChar = new char[pwBytes.length];
        for (int i = 0; i < pwBytes.length; i++) {
            mkAsChar[i] = (char) pwBytes[i];
        }
        if (DEBUG) {
            System.err.printf("MK as string: [%s]\n", new String(mkAsChar));
        }

        try {
            Key checksum = buildCharArrayKey(mkAsChar, salt, rounds, useUtf8);
            if (DEBUG) {
                System.err.println("Key format: " + checksum.getFormat());
            }
            return checksum.getEncoded();
        } finally {
            Arrays.fill(mkAsChar, '\0');
        }
    }

    public static SecretKey buildCharArrayKey(char[] pwArray, byte[] salt,
            int rounds, boolean useUtf8) {
        // Original code from BackupManagerService
        // this produces different results when run with Sun/Oracle Java SE
        // which apparently treats password bytes as UTF-8 (16?)
        // (the encoding is left unspecified in PKCS#5)

        return androidPBKDF2(pwArray, salt, rounds, useUtf8);
    }

    public static SecretKey androidPBKDF2(char[] pwArray, byte[] salt,
            int rounds, boolean useUtf8) {
        PBEParametersGenerator generator = new PKCS5S2ParametersGenerator();
        // Android treats password bytes as ASCII, which is obviously
        // not the case when an AES key is used as a 'password'.
        // Use the same method for compatibility.

        // Android 4.4 however uses all char bytes
        // useUtf8 needs to be true for KitKat
        byte[] pwBytes = useUtf8 ? PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(pwArray)
                : PBEParametersGenerator.PKCS5PasswordToBytes(pwArray);
        try {
            generator.init(pwBytes, salt, rounds);
            KeyParameter params = (KeyParameter) generator
                    .generateDerivedParameters(PBKDF2_KEY_SIZE);

            return new SecretKeySpec(params.getKey(), "AES");
        } finally {
            Arrays.fill(pwBytes, (byte) 0);
        }
    }

    private static SecretKey buildPasswordKey(String pw, byte[] salt, int rounds, boolean useUtf8) {
        char[] pwChars = pw.toCharArray();
        try {
            return buildCharArrayKey(pwChars, salt, rounds, useUtf8);
        } finally {
            Arrays.fill(pwChars, '\0');
        }
    }

    private static Cipher newAesCipher() throws Exception {
        // AES/CBC/PKCS5Padding is mandated by the Android `adb backup` file format.
        // nosemgrep: java.lang.security.audit.cbc-padding-oracle.cbc-padding-oracle
        return Cipher.getInstance(ENCRYPTION_MECHANISM);
    }

    private static boolean isAscii(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }

    private static final class MasterKeyMaterial {
        final byte[] iv;
        final byte[] masterKey;

        MasterKeyMaterial(byte[] iv, byte[] masterKey) {
            this.iv = iv;
            this.masterKey = masterKey;
        }

        void clear() {
            Arrays.fill(iv, (byte) 0);
            Arrays.fill(masterKey, (byte) 0);
        }
    }

    private static final class NonClosingInputStream extends FilterInputStream {
        NonClosingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() {
            // Do not close System.in
        }
    }

    private static final class NonClosingOutputStream extends FilterOutputStream {
        NonClosingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            out.flush();
            // Do not close System.out
        }
    }

}
