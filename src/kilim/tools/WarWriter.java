package kilim.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

/**
 * Utility to write to a jar/war file.
 * 
 * @author <a href="mailto:miles.wy.1@gmail.com">pf_miles</a>
 * 
 */
public class WarWriter {

    // the war file to write at last
    private File warFile;
    // the temp directory to pre-write...
    private File tempDir;

    private static byte[] buf = new byte[1048576];// the writing buffer

    /**
     * create a war writer upon a war file... should also works for a jar file
     * 
     * @param warPath
     *            the absolute path of the underlying war file
     */
    public WarWriter(String warPath) {
        File f = new File(warPath);
        if (!f.exists())
            throw new RuntimeException("War file does not exist: " + warPath);
        // test if zip format
        JarInputStream i = null;
        try {
            i = new JarInputStream(new FileInputStream(f));
            if (i.getNextEntry() == null) {
                throw new RuntimeException("Not jar/war format: " + warPath);
            }
        } catch (Exception e) {
            throw new RuntimeException("Not jar/war format: " + warPath);
        } finally {
            try {
                if (i != null)
                    i.close();
            } catch (IOException e) {
            }
        }
        this.warFile = f;
        // create temp directory
        this.tempDir = createTempDirectory(f.getName());
    }

    private static File createTempDirectory(String warName) {
        final File temp;

        try {
            temp = File.createTempFile(warName, Long.toString(System.currentTimeMillis()));
            temp.deleteOnExit();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (!(temp.delete())) {
            throw new RuntimeException("Could not delete temp file: " + temp.getAbsolutePath());
        }

        if (!(temp.mkdir())) {
            throw new RuntimeException("Could not create temp directory: " + temp.getAbsolutePath());
        }

        return (temp);
    }

    /**
     * Complete writing, rebuild the final result jar/war file and do cleaning.
     * 
     * @throws IOException
     */
    public void done() throws IOException {
        // really writing to the war file, in fact a merging from the temp dir
        // writing to war
        // // listing temp dir files in jar entry naming style
        Map<String, File> tempDirFiles = listFilesInJarEntryNamingStyle(this.tempDir, this.tempDir.getAbsolutePath());
        // // create temp war
        File tempWar = File.createTempFile(this.warFile.getName(), null);
        // // merging write to the temp war
        JarOutputStream jos = new JarOutputStream(new FileOutputStream(tempWar));
        JarFile jf = new JarFile(this.warFile);
        Enumeration<JarEntry> iter = jf.entries();
        while (iter.hasMoreElements()) {
            JarEntry e = iter.nextElement();
            String name = e.getName();
            if (!e.isDirectory() && name.endsWith(".jar")) {
                writeJarEntry(e, filterByDirName(tempDirFiles, name), jf, jos);
            } else {
                // prefer file in dir to war
                InputStream fin = null;
                if (tempDirFiles.containsKey(name)) {
                    File f = tempDirFiles.get(name);
                    if (!e.isDirectory())
                        fin = new FileInputStream(f);
                    addEntry(name, fin, f.lastModified(), jos);
                    tempDirFiles.remove(name);
                } else {
                    if (!e.isDirectory())
                        fin = jf.getInputStream(e);
                    addEntry(name, fin, e.getTime(), jos);
                }
            }
        }
        jf.close();
        // // writing remained files in dir
        for (Map.Entry<String, File> remain : tempDirFiles.entrySet()) {
            String dirFileName = remain.getKey();
            File dirFile = remain.getValue();
            InputStream in = null;
            if (!dirFile.isDirectory())
                in = new FileInputStream(dirFile);
            addEntry(dirFileName, in, dirFile.lastModified(), jos);
        }
        // // replace the target war using the temp war
        jos.close();
        tempWar.renameTo(warFile);
        // clean
        // // cleaning temp dir
        recurDel(this.tempDir);
    }

    /*
     * list the files&dirs in the specified dir, in a jar entry naming style: 1)
     * all file names come with no preceding '/' 2) all file names of
     * directories must be suffixed by a '/'
     */
    private static Map<String, File> listFilesInJarEntryNamingStyle(File f, String basePath) {
        Map<String, File> ret = new HashMap<String, File>();
        String name = f.getAbsolutePath().substring(basePath.length());
        if (name.startsWith("/"))
            name = name.substring(1);
        if (f.isDirectory()) {
            if (!name.endsWith("/"))
                name += "/";
            for (File sub : f.listFiles()) {
                ret.putAll(listFilesInJarEntryNamingStyle(sub, basePath));
            }
        }
        // add the current level directory itself except for the root dir
        if (!"/".equals(name))
            ret.put(name, f);
        return ret;
    }

    private static void recurDel(File file) {
        if (file.isDirectory()) {
            for (File item : file.listFiles())
                recurDel(item);
        }
        file.delete();
    }

    // merging write jar entry
    private void writeJarEntry(JarEntry origJarEntry, Map<String, File> mergingFiles, JarFile origWar, JarOutputStream targetWarStream)
            throws IOException {
        // if there's no merging file for this jar entry, write the original jar
        // data directly
        if (mergingFiles == null || mergingFiles.isEmpty()) {
            JarEntry je = new JarEntry(origJarEntry.getName());
            je.setTime(origJarEntry.getTime());
            targetWarStream.putNextEntry(je);
            flowTo(origWar.getInputStream(origJarEntry), targetWarStream);
            targetWarStream.closeEntry();
        } else {
            String origJarEntryName = origJarEntry.getName();
            long modTime = -1;
            String mergingDirName = origJarEntryName + "/";
            if (mergingFiles.containsKey(mergingDirName)) {
                modTime = mergingFiles.get(mergingDirName).lastModified();
            } else {
                modTime = origJarEntry.getTime();
            }

            JarEntry je = new JarEntry(origJarEntryName);
            je.setTime(modTime);
            targetWarStream.putNextEntry(je);

            mergingFiles.remove(mergingDirName);

            // build the jar data
            String jarSimpleName = origJarEntryName.contains("/") ? origJarEntryName.substring(origJarEntryName.lastIndexOf("/") + 1)
                    : origJarEntryName;
            // // build the tmp jar file to write to
            File tmpOutputJarFile = File.createTempFile(jarSimpleName, null);
            JarOutputStream tmpOutputJar = new JarOutputStream(new FileOutputStream(tmpOutputJarFile));

            // // dump the original jar file to iterate over
            File tmpOrigJarFile = buildTempOrigJarFile(jarSimpleName + "_orig", origWar.getInputStream(origJarEntry));
            JarFile tmpOrigJar = new JarFile(tmpOrigJarFile);

            for (Enumeration<JarEntry> e = tmpOrigJar.entries(); e.hasMoreElements();) {
                JarEntry origJarItemEntry = e.nextElement();
                String origJarItemEntryName = origJarItemEntry.getName();
                String mergingFileName = mergingDirName + origJarItemEntryName;
                InputStream itemIn = null;
                long itemModTime = -1;
                // prefer dir files to origJar entries
                if (mergingFiles.containsKey(mergingFileName)) {
                    File f = mergingFiles.get(mergingFileName);
                    if (!origJarItemEntry.isDirectory())
                        itemIn = new FileInputStream(f);
                    itemModTime = f.lastModified();
                    mergingFiles.remove(mergingFileName);
                } else {
                    if (!origJarItemEntry.isDirectory())
                        itemIn = tmpOrigJar.getInputStream(origJarItemEntry);
                    itemModTime = origJarItemEntry.getTime();
                }
                addEntry(origJarItemEntryName, itemIn, itemModTime, tmpOutputJar);
            }
            tmpOrigJar.close();
            tmpOrigJarFile.delete();

            // check&write remained dir files
            for (Map.Entry<String, File> remain : mergingFiles.entrySet()) {
                String dirFileName = remain.getKey();
                File dirFile = remain.getValue();
                InputStream in = null;
                if (!dirFile.isDirectory())
                    in = new FileInputStream(dirFile);
                addEntry(dirFileName.substring(mergingDirName.length()), in, dirFile.lastModified(), tmpOutputJar);
            }
            tmpOutputJar.close();

            // write to war
            InputStream jarData = new FileInputStream(tmpOutputJarFile);
            flowTo(jarData, targetWarStream);
            jarData.close();
            tmpOutputJarFile.delete();

            targetWarStream.closeEntry();
        }
    }

    // build a temp file containing the given inputStream data
    private File buildTempOrigJarFile(String name, InputStream in) throws IOException {
        File f = File.createTempFile(name, null);
        OutputStream out = new FileOutputStream(f);
        try {
            flowTo(in, out);
        } finally {
            out.close();
        }
        return f;
    }

    // data stream 'flow' from in to out, pseudo-zero-copy
    private static void flowTo(InputStream in, OutputStream out) throws IOException {
        try {
            for (int count = in.read(buf); count != -1; count = in.read(buf)) {
                out.write(buf, 0, count);
            }
        } finally {
            in.close();
        }
    }

    // collect entries which contain the specified dir path segment, and also
    // delete from the original map
    private Map<String, File> filterByDirName(Map<String, File> nameFileMapping, String pathSegment) {
        if (nameFileMapping == null || nameFileMapping.isEmpty())
            return Collections.emptyMap();
        Map<String, File> ret = new HashMap<String, File>();
        if (!pathSegment.endsWith("/"))
            pathSegment += "/";
        for (Iterator<Map.Entry<String, File>> iter = nameFileMapping.entrySet().iterator(); iter.hasNext();) {
            Map.Entry<String, File> e = iter.next();
            if (e.getKey().contains(pathSegment)) {
                ret.put(e.getKey(), e.getValue());
                iter.remove();
            }
        }
        return ret;
    }

    private static void addEntry(String entryName, InputStream in, long modTime, JarOutputStream target) throws IOException {
        JarEntry e = new JarEntry(entryName);
        e.setTime(modTime);
        target.putNextEntry(e);
        if (in != null) {
            flowTo(in, target);
        }
        target.closeEntry();
    }

    /**
     * create outputStream writing to the specified war/jar file, all paths
     * specified here are relative to the root of the war/jar.
     */
    public OutputStream getFileOutputStream(String relPath) throws IOException {
        if (relPath.startsWith("/"))
            relPath = relPath.substring(1);
        if (relPath.endsWith("/"))
            relPath = relPath.substring(0, relPath.length() - 1);
        File f = new File(this.tempDir.getAbsolutePath() + "/" + relPath);
        File p = f.getParentFile();
        if (p != null && !p.exists()) {
            p.mkdirs();
        }
        if (!f.exists())
            f.createNewFile();
        return new FileOutputStream(f);
    }

    /**
     * get the temporarily pre-writing directory
     */
    public String getTempPrewriteDir() {
        return this.tempDir.getAbsolutePath();
    }

    /**
     * return the current writing war file path
     */
    public String getWarFilePath() {
        return this.warFile.getAbsolutePath();
    }

}
