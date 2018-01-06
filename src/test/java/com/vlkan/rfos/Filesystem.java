package com.vlkan.rfos;

import java.io.File;

public enum Filesystem {;

    private static final String TMP_DIR_PROPERTY = "java.io.tmpdir";

    private static final File TMP_DIR = new File(System.getProperty(TMP_DIR_PROPERTY));

    static {
        checkTmpDir();
    }

    private static void checkTmpDir() {
        String message = null;
        if (!TMP_DIR.exists()) {
            message = "does not exist";
        } else if (!TMP_DIR.canWrite()) {
            message = "is not writable";
        }
        if (message != null) {
            String extendedMessage = String.format("%s=%s %s", TMP_DIR_PROPERTY, TMP_DIR, message);
            throw new RuntimeException(extendedMessage);
        }
    }

    public static File tmpDir() {
        return TMP_DIR;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void delete(String pathname) {
        new File(pathname).delete();
    }

}
