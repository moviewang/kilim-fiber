package kilim.tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * classloader which allows user to set a war archive path to be included in the
 * classpath dynamically.
 * 
 * @author <a href="mailto:miles.wy.1@gmail.com">pf_miles</a>
 * 
 */
public class WarPathClassLoader extends URLClassLoader {

    private boolean warPathSet;
    private String explodedWarPath;

    public WarPathClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
    }

    public void setWarPath(String path) {
        if (this.warPathSet)
            throw new RuntimeException("War archive path already set. Kilim-fiber tools cannot weave multiple war archives at the same time.");
        File w = new File(path);
        if (w.exists()) {
            final File dir;
            try {
                warPathSet = true;
                // 0.create a temp dir
                dir = createTempDirectory(w.getName());
                // 1.extract the war to the temp dir
                extractTo(w, dir);
                // 2.add /WEB-INF/classes to cp
                File clses = locateFile(dir, "WEB-INF", "classes");
                super.addURL(clses.toURI().toURL());
                // 3.add all jars under /WEB-INF/lib/ to cp
                File lib = locateFile(dir, "WEB-INF", "lib");
                File[] jars = lib.listFiles();
                for (File j : jars)
                    super.addURL(j.toURI().toURL());
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
            // delete temp dir when exit
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                public void run() {
                    if (dir != null) {
                        delRecur(dir);
                    }
                }
            }));
            this.explodedWarPath = dir.getAbsolutePath();
        } else {
            throw new RuntimeException("File not exists: " + path);
        }
    }

    private static void delRecur(File file) {
        if (!file.exists())
            return;
        if (file.isDirectory()) {
            // 1.del sub files first
            for (File s : file.listFiles())
                delRecur(s);
            // 2.del the dir
            file.delete();
        } else {
            file.delete();
        }
    }

    private static File locateFile(File dir, String... paths) {
        File cur = dir;
        outter: for (String p : paths) {
            File[] all = cur.listFiles();
            for (File f : all) {
                if (p.equals(f.getName())) {
                    cur = f;
                    continue outter;
                }
            }
            throw new RuntimeException("No path named '" + p + "' found in file: " + cur.getAbsolutePath());
        }
        return cur;
    }

    // extract content of 'w' to dir
    private static void extractTo(File w, File dir) {
        String dirPath = dir.getAbsolutePath();
        if (!dirPath.endsWith("/"))
            dirPath += "/";
        JarFile jar = null;
        try {
            jar = new JarFile(w);
            Enumeration<JarEntry> e = jar.entries();
            byte[] buf = new byte[4096];

            while (e.hasMoreElements()) {
                JarEntry file = (JarEntry) e.nextElement();
                File f = new File(dirPath + file.getName());

                if (file.isDirectory()) { // if its a directory, create it
                    f.mkdirs();
                    continue;
                }

                InputStream is = jar.getInputStream(file);
                FileOutputStream fos = ensureOpen(f);

                // write contents of 'is' to 'fos'
                for (int avai = is.read(buf); avai != -1; avai = is.read(buf)) {
                    fos.write(buf, 0, avai);
                }
                fos.close();
                is.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (jar != null)
                try {
                    jar.close();
                } catch (IOException e) {
                }
        }
    }

    // if does not exist, create one, and ensure all parent dirs exist
    private static FileOutputStream ensureOpen(File f) throws IOException {
        if (!f.exists()) {
            File p = f.getParentFile();
            if (p != null && !p.exists())
                p.mkdirs();
            f.createNewFile();
        }
        return new FileOutputStream(f);
    }

    private static File createTempDirectory(String dirName) {
        final File temp;

        try {
            temp = File.createTempFile(dirName, Long.toString(System.currentTimeMillis()));
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
     * get the temporary directory path which contains the exploded war files
     */
    public String getExplodedWarPath() {
        if (this.explodedWarPath == null)
            throw new RuntimeException("'explodedWarPath' is null, maybe the war path is not set.");
        return this.explodedWarPath;
    }

}
