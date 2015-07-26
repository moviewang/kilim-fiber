package kilim.tools;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
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
     */
    public void done() {
        // really writing to the war file, in fact a merging from the temp dir
        // writing to war
        // // listing temp dir files in jar entry naming style
        Map<String, File> tempDirFiles = listTempDirInJarEntryNamingStyle();
        // // create temp war
        File tempWar = File.createTempFile(this.warFile.getName(), null);
        // // merging write to the temp war
        JarOutputStream jos = new JarOutputStream(new FileOutputStream(tempWar));
        JarFile jf = new JarFile(this.warFile);
        Enumeration<JarEntry> iter = jf.entries();
        while (iter.hasMoreElements()) {
            JarEntry e = iter.nextElement();
            String name = e.getName();
            // prefer file in dir to war
            if (tempDirFiles.containsKey(name)) {
                // TODO
            } else {
            }
        }
        // // writing remained files in dir
        // // replace the target war using the temp war
        // clean
        // // cleaning temp dir
    }

    private static void addEntry(File source, JarOutputStream target) throws IOException {
        BufferedInputStream in = null;
        try {
            if (source.isDirectory()) {
                String name = source.getPath().replace("\\", "/");
                if (!name.isEmpty()) {
                    if (!name.endsWith("/"))
                        name += "/";
                    JarEntry entry = new JarEntry(name);
                    entry.setTime(source.lastModified());
                    target.putNextEntry(entry);
                    target.closeEntry();
                }
                for (File nestedFile : source.listFiles())
                    addEntry(nestedFile, target);
                return;
            }

            JarEntry entry = new JarEntry(source.getPath().replace("\\", "/"));
            entry.setTime(source.lastModified());
            target.putNextEntry(entry);
            in = new BufferedInputStream(new FileInputStream(source));

            byte[] buffer = new byte[1024];
            while (true) {
                int count = in.read(buffer);
                if (count == -1)
                    break;
                target.write(buffer, 0, count);
            }
            target.closeEntry();
        } finally {
            if (in != null)
                in.close();
        }
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

}
