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
        if (!"pack".equals(mode) && !"unpack".equals(mode) && !"pack-kk".equals(mode)) {
            usage();

            System.exit(1);
        }

        boolean unpack = "unpack".equals(mode);
        String backupFilename = unpack ? args[1] : args[2];
        String tarFilename = unpack ? args[2] : args[1];
        String password = null;
        if (args.length > 3) {
            password = args[3];
        }

        if (unpack) {
            AndroidBackup.extractAsTar(backupFilename, tarFilename, password);
        } else {
            boolean isKitKat = "pack-kk".equals(mode);
            AndroidBackup.packTar(tarFilename, backupFilename, password, isKitKat);
        }

    }

    private static void usage() {
        System.out.println("Usage:");
        System.out
                .println("  unpack:\tabe unpack\t<backup.ab> <backup.tar> [password]");
        System.out
                .println("  pack:\t\tabe pack\t<backup.tar> <backup.ab> [password]");
        System.out
                .println("  pack for 4.4:\tabe pack-kk\t<backup.tar> <backup.ab> [password]");
    }

}
