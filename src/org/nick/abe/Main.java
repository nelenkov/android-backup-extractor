package org.nick.abe;

import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;


public class Main {

    public static void main(String[] args) {
        Security.addProvider(new BouncyCastleProvider());

        if (args.length < 3) {
            usage();

            System.exit(1);
        }

        String mode = args[0];
        if (!"pack".equals(mode) && !"unpack".equals(mode) && !"pack-kk".equals(mode) && !"unpack-helium".equals(mode)) {
            usage();

            System.exit(1);
        }

        String password = null;
        if (args.length > 3) {
            password = args[3];
        }

        if (password == null) {
            /* if password is not given, try to read it from environment */
            password = System.getenv("ABE_PASSWD");
        }

        if ("unpack".equals(mode)) {
            String backupFilename = args[1];
            String tarFilename = args[2];
            AndroidBackup.extractAsTar(backupFilename, tarFilename, password);
        } else if ("unpack-helium".equals(mode)) {
            String backupFilename = args[1];
            String outputDirectory = args[2];
            AndroidBackup.extractHelium(backupFilename, outputDirectory, password);
        } else {
            String backupFilename = args[2];
            String tarFilename = args[1];
            boolean isKitKat = "pack-kk".equals(mode);
            AndroidBackup.packTar(tarFilename, backupFilename, password, isKitKat);
        }

    }

    private static void usage() {
        System.out.println("Usage:");
        System.out
                .println("  unpack:\tabe unpack\t<backup.ab> <backup.tar> [password]");
        System.out
                .println("  unpack-helium:\tabe unpack-helium\t<backup.ab> outputDir [password]");
        System.out
                .println("  pack:\t\tabe pack\t<backup.tar> <backup.ab> [password]");
        System.out
                .println("  pack for 4.4:\tabe pack-kk\t<backup.tar> <backup.ab> [password]");
        System.out
                .println("If the filename is `-`, then data is read from standard input");
        System.out
                .println("or written to standard output.");
        System.out
                .println("Envvar ABE_PASSWD is tried when password is not given");
    }

}
