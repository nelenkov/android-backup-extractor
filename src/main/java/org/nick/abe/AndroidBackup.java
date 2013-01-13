package org.nick.abe;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.crypto.PBEParametersGenerator;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.params.KeyParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.*;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import static org.fest.util.Preconditions.checkNotNull;

// mostly lifted off com.android.server.BackupManagerService.java
public class AndroidBackup {
  private static final Logger LOG = LoggerFactory.getLogger(AndroidBackup.class);

  private static final String BACKUP_FILE_HEADER_MAGIC = "ANDROID BACKUP\n";
  private static final int BACKUP_FILE_VERSION = 1;

  private static final String ALGORITHM = "AES";
  private static final String ENCRYPTION_ALGORITHM_NAME = "AES-256";
  private static final String ENCRYPTION_MECHANISM = "AES/CBC/PKCS5Padding";
  private static final int PBKDF2_HASH_ROUNDS = 10000;
  private static final int PBKDF2_KEY_SIZE = 256; // bits
  private static final int MASTER_KEY_SIZE = 256; // bits
  private static final int PBKDF2_SALT_SIZE = 512; // bits

  private static final SecureRandom random = new SecureRandom();
  private static final char NEW_LINE = '\n';

  private boolean compressOutput = true;

  public void extractAsTar(String backupFilename, String filename, String password) {
    checkNotNull(backupFilename);
    checkNotNull(filename);
    checkNotNull(password);

    try (InputStream rawInStream = new FileInputStream(backupFilename)) {
      String magic = readHeaderLine(rawInStream); // 1
      LOG.debug("Magic: {}", magic);

      String version = readHeaderLine(rawInStream); // 2
      LOG.debug("Version: {}", version);

      if (BACKUP_FILE_VERSION != Integer.parseInt(version)) {
        throw new IllegalArgumentException("Don't know how to process version " + version);
      }

      String compressed = readHeaderLine(rawInStream); // 3
      boolean isCompressed = Integer.parseInt(compressed) == 1;
      LOG.debug("Compressed: {}", compressed);

      String encryptionAlg = readHeaderLine(rawInStream); // 4
      LOG.debug("Algorithm: {}", encryptionAlg);

      boolean encrypted = encryptionAlg.equals(ENCRYPTION_ALGORITHM_NAME);

      InputStream baseStream = encrypted ? getCipherStream(password, rawInStream) : rawInStream;

      try (InputStream in = isCompressed ? new InflaterInputStream(baseStream) : baseStream) {
        try (FileOutputStream out = new FileOutputStream(filename)) {
          IOUtils.copy(in, out);
          out.flush();
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private InputStream getCipherStream(String password, InputStream rawInStream) throws IOException, DecoderException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
    if ("".equals(password)) {
      throw new IllegalArgumentException("Backup encrypted but password not specified");
    }

    String userSaltHex = readHeaderLine(rawInStream); // 5
    byte[] userSalt = Hex.decodeHex(userSaltHex.toCharArray());
    if (userSalt.length != PBKDF2_SALT_SIZE / 8) {
      throw new IllegalArgumentException("Invalid salt length: " + userSalt.length);
    }

    String ckSaltHex = readHeaderLine(rawInStream); // 6
    byte[] ckSalt = Hex.decodeHex(ckSaltHex.toCharArray());

    int rounds = Integer.parseInt(readHeaderLine(rawInStream)); // 7
    String userIvHex = readHeaderLine(rawInStream); // 8

    String masterKeyBlobHex = readHeaderLine(rawInStream); // 9

    // decrypt the master key blob
    Cipher c = Cipher.getInstance(ENCRYPTION_MECHANISM);
    SecretKey userKey = buildPasswordKey(password.toCharArray(), userSalt, rounds);
    byte[] IV = Hex.decodeHex(userIvHex.toCharArray());
    IvParameterSpec ivSpec = new IvParameterSpec(IV);
    c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(userKey.getEncoded(), ALGORITHM), ivSpec);
    byte[] mkCipher = Hex.decodeHex(masterKeyBlobHex.toCharArray());
    byte[] mkBlob = c.doFinal(mkCipher);

    // first, the master key IV
    int offset = 0;
    int len = mkBlob[offset++];
    IV = Arrays.copyOfRange(mkBlob, offset, offset + len);

    if (LOG.isDebugEnabled()) {
      LOG.debug("IV: {}", Hex.encodeHexString(IV));
    }

    offset += len;
    // then the master key itself
    len = mkBlob[offset++];
    byte[] mk = Arrays.copyOfRange(mkBlob, offset, offset + len);

    if (LOG.isDebugEnabled()) {
      LOG.debug("MK: {}", Hex.encodeHexString(mk));
    }

    offset += len;
    // and finally the master key checksum hash
    len = mkBlob[offset++];
    byte[] mkChecksum = Arrays.copyOfRange(mkBlob, offset, offset + len);

    if (LOG.isDebugEnabled()) {
      LOG.debug("MK checksum: " + Hex.encodeHexString(mkChecksum));
    }

    // now validate the decrypted master key against the checksum
    byte[] calculatedCk = makeKeyChecksum(mk, ckSalt, rounds);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Calculated MK checksum: " + Hex.encodeHexString(calculatedCk));
    }

    if (Arrays.equals(calculatedCk, mkChecksum)) {
      ivSpec = new IvParameterSpec(IV);
      c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(mk, ALGORITHM), ivSpec);
      // Only if all of the above worked properly will 'result' be assigned
      return new CipherInputStream(rawInStream, c);
    } else {
      throw new IllegalStateException("Invalid password or master key checksum.");
    }
  }

  public void packTar(String tarFilename, String backupFilename, String password) {
    boolean encrypting = password != null && !"".equals(password);

    StringBuilder headerbuf = new StringBuilder(1024);

    headerbuf.append(BACKUP_FILE_HEADER_MAGIC);
    headerbuf.append(BACKUP_FILE_VERSION); // integer, no trailing \n
    headerbuf.append(compressOutput ? "\n1\n" : "\n0\n");

    try (FileInputStream in = new FileInputStream(tarFilename)) {
      try (FileOutputStream ofstream = new FileOutputStream(backupFilename)) {

        // Set up the encryption stage if appropriate, and add the correct header
        if (encrypting) {
          try (OutputStream encOutput = getEncryptedOutputStream(password, headerbuf, ofstream)) {
            if (compressOutput) {
              try (OutputStream deflateOutput = getDeflateOutputStream(encOutput)) {
                writeOut(in, ofstream, headerbuf, deflateOutput);
              }
            } else {
              writeOut(in, ofstream, headerbuf, encOutput);
            }
          }
        } else {
          headerbuf.append("none\n");
          if (compressOutput) {
            try (OutputStream deflateOutput = getDeflateOutputStream(ofstream)) {
              writeOut(in, ofstream, headerbuf, deflateOutput);
              deflateOutput.flush();
            }
          } else {
            writeOut(in, ofstream, headerbuf, ofstream);
          }
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void writeOut(FileInputStream in, OutputStream ofstream, StringBuilder headerbuf, OutputStream highLevelOut) throws IOException {
    byte[] header = headerbuf.toString().getBytes("UTF-8");
    ofstream.write(header);
    IOUtils.copy(in, highLevelOut);
  }

  private OutputStream getDeflateOutputStream(OutputStream finalOutput) {
    Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
    // requires Java 7
    finalOutput = new DeflaterOutputStream(finalOutput, deflater, true);
    return finalOutput;
  }

  private OutputStream getEncryptedOutputStream(String password, StringBuilder headerbuf, FileOutputStream ofstream) throws Exception {
    OutputStream finalOutput;// User key will be used to encrypt the master key.
    byte[] newUserSalt = randomBytes(PBKDF2_SALT_SIZE);
    SecretKey userKey = buildPasswordKey(password.toCharArray(), newUserSalt, PBKDF2_HASH_ROUNDS);

    // the master key is random for each backup
    byte[] masterPw = randomBytes(MASTER_KEY_SIZE);

    // primary encryption of the datastream with the random key
    SecretKeySpec masterKeySpec = new SecretKeySpec(masterPw, ALGORITHM);
    Cipher cipher = Cipher.getInstance(ENCRYPTION_MECHANISM);
    cipher.init(Cipher.ENCRYPT_MODE, masterKeySpec);
    finalOutput = new CipherOutputStream(ofstream, cipher);

    StringBuilder backupHeader = addAesBackupHeader(newUserSalt, userKey, cipher, masterKeySpec);
    headerbuf.append(backupHeader.toString());
    return finalOutput;
  }

  private StringBuilder addAesBackupHeader(byte[] newUserSalt, SecretKey userKey, Cipher cipher, SecretKeySpec masterKeySpec) throws Exception {
    StringBuilder backupHeader = new StringBuilder();

    byte[] checksumSalt = randomBytes(PBKDF2_SALT_SIZE);

    // line 4: name of encryption algorithm
    backupHeader.append(ENCRYPTION_ALGORITHM_NAME);
    backupHeader.append(NEW_LINE);
    // line 5: user password salt [hex]
    backupHeader.append(Hex.encodeHexString(newUserSalt));
    backupHeader.append(NEW_LINE);
    // line 6: master key checksum salt [hex]
    backupHeader.append(Hex.encodeHexString(checksumSalt));
    backupHeader.append(NEW_LINE);
    // line 7: number of PBKDF2 rounds used [decimal]
    backupHeader.append(PBKDF2_HASH_ROUNDS);
    backupHeader.append(NEW_LINE);

    // line 8: IV of the user key [hex]
    Cipher mkC = Cipher.getInstance(ENCRYPTION_MECHANISM);
    mkC.init(Cipher.ENCRYPT_MODE, userKey);

    backupHeader.append(Hex.encodeHexString(mkC.getIV()));
    backupHeader.append(NEW_LINE);

    // line 9: master IV + key blob, encrypted by the user key [hex].  Blob format:
    //    [byte] IV length = Niv
    //    [array of Niv bytes] IV itself
    //    [byte] master key length = Nmk
    //    [array of Nmk bytes] master key itself
    //    [byte] MK checksum hash length = Nck
    //    [array of Nck bytes] master key checksum hash
    //
    // The checksum is the (master key + checksum salt), run through the
    // stated number of PBKDF2 rounds
    byte[] IV = cipher.getIV();
    byte[] mk = masterKeySpec.getEncoded();
    byte[] checksum = makeKeyChecksum(masterKeySpec.getEncoded(), checksumSalt, PBKDF2_HASH_ROUNDS);

    ByteArrayOutputStream blob = new ByteArrayOutputStream(IV.length + mk.length + checksum.length + 3);

    try (DataOutputStream mkOut = new DataOutputStream(blob)) {
      mkOut.writeByte(IV.length);
      mkOut.write(IV);
      mkOut.writeByte(mk.length);
      mkOut.write(mk);
      mkOut.writeByte(checksum.length);
      mkOut.write(checksum);
      mkOut.flush();
    }

    byte[] encryptedMk = mkC.doFinal(blob.toByteArray());
    backupHeader.append(Hex.encodeHexString(encryptedMk));
    backupHeader.append(NEW_LINE);
    return backupHeader;
  }

  private String readHeaderLine(InputStream in) throws IOException {
    int c;
    StringBuilder buffer = new StringBuilder(80);
    while ((c = in.read()) >= 0) {
      if (c == NEW_LINE) {
        break; // consume and discard the newlines
      }
      buffer.append((char) c);
    }
    return buffer.toString();
  }

  private byte[] makeKeyChecksum(byte[] pwBytes, byte[] salt, int rounds) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("key bytes: " + Hex.encodeHexString(pwBytes));
      LOG.debug("salt bytes: " + Hex.encodeHexString(salt));
    }

    char[] mkAsChar = new char[pwBytes.length];
    for (int i = 0; i < pwBytes.length; i++) {
      mkAsChar[i] = (char) pwBytes[i];
    }

    LOG.debug("MK as string: [{}]\n", String.valueOf(mkAsChar));

    Key checksum = buildPasswordKey(mkAsChar, salt, rounds);
    LOG.debug("Key format: {}", checksum.getFormat());

    return checksum.getEncoded();
  }

  private SecretKey buildPasswordKey(char[] pwArray, byte[] salt, int rounds) {
    // Original code from BackupManagerService
    // this produces different results when run with Sun/Oracale Java SE
    // which apparently treats password bytes as UTF-8 (16?)
    // (the encoding is left unspecified in PKCS#5)

    //        try {
    //            SecretKeyFactory keyFactory = SecretKeyFactory
    //                    .getInstance("PBKDF2WithHmacSHA1");
    //            KeySpec ks = new PBEKeySpec(pwArray, salt, rounds, PBKDF2_KEY_SIZE);
    //            return keyFactory.generateSecret(ks);
    //        } catch (InvalidKeySpecException e) {
    //            throw new RuntimeException(e);
    //        } catch (NoSuchAlgorithmException e) {
    //            throw new RuntimeException(e);
    //        } catch (NoSuchProviderException e) {
    //            throw new RuntimeException(e);
    //        }
    //        return null;

    PBEParametersGenerator generator = new PKCS5S2ParametersGenerator();
    generator.init(
        // PBEParametersGenerator.PKCS5PasswordToUTF8Bytes(pwArray),
        // Android treats password bytes as ASCII, which is obviously
        // not the case when an AES key is used as a 'password'.
        // Use the same method for compatibility.
        PBEParametersGenerator.PKCS5PasswordToBytes(pwArray), salt, rounds);
    KeyParameter params = (KeyParameter) generator.generateDerivedParameters(PBKDF2_KEY_SIZE);

    return new SecretKeySpec(params.getKey(), ALGORITHM);
  }

  private byte[] randomBytes(int bits) {
    byte[] array = new byte[bits / 8];
    random.nextBytes(array);
    return array;
  }

  public void setCompressing(boolean compressing) {
    this.compressOutput = compressing;
  }
}
