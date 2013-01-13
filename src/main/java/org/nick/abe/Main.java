package org.nick.abe;

import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;

public class Main {
  private static final Logger LOG = LoggerFactory.getLogger(Main.class);

  private final AndroidBackup backup;
  private boolean isTesting = false;

  public Main(AndroidBackup backup) {
    this.backup = backup;
  }

  public static void main(String[] args) {
    new Main(new AndroidBackup()).run(args);
  }

  protected void run(String[] args) {
    LOG.debug("running with args: {}", Joiner.on(" ").join(args));

    if (args.length < 3) {
      exitWithErrorMsg("less than 3 arguments");
    } else if (args.length > 4) {
      exitWithErrorMsg("more than 4 arguments");
    } else {

      String mode = args[0];
      if (!"pack".equals(mode) && !"unpack".equals(mode)) {
        exitWithErrorMsg("need unpack or pack as first argument");
      } else {

        boolean unpack = "unpack".equals(mode);
        String backupFilename = unpack ? args[1] : args[2];
        String tarFilename = unpack ? args[2] : args[1];
        String password = args.length == 4 ? args[3] : null;

        if (unpack) {
          unpack(backupFilename, tarFilename, password);
        } else {
          pack(tarFilename, backupFilename, password);
        }
      }
    }
  }

  private void unpack(String backupFilename, String tarFilename, String password) {
    LOG.debug("unpack");
    if ("-".equals(backupFilename)) {
      LOG.debug("reading from stdin");
      if ("-".equals(tarFilename)) {
        LOG.debug("writing to stdout");
        backup.extractAsTarFromStdinToStdout(password);
      } else {
        LOG.debug("writing to file {}", tarFilename);
        backup.extractAsTarFromStdinToFile(tarFilename, password);
      }
    } else {
      LOG.debug("reading from {}", backupFilename);
      if ("-".equals(tarFilename)) {
        LOG.debug("writing to stdout");
        backup.extractAsTarFromFileToStdout(backupFilename, password);
      } else {
        LOG.debug("writing to file {}", tarFilename);
        backup.extractAsTarFromFileToFile(backupFilename, tarFilename, password);
      }
    }
  }

  private void pack(String tarFilename, String backupFilename, String password) {
    LOG.debug("pack");
    if ("-".equals(tarFilename)) {
      LOG.debug("reading from stdin");
      if ("-".equals(backupFilename)) {
        LOG.debug("writing to stdout");
        backup.packTarFromStdinToStdout(password);
      } else {
        LOG.debug("writing to file {}", backupFilename);
        backup.packTarFromStdinToFile(backupFilename, password);
      }
    } else {
      LOG.debug("reading from {}", backupFilename);
      if ("-".equals(backupFilename)) {
        LOG.debug("writing to stdout");
        backup.packTarFromFileToStdout(tarFilename, password);
      } else {
        LOG.debug("writing to file {}", backupFilename);
        backup.packTarFromFileToFile(tarFilename, backupFilename, password);
      }
    }
  }

  private void exitWithErrorMsg(String msg) {
    LOG.debug(msg);
    usage();
    LOG.debug("exiting");
    if (!isTesting) {
      System.exit(1);
    }
  }

  protected void usage() {
    PrintStream out = System.out;
    out.println("Android backup extractor");
    out.println("Usage:");
    out.println("\tunpack:");
    out.println("\t\tabe unpack <backup.ab> <backup.tar> [password]");
    out.println("\tpack:");
    out.println("\t\tabe pack <backup.tar> <backup.ab> [password]");
    out.println("");
    out.println("\ttips:");
    out.println("\t\tin case of <backup.tar> is - stdin will be read from/written to stdout");
    out.println("\t\tin case of <backup.ab>  is - stdin will be read from/written to stdout");
    out.println();
    out.println("\t\t cat x.tar | ab pack - - > x.ab");
  }

  protected void setTesting(boolean testing) {
    isTesting = testing;
  }
}
