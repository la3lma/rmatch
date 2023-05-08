/**
 * Copyright 2012. Bjørn Remseth (rmz@rmz.no).
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *      http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package no.rmz.rmatch.interfaces;

/**
 * Buffers are the basic way to get access to the input being parsed.
 */
public interface Buffer extends Comparable<Buffer> {

    /**
     * Return everything in the Buffer from, but not including the current
     * character as a string. XXX This may be useful for testing, but shouldn't
     * be i the main interface!
     *
     * @return the string representing the rest of the buffer.
     */
    @Deprecated
    String getCurrentRestString();

    /**
     * We need some way to get the content of the matches out, and this is one
     * way of doing it.
     *
     * @param start the first character of the substring to return.
     * @param stop The last character of the substring to return.
     * @return part of the buffer's content.
     */
    String getString(final int start, final int stop);

    /**
     * Are there any more characters after the current one?
     *
     * @return true iff more characters are available
     */
    boolean hasNext();

    /**
     * Get the next character.
     *
     * @return the next character.
     */
    Character getNext();

    /**
     * Get the current position in the buffer, to be used when getting strings
     * representing matches.
     *
     * @return an integer representing the position in a buffer.
     */
    int getCurrentPos(); // XXX Should this be a long?

    /**
     * Return a copy of the present buffer that can be modified without
     * modifying the state of the cloned original.
     *
     * @return A cloned buffer.
     */
    Buffer clone();


    private static int compoundCompare(int... results) {
        for (int result : results) {
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    @Override
    default int compareTo(Buffer other) {
        return compoundCompare(
        Integer.compare(this.getCurrentPos(), other.getCurrentPos()),
                this.getString(0, this.getCurrentPos()).compareTo(other.getString(0, other.getCurrentPos())),
                Boolean.compare(this.hasNext(), other.hasNext()));
    }
}
