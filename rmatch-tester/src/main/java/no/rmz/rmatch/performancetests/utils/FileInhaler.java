package no.rmz.rmatch.performancetests.utils;

import static com.google.common.base.Preconditions.checkNotNull;
import java.io.*;
import java.util.ArrayList;
import java.util.List;


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
                throw new IllegalStateException("Couldn't find file " + file);
            }
            final FileInputStream fstream;
            try {
                fstream = new FileInputStream(file);
            } catch (FileNotFoundException ex) {
                throw new IllegalStateException("Couldn't find file " + file);
            }
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
                    throw new RuntimeException(ex);
                }
            }
            return new StringSourceBuffer(sbuilder.toString());
        }
    }


    /**
     * Get the content of the file as a list of lines.
     * @return a list of lines.
     */
    public List<String> inhaleAsListOfLines() {
        synchronized (file) {
            final List<String> result = new ArrayList<String>();

            inhaleIntoCollector(new Collector() {
                @Override
                public void add(final String strLine) {
                    result.add(strLine);
                }
            });
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
                throw new IllegalStateException("Couldn't find file " + file);
            }
            final FileInputStream fstream;
            try {
                fstream = new FileInputStream(file);
            } catch (FileNotFoundException ex) {
                throw new IllegalStateException("Couldn't find file " + file);
            }
            // Get the object of DataInputStream
            final DataInputStream in = new DataInputStream(fstream);
            final BufferedReader br =
                    new BufferedReader(new InputStreamReader(in));
            try {
                String strLine;
                //Read File Line By Line
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
