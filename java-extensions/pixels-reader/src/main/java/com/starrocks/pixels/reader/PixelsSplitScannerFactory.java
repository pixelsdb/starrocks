package com.starrocks.pixels.reader;

import com.starrocks.jni.connector.ScannerFactory;
import com.starrocks.jni.connector.ScannerHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class PixelsSplitScannerFactory implements ScannerFactory{
    static ClassLoader classLoader;

    static {
        String basePath = System.getenv("STARROCKS_HOME");
        List<File> preloadFiles = new ArrayList<>();
        File dir = new File(basePath + "/lib/pixels-reader-lib");
        preloadFiles.addAll(Arrays.asList(Objects.requireNonNull(dir.listFiles())));
        dir = new File(basePath + "/lib/common-runtime-lib");
        preloadFiles.addAll(Arrays.asList(Objects.requireNonNull(dir.listFiles())));
        classLoader = ScannerHelper.createChildFirstClassLoader(preloadFiles, "pixels scanner");
    }

    @Override
    public Class getScannerClass() throws ClassNotFoundException {
        try {
            return classLoader.loadClass("com.starrocks.pixels.reader.PixelsSplitScanner");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw e;
        }
    }
}
