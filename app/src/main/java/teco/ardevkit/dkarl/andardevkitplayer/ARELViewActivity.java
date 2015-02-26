// Copyright 2007-2013 Metaio GmbH. All rights reserved.
package teco.ardevkit.dkarl.andardevkitplayer;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Window;
import android.widget.Toast;

import com.metaio.sdk.ARELActivity;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;

import teco.ardevkit.dkarl.andardevkitplayer.network.TCPThread;
import teco.ardevkit.dkarl.andardevkitplayer.network.UDPThread;

public class ARELViewActivity extends ARELActivity 
{
    private UDPThread udpThread;
    private TCPThread tcpThread;
    private String log = "ARELVIEWActivity-log";
    private long maxProgressFileSize = 0;
    private int oldProgress = 0;
    private static Context context;

	@Override
	protected int getGUILayout() 
	{
		// Attaching layout to the activity
		return R.layout.template;
	}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_PROGRESS);
        context = this;
    }

    public static Context getContext() {
        return context;
    }

    @Override
    protected void onResume() {
        setProgressBarVisibility(false);
        Log.d(log, "ARELViewActivity.onResume");
        super.onResume();
        if (udpThread == null) {
            udpThread = new UDPThread();
            udpThread.start();
        }
        if (tcpThread == null) {
            tcpThread = new TCPThread(this);
            tcpThread.start();
        }
    }

    @Override
    protected void loadContents() {
        Log.d(log, "ARELVIEWActivity.loadContents");
        super.loadContents();

    }

    @Override
    protected void onStop() {
        Log.d(log, "ARELViewActivity.onPause");
        super.onPause();
        udpThread.running = false;
        udpThread.interrupt();
        udpThread = null;
        tcpThread.running = false;
        tcpThread.interrupt();
        tcpThread = null;
    }

    public boolean loadNewProject() {
        String appname = getResources().getString(R.string.app_name);
        final File projectsFolder = new File(Environment.getExternalStorageDirectory()
                + "/" + appname);
        String[] projects = projectsFolder.list(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                File toTest = new File(file.getAbsolutePath() + "/" + s);
                if (toTest.isDirectory()) {
                    return true;
                }

                return false;
            }
        });

        Arrays.sort(projects);

        String newsetProjectPath = projectsFolder.getAbsolutePath() + "/" + projects[projects.length - 1];

        if (projects.length > 2) {
            String oldestProjectPath = projectsFolder.getAbsolutePath() + "/" + projects[0];
            File oldestProjectFolder = new File(oldestProjectPath);
            try {
                FileUtils.deleteDirectory(new File(oldestProjectPath));
            } catch (IOException e) {
                e.printStackTrace();
            }
            MediaScannerConnection.scanFile(getApplicationContext(), new String[]{oldestProjectFolder.getAbsolutePath()}, null, null);
        }

        mARELInterpreter.loadARELFile(newsetProjectPath + "/arelConfig.xml");


        return true;
    }

    public void reportNewProjectIncoming() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setProgressBarVisibility(true);
                Toast t = Toast.makeText(getApplicationContext(),
                        getResources().getString(R.string.indicate_projectLoading),
                        Toast.LENGTH_LONG);
                t.show();
            }
        });
    }

    public void reportMaxProgress(final long l) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setProgressBarIndeterminate(false);
            }
        });
        oldProgress = 0;
        maxProgressFileSize = l;
    }

    public void reportProgress(final long l) {
        final int maxProgress = 10000;
        final float currentProgress = (float) l / (float) maxProgressFileSize;
        final int progressInt = (int) (currentProgress * maxProgress);
        if (progressInt > oldProgress) {
            oldProgress = progressInt;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    if (currentProgress < 1)
                        setProgress(progressInt);
                    else {
                        setProgressBarIndeterminate(true);
                    }
                }
            });
        }




    }
}
