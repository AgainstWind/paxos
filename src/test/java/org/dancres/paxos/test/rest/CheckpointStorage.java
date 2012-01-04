package org.dancres.paxos.test.rest;

import java.io.*;
import java.util.Arrays;
import java.util.Comparator;

public class CheckpointStorage {
    public interface WriteCheckpoint {
        public void saved();
        public OutputStream getStream() throws IOException;
    }
    
    public interface ReadCheckpoint {
        public InputStream getStream() throws IOException;
    }
    
    private final File _dir;

    public CheckpointStorage(File aDirectory) {
        _dir = aDirectory;
        _dir.mkdirs();
    }

    public ReadCheckpoint getLastCheckpoint() {
        final File[] myFiles = getFiles();
        
        return ((myFiles.length == 0) ? null : new ReadCheckpoint() {
            private InputStream _stream;

            public InputStream getStream() throws IOException {
                synchronized (this) {
                    if (_stream == null) {
                        _stream = new FileInputStream(myFiles[myFiles.length - 1]);                        
                    }
                    
                    return _stream;
                }
            }
        });
    }

    public WriteCheckpoint newCheckpoint() {
        return new WriteCheckpoint() {
            private File _temp;
            private FileOutputStream _stream;
            
            public void saved() {
                try {
                    _stream.getChannel().force(true);
                    _stream.close();
                } catch (Exception anE) {}

                _temp.renameTo(new File(_dir, "ckpt" + Long.toString(System.currentTimeMillis())));
                
                File[] myFiles = getFiles();
                for (int i = 0; i <= myFiles.length - 2; i++) {
                    myFiles[i].delete();
                }
            }

            public OutputStream getStream() throws IOException {
                synchronized (this) {
                    if (_temp == null) {
                        _temp = File.createTempFile("ckpt", null, _dir);
                        _stream = new FileOutputStream(_temp);
                    }
                    
                    return _stream;
                }
            }
        };
    }
    
    public File[] getFiles() {
        File[] myFiles = _dir.listFiles(new FilenameFilter() {
            public boolean accept(File file, String s) {
                return s.startsWith("ckpt");
            }
        });

        Arrays.sort(myFiles, new Comparator<File>() {
            public int compare(File file, File file1) {
                long fMod = file.lastModified();
                long f1Mod = file1.lastModified();
                
                if (fMod < f1Mod)
                    return -1;
                else if (fMod > f1Mod)
                    return 1;
                else
                    return 0;
            }

            public boolean equals(Object o) {
                return o.getClass().equals(this.getClass());
            }
        });

        return myFiles;
    }
    
    public static void main(String[] anArgs) throws Exception {
        CheckpointStorage myStorage = new CheckpointStorage(new File(anArgs[0]));
        
        File[] myFiles = myStorage.getFiles();
        
        for (int i = 0; i < myFiles.length; i++)
            System.out.println(myFiles[i]);
        
        WriteCheckpoint myCkpt = myStorage.newCheckpoint();
        ObjectOutputStream myOOS = new ObjectOutputStream(myCkpt.getStream());
        myOOS.writeObject(new Integer(55));
        myOOS.close();
        myCkpt.saved();
        
        ReadCheckpoint myRestore = myStorage.getLastCheckpoint();
        ObjectInputStream myOIS = new ObjectInputStream(myRestore.getStream());
        System.out.println(myOIS.readObject());
        myOIS.close();
    }
}
