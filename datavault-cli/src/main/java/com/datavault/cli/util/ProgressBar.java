package com.datavault.cli.util;

public class ProgressBar {

    private final String taskName;
    private final long totalBytes;
    private final long startTime;
    private long transferredBytes;
    private long lastPrintTime;

    public ProgressBar(String taskName, long totalBytes) {
        this.taskName = taskName;
        this.totalBytes = totalBytes;
        this.startTime = System.currentTimeMillis();
        this.transferredBytes = 0;
        this.lastPrintTime = 0;
    }

    public synchronized void update(long bytesAdded) {
        this.transferredBytes += bytesAdded;
        long now = System.currentTimeMillis();
        
        // Refresh terminal output every 100ms or when finished
        if (now - lastPrintTime >= 100 || transferredBytes >= totalBytes) {
            this.lastPrintTime = now;
            printProgress();
        }
    }

    public void finish() {
        this.transferredBytes = totalBytes;
        printProgress();
        System.out.println();
    }

    private void printProgress() {
        int width = 30;
        double progressRatio = totalBytes > 0 ? (double) transferredBytes / totalBytes : 1.0;
        if (progressRatio > 1.0) progressRatio = 1.0;

        int filledWidth = (int) (progressRatio * width);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < width; i++) {
            if (i < filledWidth) {
                bar.append("█");
            } else {
                bar.append("░");
            }
        }

        double elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0;
        double mbTransferred = transferredBytes / (1024.0 * 1024.0);
        double mbTotal = totalBytes / (1024.0 * 1024.0);
        double speedMbps = elapsedSeconds > 0 ? mbTransferred / elapsedSeconds : 0.0;

        System.out.printf("\r%s [%s] %.1f%% (%.2f MB / %.2f MB) %.2f MB/s",
                taskName, bar, progressRatio * 100.0, mbTransferred, mbTotal, speedMbps);
        System.out.flush();
    }
}
