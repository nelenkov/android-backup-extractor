package org.nick.abe;

import org.junit.Test;

import java.io.File;
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
  private final static File UNENCRYPTED_BACKUP = new File(SRC_TEST_RESOURCES + "test.unencrypted.backup");

  @Test
  public void shouldPackTarUnencrypted() throws IOException {
    File destinationFile = File.createTempFile("foo", "bar");

    AndroidBackup.packTar(TAR.getAbsolutePath(), destinationFile.getAbsolutePath(), "");

    assertThat(destinationFile).hasContentEqualTo(UNENCRYPTED_BACKUP);
  }

  @Test
  public void shouldPackTarEncrypted() throws IOException {
    File destinationFile = new File("test.backup");
    File extractedTemp = File.createTempFile("extracted","tar");

    AndroidBackup.packTar(TAR.getAbsolutePath(), destinationFile.getAbsolutePath(), PASSWORD);
    AndroidBackup.extractAsTar(destinationFile.getAbsolutePath(),extractedTemp.getAbsolutePath(),PASSWORD);

    assertThat(extractedTemp).hasContentEqualTo(TAR);
  }

  @Test
  public void shouldExtractAsTarUnencrypted() throws IOException {
    File destinationFile = File.createTempFile("foo", "bar");

    AndroidBackup.extractAsTar(UNENCRYPTED_BACKUP.getAbsolutePath(), destinationFile.getAbsolutePath(), "");

    assertThat(destinationFile).hasContentEqualTo(TAR);
  }

  @Test
  public void shouldExtractAsTarEncrypted() throws IOException {
    File destinationFile = File.createTempFile("foo", "bar");

    AndroidBackup.extractAsTar(ENCRYPTED_BACKUP.getAbsolutePath(), destinationFile.getAbsolutePath(), PASSWORD);

    assertThat(destinationFile).hasContentEqualTo(TAR);
  }

}
