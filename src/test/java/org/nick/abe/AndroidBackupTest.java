package org.nick.abe;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static org.fest.assertions.api.Assertions.assertThat;

/**
 * User: lars
 */
public class AndroidBackupTest {
  private static final String PASSWORD = "12345";
  private static final String SRC_TEST_RESOURCES = "src/test/resources/";
  private final static File TAR = new File(SRC_TEST_RESOURCES + "test.tar");
  private final static File ENCRYPTED_BACKUP = new File(SRC_TEST_RESOURCES + "test.encrypted.backup");
  private final static File INVALID_ENCRYPTED_BACKUP = new File(SRC_TEST_RESOURCES + "test.encrypted_invalidHeader.backup");
  private final static File INVALID_ENCRYPTED_BACKUP2 = new File(SRC_TEST_RESOURCES + "test.encrypted_invalidHeader2.backup");
  private final static File UNENCRYPTED_COMPRESSED_BACKUP = new File(SRC_TEST_RESOURCES + "test.unencrypted.backup");
  private final static File UNENCRYPTED_UNCOMPRESSED_BACKUP = new File(SRC_TEST_RESOURCES + "uncompressed.backup");
  private final static File INVALID_UNENCRYPTED_UNCOMPRESSED_BACKUP = new File(SRC_TEST_RESOURCES + "invalid_header.uncompressed.unencrypted.backup");

  @Test
  public void shouldPackTarUnencryptedCompressing() throws IOException {
    File destinationFile = File.createTempFile("foo", "bar");

    new AndroidBackup().packTar(AndroidBackupTest.TAR.getAbsolutePath(), destinationFile.getAbsolutePath(), "");

    assertThat(destinationFile).hasContentEqualTo(UNENCRYPTED_COMPRESSED_BACKUP);
  }

  @Test
  public void shouldPackTarUnencryptedNotCompressing() throws IOException {
    File destinationFile = File.createTempFile("foo", "bar");

    AndroidBackup backup = new AndroidBackup();
    backup.setCompressing(false);
    backup.packTar(AndroidBackupTest.TAR.getAbsolutePath(), destinationFile.getAbsolutePath(), "");

    assertThat(destinationFile).hasContentEqualTo(UNENCRYPTED_UNCOMPRESSED_BACKUP);
  }

  @Test
  public void shouldPackTarEncrypted() throws IOException {
    File destinationFile = File.createTempFile("test", "backup");
    File extractedTemp = File.createTempFile("extracted", "tar");

    AndroidBackup backup = new AndroidBackup();
    backup.packTar(AndroidBackupTest.TAR.getAbsolutePath(), destinationFile.getAbsolutePath(), PASSWORD);
    backup.extractAsTar(destinationFile.getAbsolutePath(), extractedTemp.getAbsolutePath(), PASSWORD);

    assertThat(extractedTemp).hasContentEqualTo(TAR);
  }

  @Test
  public void shouldExtractAsTarUnencrypted() throws IOException {
    File destinationFile = File.createTempFile("foo", "bar");

    AndroidBackup backup = new AndroidBackup();
    backup.extractAsTar(UNENCRYPTED_COMPRESSED_BACKUP.getAbsolutePath(), destinationFile.getAbsolutePath(), "");

    assertThat(destinationFile).hasContentEqualTo(TAR);
  }

  @Test
  public void shouldExtractAsTarEncrypted() throws IOException {
    File destinationFile = File.createTempFile("foo", "bar");

    new AndroidBackup().extractAsTar(ENCRYPTED_BACKUP.getAbsolutePath(), destinationFile.getAbsolutePath(), PASSWORD);

    assertThat(destinationFile).hasContentEqualTo(TAR);
  }

  @Test
  public void shouldFailOnMissingPassword() throws IOException {
    File destinationFile = File.createTempFile("foo", "bar");

    try {
      new AndroidBackup().extractAsTar(ENCRYPTED_BACKUP.getAbsolutePath(), destinationFile.getAbsolutePath(), "");
    } catch (Exception e) {
      assertThat(e.getCause()).hasMessage("Backup encrypted but password not specified");
    }
  }

  @Test
  public void shouldFailOnInvalidHeader() throws IOException {
    File destinationFile = File.createTempFile("foo", "bar");

    try {
      new AndroidBackup().extractAsTar(INVALID_UNENCRYPTED_UNCOMPRESSED_BACKUP.getAbsolutePath(), destinationFile.getAbsolutePath(), "");
    } catch (Exception e) {
      assertThat(e.getCause()).hasMessage("Don't know how to process version 2");
    }
  }

  @Test
  public void shouldExtractAsTarUnencryptedUncompressed() throws IOException {
    File destinationFile = File.createTempFile("foo", "bar");

    AndroidBackup backup = new AndroidBackup();
    backup.setCompressing(false);
    backup.extractAsTar(UNENCRYPTED_UNCOMPRESSED_BACKUP.getAbsolutePath(), destinationFile.getAbsolutePath(), "");

    assertThat(destinationFile).hasContentEqualTo(TAR);
  }

  @Test(expected = NullPointerException.class)
  public void shouldFailOnExtractAsTarEncryptedWithPasswordIsNull() throws IOException {
    File destinationFile = File.createTempFile("foo", "bar");

    new AndroidBackup().extractAsTar(ENCRYPTED_BACKUP.getAbsolutePath(), destinationFile.getAbsolutePath(), null);
  }

  @Test
  public void shouldFailOnPackEncryptedWithPasswordIsNull() throws IOException {
    File destinationFile = File.createTempFile("foo", "bar");
    try {
      new AndroidBackup().packTar(AndroidBackupTest.TAR.getAbsolutePath(), destinationFile.getAbsolutePath(), null);
    } catch (Exception e) {
      assertThat(e.getCause()).hasMessage("Backup encrypted but password not specified");
    }
  }

  @Test
  public void shouldFailOnExtractAsTarEncryptedWithPasswordIsEmpty() throws IOException {
    File destinationFile = File.createTempFile("foo", "bar");
    try {
      new AndroidBackup().extractAsTar(ENCRYPTED_BACKUP.getAbsolutePath(), destinationFile.getAbsolutePath(), "");
    } catch (Exception e) {
      assertThat(e.getCause()).hasMessage("Backup encrypted but password not specified");
    }
  }

  @Test
  public void shouldFailOnExtractAsTarEncryptedWithInvalidPassword() throws IOException {
    File destinationFile = File.createTempFile("foo", "bar");
    try {
      new AndroidBackup().extractAsTar(ENCRYPTED_BACKUP.getAbsolutePath(), destinationFile.getAbsolutePath(), "x");
    } catch (Exception e) {
      assertThat(e.getCause()).hasMessage("Given final block not properly padded");
    }
  }

  @Test
  public void shouldFailOnExtractAsTarEncryptedWithInvalidHeader() throws IOException {
    File destinationFile = File.createTempFile("foo", "bar");
    try {
      new AndroidBackup().extractAsTar(INVALID_ENCRYPTED_BACKUP.getAbsolutePath(), destinationFile.getAbsolutePath(), PASSWORD);
    } catch (Exception e) {
      assertThat(e.getCause()).hasMessage("Invalid salt length: 65");
    }
  }

  @Test
  public void shouldFailOnExtractAsTarEncryptedWithInvalidHeader2() throws IOException {
    File destinationFile = File.createTempFile("foo", "bar");
    try {
      new AndroidBackup().extractAsTar(INVALID_ENCRYPTED_BACKUP2.getAbsolutePath(), destinationFile.getAbsolutePath(), PASSWORD);
    } catch (Exception e) {
      assertThat(e.getCause()).hasMessage("Invalid password or master key checksum.");
    }
  }

  @Test
  public void shouldFailReadingMissingTar() throws IOException {
    File destinationFile = File.createTempFile("foo", "bar");
    try {
      new AndroidBackup().extractAsTar("missing", destinationFile.getAbsolutePath(), "");
    } catch (Exception e) {
      assertThat(e.getCause()).hasMessageStartingWith("missing ");
    }
  }

  @Test
  public void shouldFailReadingMissingBackupFile() throws IOException {
    File destinationFile = File.createTempFile("foo", "bar");
    try {
      new AndroidBackup().packTar("missing", destinationFile.getAbsolutePath(), "");
    } catch (Exception e) {
      assertThat(e.getCause()).hasMessageStartingWith("missing ");
    }
  }

  @Test
  public void shouldReadUnencryptedBackupFromStdinAndExtractAsTar() throws IOException {
    File destinationFile = File.createTempFile("foo", "bar");

    System.setIn(new FileInputStream(UNENCRYPTED_UNCOMPRESSED_BACKUP.getAbsolutePath()));

    new AndroidBackup().extractAsTarFromStdin(destinationFile.getAbsolutePath(), "");

    assertThat(destinationFile).hasContentEqualTo(TAR);
  }

  @Test
  public void shouldReadEncryptedBackupFromStdinAndExtractAsTar() throws IOException {
    File destinationFile = File.createTempFile("foo", "bar");

    System.setIn(new FileInputStream(ENCRYPTED_BACKUP.getAbsolutePath()));

    new AndroidBackup().extractAsTarFromStdin(destinationFile.getAbsolutePath(), PASSWORD);

    assertThat(destinationFile).hasContentEqualTo(TAR);
  }
}
