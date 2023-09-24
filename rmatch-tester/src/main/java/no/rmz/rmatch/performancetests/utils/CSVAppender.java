package no.rmz.rmatch.performancetests.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Append CSV (Comma Separated Value) lines to files, thus making them
 * CSV files. ;-)
 */
public final class CSVAppender {

    /**
     * Utility class should have private constructor.
     */
    private CSVAppender() {
    }
    /**
     * Monitor used to synchronize access to the static methods of this class.
     */
    private static final Object MONITOR = new Object();

    /**
     * Append a logline to a file. If the file on is trying to write to does not
     * exist, one is crated, a line containing the text "secondsSinceEpoch,
     * millisDuration, memoryInMb" is written to it, and then a CSV line of
     * values is written.
     *
     * @param filePath the path to the file.
     * @param l An array of numbers to append to the file, CSV style.
     */
    public static void append(final String filePath, final long[] l) {
        synchronized (MONITOR) {
            FileWriter fileWriter = null;
            try {
                final File file = new File(filePath);
                final boolean exists = file.exists();
                fileWriter = new FileWriter(file, exists); // Append if exists

                final BufferedWriter bw = new BufferedWriter(fileWriter);

                // Write header line if file didn't already exist
                if (!exists) {
                    bw.append("secondsSinceEpoch,millisDuration,memoryInMb");
                    bw.newLine();
                }

                // Then write the logline and close things down.
                try {
                    for (int i = 0; i < l.length; i++) {
                        bw.write(Long.toString(l[i]));
                        if ((i + 1) < l.length) {
                            bw.write(", ");
                        }
                    }
                    bw.newLine();
                    bw.close();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            } finally {
                try {
                    
                    if (fileWriter != null) {
                        fileWriter.close();
                    } else {
                        //noinspection ThrowFromFinallyBlock
                        throw new RuntimeException(
                                "Something weird happening.  The file " 
                                + filePath 
                                + " resulted in a null pointer");
                    }
                } catch (IOException ex) {
                    // This should never happen. Come on!
                    // Also: This should be written to log!
                    System.err.println("This should never happen");
                    System.exit(1);
                }
            }
        }
    }
}
