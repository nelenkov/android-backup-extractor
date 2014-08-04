package org.nick.abe;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class Main {

    public static void main(String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        if (args.length < 3) {
            usage();
            System.exit(1);
        }

        String mode = args[0];

        boolean unpack = "unpack".equals(mode);
        String backupFilename = unpack ? args[1] : args[2];
        String tarFilename = unpack ? args[2] : args[1];
        String password = null;
        if (args.length > 3) {
            password = args[3];
        }


        if (mode.equals("unpack")) {
            AndroidBackup.extractAsTar(backupFilename, password, new FileOutputStream(tarFilename));
        } else if(mode.equals("pack") || mode.equals("pack-kk")) {
            boolean isKitKat = "pack-kk".equals(mode);
            AndroidBackup.packTar(new FileInputStream(tarFilename), backupFilename, password, isKitKat);
        } else if(mode.equalsIgnoreCase("addFileToBackup")){
            if(args.length < 4){
                usage();
                System.exit(-1);
            }
           AndroidBackup.addFileToBackup(args[1], args[2], args[3], args[4]);
        } else {
            usage();
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
        System.out
                .println("  addFileToBackup <backup.ab> <modifedbackup.ab> <filetoAdd> <nameInTar>");
    }

}
