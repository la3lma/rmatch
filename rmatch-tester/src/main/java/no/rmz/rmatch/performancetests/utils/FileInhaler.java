/**
 * Copyright 2026. Bjørn Remseth (rmz@rmz.no).
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package no.rmz.rmatch.performancetests.utils;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.System.exit;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Inhale a file into some content. */
public final class FileInhaler {

  /** The file we should inhale. */
  private final File file;

  /**
   * Set up a new inhaler.
   *
   * @param file a file. MMmh. a file.
   */
  public FileInhaler(final File file) {
    this.file = checkNotNull(file);
  }

  /**
   * Inhale the file into a string.
   *
   * @return the content of the file as a string.
   */
  public StringSourceBuffer inhaleAsStringBuffer() {
    synchronized (file) {
      final File effectiveFile = resolveExistingFile();
      if (!effectiveFile.exists()) {
        String currentWorkingDirectory = System.getProperty("user.dir");
        throw new IllegalStateException(
            "Couldn't find file "
                + effectiveFile
                + " while current working directory is "
                + currentWorkingDirectory);
      }
      final FileInputStream fstream = getFileInputStream(effectiveFile);
      // Get the object of DataInputStream
      final DataInputStream in = new DataInputStream(fstream);
      final BufferedReader br =
          new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
      final StringBuilder sbuilder = new StringBuilder();
      try {
        String strLine;
        // Read File Line By Line
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
   *
   * @return the input stream instance
   */
  private FileInputStream getFileInputStream() {
    return getFileInputStream(resolveExistingFile());
  }

  private FileInputStream getFileInputStream(final File effectiveFile) {
    final FileInputStream fstream;
    try {
      fstream = new FileInputStream(effectiveFile);
    } catch (FileNotFoundException ex) {
      throw new IllegalStateException("Couldn't find file " + effectiveFile);
    }
    return fstream;
  }

  /**
   * Get the content of the file as a list of lines.
   *
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
   * Inhale everything into a collector that is fed one line at a time.
   *
   * @param collector the collector.
   */
  public void inhaleIntoCollector(final Collector collector) {
    synchronized (file) {
      final File effectiveFile = resolveExistingFile();
      if (!effectiveFile.exists()) {
        String currentDir = System.getProperty("user.dir");
        System.out.println("Current dir using System:" + currentDir);

        throw new IllegalStateException(
            "Couldn't find file " + effectiveFile + " while cwd=" + currentDir);
      }
      final FileInputStream fstream = getFileInputStream(effectiveFile);
      // Get the object of DataInputStream
      final DataInputStream in = new DataInputStream(fstream);
      final BufferedReader br =
          new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
      try {
        String strLine;
        // Read File Line By Line
        while ((strLine = br.readLine()) != null) {
          collector.add(strLine);
        }
      } catch (IOException ex) {
        throw new IllegalStateException("Couldn't handle IO exception " + ex);
      } finally {
        try {
          br.close();
          in.close();
          fstream.close();
        } catch (IOException ex) {
          System.err.println("This should never happen" + ex);
          exit(1);
        }
      }
    }
  }

  private File resolveExistingFile() {
    if (file.exists()) {
      return file;
    }

    final String path = file.getPath();
    final String modulePrefix = "rmatch-tester" + File.separator;
    if (path.startsWith(modulePrefix)) {
      final File withoutModulePrefix = new File(path.substring(modulePrefix.length()));
      if (withoutModulePrefix.exists()) {
        return withoutModulePrefix;
      }
    } else {
      final File withModulePrefix = new File(modulePrefix + path);
      if (withModulePrefix.exists()) {
        return withModulePrefix;
      }
    }

    return file;
  }
}
