package teco.ardevkit.dkarl.andardevkitplayer.network;

import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import teco.ardevkit.dkarl.andardevkitplayer.ARELViewActivity;
import teco.ardevkit.dkarl.andardevkitplayer.R;


/**
 * Created by dkarl on 29.01.15.
 */
public class TCPThread extends Thread {
    private ServerSocket serverSocket;
    private final int port = ARELViewActivity.getContext().getResources().getInteger(R.integer.networkPort);
    public volatile boolean running = true;
    private Socket socket;
    private BufferedInputStream inputStream;
    private BufferedOutputStream outputStream;
    private Reader inputReader;
    private Writer outputWriter;
    private ARELViewActivity main;
    private String log = "TCPThread";


    public TCPThread(ARELViewActivity main) {
        super();
        this.main = main;
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void interrupt() {
        super.interrupt();
        try {
            if (socket != null)
                socket.close();

            if (serverSocket != null)
                serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        while (running) {
            byte[] buffer = new byte[8];
            try {
                socket = serverSocket.accept();
                inputStream = new BufferedInputStream(socket.getInputStream());
                outputStream = new BufferedOutputStream(socket.getOutputStream());
                inputReader = new InputStreamReader(inputStream);
                outputWriter = new OutputStreamWriter(outputStream);
                int msgLength = inputStream.read(buffer, 0, buffer.length);
                if (msgLength == -1) {
                   Log.e(log, "incoming message length was 0");
                }
                String incomingStr = new String(buffer);
                if (incomingStr.contains("project"))
                    handleProjectRequests(socket);
                if (incomingStr.contains("debug"))
                    handleDebugRequests(socket);

                if (socket.isConnected()) {
                    String s = new String("OK");
                    outputWriter.write(s);
                    outputWriter.flush();
                }
                socket.close();

            } catch (IOException e) {
                Log.e(log, "Error in communication", e);
                e.printStackTrace();
            }
        }
        try {
            if (socket != null) {
                socket.close();
            }
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.e(log, "Error in shutdown", e);
        }

    }

    private int unsignedToBytes(byte b) {
        return b & 0xFF;
    }

    private void handleProjectRequests(Socket socket) {
        main.reportNewProjectIncoming();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH_mm_ss");
        long currentTime = System.currentTimeMillis();
        String currentTimeStr = sdf.format(new Date(currentTime));

        String appname = ARELViewActivity.getContext().getString(R.string.app_name);
        File programFolder = new File(Environment.getExternalStorageDirectory() + "/" + appname);
        File projectZip = new File(programFolder.getAbsolutePath() + "/currentproject.zip");
        File projectFolder = new File(programFolder.getAbsolutePath() + "/" + currentTimeStr + "/");
        if (projectZip.exists())
            projectZip.delete();

        byte[] msgSizeByte = new byte[8];
        int msgSize = 0;

        try {
            InputStream incoming = inputStream;
            msgSize = incoming.read(msgSizeByte, 0, msgSizeByte.length);
            msgSize = 0;
            for (int i = 0; i < msgSizeByte.length; i++) {
                msgSize += unsignedToBytes(msgSizeByte[i]) << (8 * i);
            }
            if (msgSize > 0) {
                byte[] zipFile = new byte[1024];
                int alreadyRead = 0;
                FileOutputStream projectZipStream = new FileOutputStream(projectZip);
                while (alreadyRead < msgSize) {
                    int currentlyRead = incoming.read(zipFile, 0, 1024);
                    if (currentlyRead != -1)
                    projectZipStream.write(zipFile, 0, currentlyRead);
                    alreadyRead += currentlyRead;
                }
                projectZipStream.flush();
                projectZipStream.close();

                Log.d(log, "writing currentProject.zip successful");

                new UnzipTask().execute(projectZip.getAbsolutePath(),
                        projectFolder.getAbsolutePath() + "/");


            }
        } catch (IOException e) {
            e.printStackTrace();

        }




    }

    private void handleDebugRequests(Socket socket) {
        // start logcat reading until OK from editor app received
        try {
            Process process = Runtime.getRuntime().exec("logcat -c");
            process = Runtime.getRuntime().exec("logcat");
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            StringBuilder log=new StringBuilder();
            String line = "";
            boolean endLoop = false;
            do {
                if (bufferedReader.ready()) {
                    line = bufferedReader.readLine() + "\n";
                    outputWriter.write(line);
                    outputWriter.flush();

                }
                if (inputStream.available() >= 2) {
                    byte[] incoming = new byte[inputStream.available()];
                    inputStream.read(incoming);
                    String incomingMSG = new String(incoming);
                    if (incomingMSG.contains("OK")) {
                        endLoop = true;
                        Log.d(String.valueOf(log), "Debugcall sucessfully finished");
                    } else {
                        Log.d(String.valueOf(log), "Debugcall not sucessfully finished");
                    }
                }
            } while (!endLoop || !socket.isConnected());

        }
        catch (IOException e)  {
            e.printStackTrace();
        }

    }


    private class UnzipTask extends AsyncTask<String, Integer, Boolean> {
        private String _location;

        @Override
        protected Boolean doInBackground(String... filePaths) {
            if(android.os.Debug.isDebuggerConnected())
                android.os.Debug.waitForDebugger();
            if (filePaths.length != 2) {
                this.cancel(false);
            } else {
                try  {
                    _location = filePaths[1];
                    File newFolder = new File(_location);
                    newFolder.mkdir();
                    FileInputStream countFile = new FileInputStream(filePaths[0]);
                    ZipInputStream count = new ZipInputStream(countFile);

                    ZipEntry ze = null;
                    long length = 0;
                    while ((ze = count.getNextEntry()) != null) {
                        length+= ze.getSize();
                    }
                    count.close();

                    main.reportMaxProgress(length);

                    FileInputStream fin = new FileInputStream(filePaths[0]);
                    ZipInputStream zin = new ZipInputStream(fin);
                    ze= null;
                    long allRead = 0;
                    while ((ze = zin.getNextEntry()) != null) {
                        String filename = ze.getName().replace('\\' , '/');
                        Log.v("Decompress", "Unzipping " + filename);

                        if (filename.contains("/")) {
                            File currentFile = new File(_location + "/" + filename);
                            File parentFolder = currentFile.getParentFile();
                            if (!parentFolder.exists()) {
                                parentFolder.mkdir();
                            }
                        }

                        if(ze.isDirectory()) {
                            _dirChecker(ze.getName());
                        } else {
                            FileOutputStream fout = new FileOutputStream(_location + filename);
                            for (int c = zin.read(); c != -1; c = zin.read()) {
                                fout.write(c);
                                allRead ++;
                                main.reportProgress(allRead);
                            }

                            zin.closeEntry();
                            fout.flush();
                            fout.close();
                        }

                    }
                    zin.close();
                } catch(Exception e) {
                    Log.e("Decompress", "unzip", e);
                }

            }
            return true;
        }

        private void _dirChecker(String dir) {
            File f = new File(_location + dir);

            if(!f.isDirectory()) {
                f.mkdirs();
            }
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            main.setProgressBarVisibility(false);
            main.setProgressBarIndeterminate(false);
            if (aBoolean) {
                Log.d("ARDEVKITPlayer-unzip", "Unzip succesful");
                main.loadNewProject();
            }
            else
                Log.d("ARDEVKITPlayer-unzip", "Unzip failed");
        }

        @Override
        protected void onCancelled() {
            main.setProgressBarVisibility(false);
            main.setProgressBarIndeterminate(false);
            Log.d("ARDEVKITPlayer-unzip", "Unzip failed");
        }

        @Override
        protected void onCancelled(Boolean aBoolean) {
            main.setProgressBarVisibility(false);
            main.setProgressBarIndeterminate(false);
        }
    }


}



