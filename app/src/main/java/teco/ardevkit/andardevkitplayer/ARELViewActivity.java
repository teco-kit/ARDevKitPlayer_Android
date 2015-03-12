// Copyright 2007-2013 Metaio GmbH. All rights reserved.
package teco.ardevkit.andardevkitplayer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.metaio.sdk.ARELActivity;
import com.metaio.sdk.MetaioDebug;
import com.metaio.sdk.jni.Camera;
import com.metaio.sdk.jni.CameraVector;
import com.metaio.sdk.jni.IMetaioSDKAndroid;
import com.metaio.sdk.jni.IMetaioSDKCallback;
import com.metaio.sdk.jni.ImageStruct;
import com.metaio.sdk.jni.Vector2di;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import teco.ardevkit.andardevkitplayer.network.TCPThread;
import teco.ardevkit.andardevkitplayer.network.UDPThread;

public class ARELViewActivity extends ARELActivity 
{
    private UDPThread udpThread;
    private TCPThread tcpThread;
    private String log = "ARELVIEWActivity-log";
    private long maxProgressFileSize = 0;
    private int oldProgress = 0;
    private static Context context;
    private MenuItem pauseItem;
    private boolean isPaused;
    private boolean stateLoaded;
    private Menu mMenu;
    private Handler uiHandler = new Handler();
    private boolean requestImageSave = false;
    private File tempPauseImage;
    private Activity activity = this;
    private String stateSavePath;
    IMetaioSDKCallback mSDKCallback;

	@Override
	protected int getGUILayout() 
	{
		// Attaching layout to the activity
		return R.layout.template;
	}

    @Override
    protected IMetaioSDKCallback getMetaioSDKCallbackHandler() {
        return mSDKCallback;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSDKCallback = new MetaioSDKCallbackHandler();
        this.requestWindowFeature(Window.FEATURE_PROGRESS);
        context = this;
        stateSavePath = Environment.getExternalStorageDirectory()
                + "/" + getResources().getString(R.string.app_name) + "/states/";
        tempPauseImage = new File(stateSavePath + "temp/pause.jpg");
        if (!tempPauseImage.getParentFile().exists()) {
            tempPauseImage.getParentFile().mkdirs();
        }
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
        metaioSDK.registerCallback(mSDKCallback);
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


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        mMenu = menu;
        pauseItem = menu.findItem(R.id.pause);
        if (isPaused || stateLoaded) {
            pauseItem.setIcon(android.R.drawable.ic_media_pause);
            pauseItem.setTitle(R.string.resume);
        }
        // fill snapshot preview items with shots
        File saveFolder = new File(stateSavePath);
        final List<String> imageNames = new ArrayList<String>(Arrays.asList(saveFolder.list(new FilenameFilter() {

            @Override
            public boolean accept(File dir, String filename) {
                if (filename.endsWith(".jpg")) {
                    return true;
                }
                return false;
            }
        })));

        // set up snapshot/manage button
        MenuItem saveload = menu.findItem(R.id.saveLoadMenu);
        LinearLayout saveLoadButtonBase = (LinearLayout) saveload.getActionView();
        saveLoadButtonBase.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ImageButton saveLoadButton = (ImageButton) saveLoadButtonBase.getChildAt(0);
        saveLoadButton.setScaleType(ImageView.ScaleType.CENTER);
        saveLoadButton.setOnClickListener(new SaveOnClickListener(uiHandler));
        saveLoadButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                StateManagerDialog statemanager = new StateManagerDialog(activity, metaioSDK);
                statemanager.show();
                return true;
            }
        });

        java.util.Collections.sort(imageNames);
        for (int i = 0; i < 5 && i < imageNames.size(); i++) {
            String myJpgPath = saveFolder.getAbsolutePath() + "/" + imageNames.get(imageNames.size() - 1 - i);
            BitmapDrawable snapshot = new BitmapDrawable(getResources(), myJpgPath);
            ImageButton currentButton;
            MenuItem snapshotMenuItem;
            switch (i) {
                case 0:
                    snapshotMenuItem = menu.findItem(R.id.snapshot1);
                    break;
                case 1:
                    snapshotMenuItem = menu.findItem(R.id.snapshot2);
                    break;
                case 2:
                    snapshotMenuItem = menu.findItem(R.id.snapshot3);
                    break;
                case 3:
                    snapshotMenuItem = menu.findItem(R.id.snapshot4);
                    break;
                case 4:
                    snapshotMenuItem = menu.findItem(R.id.snapshot5);
                    break;
                default:
                    snapshotMenuItem = menu.findItem(R.id.snapshot1);
            }
            LinearLayout lin = (LinearLayout) snapshotMenuItem.getActionView();
            currentButton = (ImageButton) lin.getChildAt(0);
            currentButton.setImageDrawable(snapshot);
            currentButton.setContentDescription(myJpgPath);
            currentButton.setOnClickListener(new OnSnapshotClickListener());
            snapshotMenuItem.setVisible(true);
            snapshotMenuItem.setEnabled(true);
        }
        return true;
    }


    @Override
    protected void startCamera() {
        final CameraVector cameras = metaioSDK.getCameraList();

        if (cameras.size() > 0) {
            Camera camera = cameras.get(0);

            // Choose back facing camera
            for (int i = 0; i < cameras.size(); i++) {
                if (cameras.get(i).getFacing() == Camera.FACE_BACK) {
                    camera = cameras.get(i);
                    break;
                }
            }
            camera.setResolution(new Vector2di(1280, 720));
            metaioSDK.startCamera(camera);
        }
    }

    private class OnSnapshotClickListener implements ImageButton.OnClickListener {
        @Override
        public void onClick(View v) {
            String fileName = v.getContentDescription().toString();
            final File toLoad = new File(fileName);
            // make metaio open said file and allow for resuming
            isPaused = false;
            pauseItem.setEnabled(false);
            mSurfaceView.queueEvent(new LoadImageContinuousRunnable(toLoad, 25));
            Log.i("State", "Previous state loaded");

            pauseItem.setIcon(android.R.drawable.ic_media_play);
            pauseItem.setTitle(R.string.resume);
            stateLoaded = true;
        }
    }


    public class StateManagerDialog extends Dialog {

        List<String> currentSelection = new ArrayList<String>();

        public StateManagerDialog(final Context context, final IMetaioSDKAndroid metaioSDK) {
            super(context);
            this.setTitle("State Manager");


            File saveFolder = new File(stateSavePath);
            final List<String> imageNames = new ArrayList<String>(Arrays.asList(saveFolder.list(new FilenameFilter() {

                @Override
                public boolean accept(File dir, String filename) {
                    if (filename.endsWith(".jpg")) {
                        return true;
                    }
                    return false;
                }
            })));
            java.util.Collections.sort(imageNames);
            this.setContentView(R.layout.statemanagerlayout);

            final ListView lv = (ListView) findViewById(R.id.stateList);


            final ArrayAdapter adapter = new StateListAdapter(context, R.layout.statemanager_listrow, imageNames);
            lv.setAdapter(adapter);


        }

        private class StateListAdapter extends ArrayAdapter<String> {
            Context context;
            int resource;
            List<String> objects;
            StateListAdapter adapter = this;

            public StateListAdapter(Context context, int resource, List<String> imageNames) {
                super(context, resource, imageNames);
                this.resource = resource;
                this.context = context;
                this.objects = imageNames;
            }

            public boolean hasStableIds() {
                return false;
            }

            @SuppressWarnings("deprecation")
            @SuppressLint("NewApi")
            @Override
            public View getView(final int position, View convertView, ViewGroup parent) {
                View row = convertView;

                // create new row layout
                LayoutInflater inflater = ((Activity) context).getLayoutInflater();
                row = inflater.inflate(resource, parent, false);
                ImageView img = (ImageView) row.findViewById(R.id.stateRowImage);
                TextView text = (TextView) row.findViewById(R.id.stateRowText);
                text.setText(objects.get(position));
                text.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        final File toLoad = new File(stateSavePath + objects.get(position));
                        // make metaio open said file and allow for resuming
                        isPaused = false;
                        pauseItem.setEnabled(false);
                        mSurfaceView.queueEvent(new LoadImageContinuousRunnable(toLoad, 25));
                        Log.i("State", "Previous state loaded");

                        pauseItem.setIcon(android.R.drawable.ic_media_play);
                        pauseItem.setTitle(R.string.resume);
                        stateLoaded = true;
                        dismiss();
                    }
                });
                Bitmap bmap = BitmapFactory.decodeFile(stateSavePath + objects.get(position));
                img.setImageBitmap(bmap);
                ImageButton deleteButton = (ImageButton) row.findViewById(R.id.deleteButton);
                deleteButton.setOnClickListener(new ImageButton.OnClickListener() {

                    @Override
                    public void onClick(View v) {

                        File toDelete = new File(stateSavePath + objects.get(position));
                        toDelete.delete();
                        objects.remove(position);
                        adapter.notifyDataSetChanged();
                        adapter.notifyDataSetInvalidated();

                        uiHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {

                                activity.invalidateOptionsMenu();
                            }
                        }, 500);

                        Log.i("State", "Previous state deleted");
                    }
                });


                return row;
            }

        }


    }

    private class SaveOnClickListener implements View.OnClickListener {
        Handler handler;

        public SaveOnClickListener(Handler handler) {
            super();
            this.handler = handler;
        }

        @Override
        public void onClick(View view) {
            // generate file path for current state
            if (!isPaused) {
                requestImageSave = true;
                metaioSDK.requestCameraImage();
            } else {
                Long currentTime = System.currentTimeMillis();
                SimpleDateFormat formatter = new SimpleDateFormat(
                        "yyyy-MM-dd-HH:mm:ss");
                String currentTimestamp = formatter.format(new Date(currentTime));
                final File toSave = new File(stateSavePath + currentTimestamp.toString() + ".jpg");
                tempPauseImage.renameTo(toSave);

            }
            Toast.makeText(activity, "Current state saved", Toast.LENGTH_SHORT).show();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    invalidateOptionsMenu();
                }
            }, 700);
        }
    }

    /**
     * Function called when pause/resume button is clicked in menubar
     *
     * @param item the MenuItem that was clicked. Always the Pause/Resume item
     */

    public void handlePause(MenuItem item) {
        if (!stateLoaded) {
            if (!isPaused) {
                item.setIcon(android.R.drawable.ic_media_play);
                item.setTitle(R.string.resume);
                this.isPaused = true;
                metaioSDK.requestCameraImage();
            } else {
                item.setIcon(android.R.drawable.ic_media_pause);
                item.setTitle(R.string.pause);
                this.isPaused = false;
                this.startCamera();
            }
        } else {
            item.setIcon(android.R.drawable.ic_media_pause);
            item.setTitle(R.string.pause);
            this.isPaused = false;
            this.stateLoaded = false;
            mSurfaceView.queueEvent(new Runnable() {
                @Override
                public void run() {
                    startCamera();
                }
            });
        }

    }

    final class MetaioSDKCallbackHandler extends IMetaioSDKCallback {


        @Override
        public void onCameraImageSaved(String filepath) {
            super.onCameraImageSaved(filepath);
        }

        @Override
        public void onSDKReady() {
            super.onSDKReady();
            Log.d("M-DEBUGTEST", "Custom callback created, SDK ready");
        }

        @Override
        public void onNewCameraFrame(ImageStruct cameraFrame) {
            MetaioDebug.log("a new camera frame image is delivered"
                    + cameraFrame.getTimestamp());
            Long currentTime = System.currentTimeMillis();
            SimpleDateFormat formatter = new SimpleDateFormat(
                    "yyyy-MM-dd-HH:mm:ss");
            String currentTimestamp = formatter.format(new Date(currentTime));
            final File toSave = new File(stateSavePath + currentTimestamp.toString() + ".jpg");


            try {
                if (!requestImageSave) {
                    OutputStream fOutputStream = new FileOutputStream(tempPauseImage);

                    cameraFrame.getBitmap().compress(Bitmap.CompressFormat.JPEG, 100, fOutputStream);

                    fOutputStream.flush();
                    fOutputStream.close();
                } else {
                    OutputStream fOutputStream = new FileOutputStream(toSave);

                    cameraFrame.getBitmap().compress(Bitmap.CompressFormat.JPEG, 100, fOutputStream);

                    fOutputStream.flush();
                    fOutputStream.close();

                    requestImageSave = false;

                }


                if (isPaused) {
                    pauseItem.setEnabled(false);
                    mSurfaceView.queueEvent(new LoadImageContinuousRunnable(tempPauseImage, 25));
                }


            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    private class LoadImageContinuousRunnable implements Runnable {

        private File file;
        private int times;

        LoadImageContinuousRunnable(File toLoad, int times) {
            this.file = toLoad;
            this.times = times;
        }

        @Override
        public void run() {
            if (times > 0) {
                metaioSDK.setImage(file.getAbsolutePath());
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mSurfaceView.queueEvent(new LoadImageContinuousRunnable(file, --times));
                    }
                });
            } else {
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        pauseItem.setEnabled(true);
                    }
                });

            }
        }
    }

}
