package org.nick.abe;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.*;

/**
 * User: lars
 */
public class MainTest {
  private AndroidBackup backup = mock(AndroidBackup.class);
  private Main frontend = new Main(backup);

  @Before
  public void beforeEachTest() {
    frontend.setTesting(true);
    Mockito.reset(backup);
  }

  @Test
  public void shouldUnpackWithoutPassword() throws Exception {

    frontend.run(new String[]{"unpack", "in", "out"});

    verify(backup).extractAsTarFromFileToFile("in", "out", null);
  }

  @Test
  public void shouldUnpackFromStdin() throws Exception {

    frontend.run(new String[]{"unpack", "-", "out"});

    verify(backup).extractAsTarFromStdinToFile("out", null);
  }

  @Test
  public void shouldUnpackFromStdinToStdout() throws Exception {

    frontend.run(new String[]{"unpack", "-", "-"});

    verify(backup).extractAsTarFromStdinToStdout(null);
  }

  @Test
  public void shouldUnpackFromFileToStdout() throws Exception {

    frontend.run(new String[]{"unpack", "in", "-"});

    verify(backup).extractAsTarFromFileToStdout("in", null);
  }

  @Test
  public void shouldPackFromStdinToStdout() throws Exception {

    frontend.run(new String[]{"pack", "-", "-"});

    verify(backup).packTarFromStdinToStdout(null);
  }

  @Test
  public void shouldPackFromStdinToFile() throws Exception {

    frontend.run(new String[]{"pack", "-", "out"});

    verify(backup).packTarFromStdinToFile("out", null);
  }

  @Test
  public void shouldPackFromFileToStdout() throws Exception {

    frontend.run(new String[]{"pack", "in", "-"});

    verify(backup).packTarFromFileToStdout("in", null);
  }

  @Test
  public void shouldPackFromFileToFile() throws Exception {

    frontend.run(new String[]{"pack", "in", "out"});

    verify(backup).packTarFromFileToFile("in", "out", null);
  }

  @Test
  public void shouldUnpackWithPassword() throws Exception {

    frontend.run(new String[]{"unpack", "in", "out", "pass"});

    verify(backup).extractAsTarFromFileToFile("in", "out", "pass");
  }

  @Test
  public void shouldFailWithPackAndUnpackWithPassword() throws Exception {
    frontend.run(new String[]{"unpack", "pack", "in", "out", "pass"});
    verifyNoMoreInteractions(backup);
  }

  @Test
  public void shouldFailWithWithoutPackOrUnpackWithPassword() throws Exception {
    frontend.run(new String[]{"xpack", "in", "out", "pass"});
    verifyNoMoreInteractions(backup);
  }

  @Test
  public void shouldFailWithTooFewArguments() throws Exception {
    frontend.run(new String[]{"unpack", "pack"});
    verifyNoMoreInteractions(backup);
  }

}
