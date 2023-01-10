/**
 *  Copyright (C) 2002-2022   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.common.util;

import java.awt.DisplayMode;
import java.awt.EventQueue;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.MouseInfo;
import java.awt.Toolkit;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;


/**
 * Collection of small static helper methods.
 */
public class Utils {

    private static final Logger logger = Logger.getLogger(Utils.class.getName());

    /** Hex constant digits for get/restoreRandomState. */
    private static final String HEX_DIGITS = "0123456789ABCDEF";


    /**
     * Check if two objects are equal but also checks for null.
     *
     * @param <T> The object type.
     * @param one First object to compare
     * @param two Second object to compare
     * @return True if the arguments are either both null or equal in the
     *     sense of their equals() method.
     */
    public static <T> boolean equals(T one, T two) {
        return (one == null) ? (two == null) : one.equals(two);
    }

    /**
     * Get a hash code for an object, even null.
     *
     * @param object The {@code Object} to use.
     * @return A hash code.
     */
    public static int hashCode(Object object) {
        return (object == null) ? 31 : object.hashCode();
    }

    /**
     * Get the internal state of a random number generator as a
     * string.  It would have been more convenient to simply return
     * the current seed, but unfortunately it is private.
     *
     * @param random A pseudo-random number source.
     * @return A {@code String} encapsulating the object state.
     * @exception IOException is the byte stream output breaks.
     */
    public static synchronized String getRandomState(Random random)
        throws IOException {
        /* @net.ringoz.GwtIncompatible
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(random);
            oos.flush();
        }
        byte[] bytes = bos.toByteArray();
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(HEX_DIGITS.charAt((b >> 4) & 0x0F));
            sb.append(HEX_DIGITS.charAt(b & 0x0F));
        }
        return sb.toString();
        */ return "";
    }

    /**
     * Restore a previously saved state.
     *
     * @param state The saved state (@see #getRandomState()).
     * @return The restored {@code Random}.
     */
    public static synchronized Random restoreRandomState(String state) {
        /* @net.ringoz.GwtIncompatible
        if (state == null || state.isEmpty()) return null;
        byte[] bytes = new byte[state.length() / 2];
        int pos = 0;
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) HEX_DIGITS.indexOf(state.charAt(pos++));
            bytes[i] <<= 4;
            bytes[i] |= (byte) HEX_DIGITS.indexOf(state.charAt(pos++));
        }
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        try {
            ObjectInputStream ois = new ObjectInputStream(bis);
            return (Random) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            logger.log(Level.WARNING, "Unable to restore random state.", e);
        }
        return null;
        */ return new Random();
    }

    /**
     * Create a new file reader that uses UTF-8.
     *
     * @param file A {@code File} to read from.
     * @return A {@code Reader} for this file.
     */
    public static Reader getFileUTF8Reader(File file) {
        try {
            return new FileReader(file, StandardCharsets.UTF_8);
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "No input stream for " + file.getPath(),
                       ioe);
        }
        return null;
    }

    /**
     * Get the UTF-8 encoded contents of a file.
     *
     * @param file The {@code File} to query.
     * @return The contents string, or null on error.
     */
    @net.ringoz.GwtIncompatible
    public static String getUTF8Contents(File file) {
        String ret = null;
        Reader reader = getFileUTF8Reader(file);
        if (reader != null) {
            CharBuffer cb = CharBuffer.allocate((int)file.length());
            try {
                reader.read(cb);
            } catch (IOException ioe) {
                logger.log(Level.WARNING, "Read failed for " + file.getPath(),
                           ioe);
            }
            cb.flip();
            ret = cb.toString();
            try {
                reader.close();
            } catch (IOException ioe) {
                logger.log(Level.WARNING, "Failed to close", ioe);
            }
        }
        return ret;
    }
        
    /**
     * Create a new file writer that uses UTF-8.
     *
     * @param file A {@code File} to write to.
     * @param append If true, append to the file.
     * @return A {@code Writer} for this file.
     */
    private static Writer getF8W(File file, boolean append) {
        try {
            return new FileWriter(file, StandardCharsets.UTF_8, append);
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "No output stream for " + file.getName(),
                       ioe);
        }
        return null;
    }

    /**
     * Create a new file writer that uses UTF-8.
     *
     * @param file A {@code File} to write to.
     * @return A {@code Writer} for this file.
     */
    public static Writer getFileUTF8Writer(File file) {
        return getF8W(file, false);
    }

    /**
     * Create a new appending file writer that uses UTF-8.
     *
     * @param file A {@code File} to append to.
     * @return A {@code Writer} for this file.
     */
    public static Writer getFileUTF8AppendWriter(File file) {
        return getF8W(file, true);
    }
    
    /**
     * Helper to make an XML Transformer.
     *
     * @param declaration If true, include the XML declaration.
     * @param indent If true, set up the transformer to indent.
     * @return A suitable {@code Transformer}.
     */
    @net.ringoz.GwtIncompatible
    public static Transformer makeTransformer(boolean declaration,
                                              boolean indent) {
        Transformer tf = null;
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            factory.setAttribute("indent-number", Integer.valueOf(2));
            tf = factory.newTransformer();
            tf.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            tf.setOutputProperty(OutputKeys.METHOD, "xml");
            if (!declaration) {
                tf.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            }
            if (indent) {
                tf.setOutputProperty(OutputKeys.INDENT, "yes");
                tf.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            }
        } catch (TransformerException e) {
            logger.log(Level.WARNING, "Failed to install transformer!", e);
        }
        return tf;
    }

    /**
     * Delete a file.
     *
     * @param file The {@code File} to delete.
     */
    public static void deleteFile(File file) {
        try {
            if (file.isDirectory()) {
                for (File f : file.listFiles())
                    deleteFile(f);
            }
            if (!file.delete()) {
                logger.warning("Failed to delete: " + file.getPath());
            }
        } catch (SecurityException ex) {
            logger.log(Level.WARNING, "Exception deleting: "
                + file.getPath(), ex);
        }
    }
        
    /**
     * Delete a list of files.
     *
     * @param files The list of {@code File}s to delete.
     */
    public static void deleteFiles(List<File> files) {
        for (File f : files) deleteFile(f);
    }

    /**
     * Does a readable file have a matching suffix?
     *
     * @param file The {@code File} to check.
     * @param suffixes Suffixes to test.
     * @return True if any suffix matches.
     */
    public static boolean fileAnySuffix(File file, String... suffixes) {
        if (file == null || !file.isFile() || !file.canRead()) return false;
        final String name = file.getName();
        for (String s : suffixes) if (name.endsWith(s)) return true;
        return false;
    }

    /**
     * Does a directory contain the given file/s?
     *
     * @param dir The directory {@code File} to check.
     * @param names The name of the files to find therein.
     * @return True if the file is present and readable.
     */
    public static boolean directoryAllPresent(File dir, String... names) {
        if (dir == null || !dir.isDirectory() || !dir.canRead()) return false;
        for (String n : names) {
            if (!new File(dir, n).canRead()) return false;
        }
        return true;
    }

    /**
     * Delay by a number of milliseconds.
     *
     * @param ms The number of milliseconds to delay.
     * @param warning If non-null, log this warning message on interrupt,
     *     otherwise propagate the interrupt.
     */
    public static void delay(long ms, String warning) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            if (warning == null) {
                Thread.currentThread().interrupt();
            } else {
                logger.log(Level.WARNING, warning, ie);
            }
        }
    }

    public static CompletableFuture<Void> delay(long ms) {
        final boolean wasDispatchThread = EventQueue.isDispatchThread();
        return PromiseCompat.create((resolve, reject) -> {
            CompletableFuture.delayedExecutor(ms, TimeUnit.MILLISECONDS, (command) -> {
                if (!wasDispatchThread || EventQueue.isDispatchThread())
                    command.run();
                else
                    EventQueue.invokeLater(command);
            }).execute(() -> {
                resolve.accept(null);
            });
        });
    }

    /**
     * Current time since epoch in milliseconds.
     *
     * @return Time since epoch.
     */
    public static long now() {
        return System.currentTimeMillis();
    }


    /**
     * Run the garbage collector.
     *
     * Route all gc calls here, so we can disable the findbugs warning.
     */
    @SuppressFBWarnings(value="DM_GC", justification="Deliberate")
    public static void garbageCollect() {
        System.gc();
    }

    /**
     * Are we in headless mode?
     *
     * @return True if in headless mode.
     */
    public static boolean isHeadless() {
        return "true".equals(System.getProperty("java.awt.headless", "false"));
    }

    /**
     * Get a good screen device for starting FreeCol.
     *
     * @return A screen device, or null if none available
     *     (as in headless mode).
     */
    @net.ringoz.GwtIncompatible
    public static GraphicsDevice getGoodGraphicsDevice() {
        try {
            return MouseInfo.getPointerInfo().getDevice();
        } catch (HeadlessException he) {}

        try {
            final GraphicsEnvironment lge
                = GraphicsEnvironment.getLocalGraphicsEnvironment();
            return lge.getDefaultScreenDevice();
        } catch (HeadlessException he) {}
        return null;
    }
    
    /**
     * Tries to determine the DPI of the given {@code GraphicsDevice}.
     * 
     * @param gd The {@code GraphicsDevice} to determine the DPI for.
     * @return The calculated DPI.
     */
    @net.ringoz.GwtIncompatible
    public static int determineDpi(GraphicsDevice gd) {
        /*
         * getScreenResolution returns the DPI at maximum resolution -- but we might
         * currently be running with a lower resolution.
         */
        final int candidateDpi = Toolkit.getDefaultToolkit().getScreenResolution();

        final DisplayMode[] displayModes = gd.getDisplayModes();
        if (displayModes.length <= 1) {
            /*
             * There is a bug when running under KDE X11 with multiple screens
             * where getDisplayModes() return an empty array.
             */
            return candidateDpi;
        }

        final int highestScreenSize = Arrays.stream(displayModes).map(dm -> dm.getHeight()).max(Integer::compare).get();
        final int currentScreenSize = gd.getDisplayMode().getHeight();

        final int actualDpi = (candidateDpi * currentScreenSize) / highestScreenSize;
        return actualDpi;
    }
}
