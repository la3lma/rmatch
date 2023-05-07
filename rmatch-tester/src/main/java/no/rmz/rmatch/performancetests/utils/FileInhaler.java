package no.rmz.rmatch.performancetests.utils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;


/**
 * Inhale a file into some content.
 */
public final class FileInhaler {

    /**
     * The file we should inhale.
     */
    private final File file;

    /**
     * Set up a new inhaler.
     * @param file a file. MMmh. a file.
     */
    public FileInhaler(final File file) {
        this.file = checkNotNull(file);
    }

    /**
     * Inhale the file into a string.
     * @return the content of the file as a string.
     */
    public StringSourceBuffer inhaleAsStringBuffer() {
        synchronized (file) {
            if (!file.exists()) {
                String currentWorkingDirectory = System.getProperty("user.dir");
                throw new IllegalStateException("Couldn't find file " + file + " while current working directory is " + currentWorkingDirectory);
            }
            final FileInputStream fstream = getFileInputStream();
            // Get the object of DataInputStream
            final DataInputStream in = new DataInputStream(fstream);
            final BufferedReader br =
                    new BufferedReader(new InputStreamReader(in));
            final StringBuilder sbuilder = new StringBuilder();
            try {
                String strLine;
                //Read File Line By Line
                while ((strLine = br.readLine()) != null) {
                    sbuilder.append(strLine);
                    sbuilder.append("\n");
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            } finally {
                try {
                    br.close();
                    in.close();
                    fstream.close();
                } catch (IOException ex) {
                    //noinspection ThrowFromFinallyBlock
                    throw new RuntimeException(ex);
                }
            }
            return new StringSourceBuffer(sbuilder.toString());
        }
    }

    /**
     * Open this.file and return an FileInputStream instance
     * @return the input stream instance
     */
    private FileInputStream getFileInputStream() {
        final FileInputStream fstream;
        try {
            fstream = new FileInputStream(this.file);
        } catch (FileNotFoundException ex) {
            throw new IllegalStateException("Couldn't find file " + this.file);
        }
        return fstream;
    }

    /**
     * Get the content of the file as a list of lines.
     * @return a list of lines.
     */
    public List<String> inhaleAsListOfLines() {
        synchronized (file) {
            final List<String> result = new ArrayList<>();

            inhaleIntoCollector(result::add);
            return result;
        }
    }

    /**
     * Inhale everything into a collector that is fed one
     * line at a time.
     * @param collector the collector.
     */
    public void inhaleIntoCollector(final Collector collector) {
        synchronized (file) {
            if (!file.exists()) {
                String currentDir = System.getProperty("user.dir");
                System.out.println("Current dir using System:" + currentDir);

                throw new IllegalStateException("Couldn't find file " + file + " while cwd=" + currentDir);
            }
            final FileInputStream fstream = getFileInputStream();
            // Get the object of DataInputStream
            final DataInputStream in = new DataInputStream(fstream);
            final BufferedReader br =
                    new BufferedReader(new InputStreamReader(in));
            try {
                String strLine;
                // Read File Line By Line
                while ((strLine = br.readLine()) != null) {
                    collector.add(strLine);
                }
            } catch (IOException ex) {
                throw new IllegalStateException(
                        "Couldn't handle IO exception " + ex);
            } finally {
                try {
                    br.close();
                    in.close();
                    fstream.close();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }
}
