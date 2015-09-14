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
import com.metaio.sdk.jni.Camera;
import com.metaio.sdk.jni.CameraVector;
import com.metaio.sdk.jni.IMetaioSDKAndroid;
import com.metaio.sdk.jni.IMetaioSDKCallback;
import com.metaio.sdk.jni.Vector2di;

import org.apache.commons.io.FileUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import teco.ardevkit.andardevkitplayer.network.TCPThread;
import teco.ardevkit.andardevkitplayer.network.UDPThread;

/**
 * Created by dkarl on 19.03.15.
 */
public class PausableARELActivity extends ARELActivity {

    private UDPThread udpThread;
    private TCPThread tcpThread;
    private String log = "ARELVIEWActivity-log";
    private long maxProgressFileSize = 0;
    private int oldProgress = 0;
    private MenuItem pauseItem;
    private boolean isPaused;
    private boolean stateLoaded;
    private Menu mMenu;
    private Handler uiHandler = new Handler();
    private File tempPauseImage;
    protected Activity activity = this;
    private String stateSavePath;
    private String defaultTrackingFile;
    private String noFuserTrackingFile;
    IMetaioSDKCallback mSDKCallback;

    @Override
    protected int getGUILayout() {
        // Attaching layout to the activity
        return R.layout.template;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_PROGRESS);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        stateSavePath = Environment.getExternalStorageDirectory()
                + "/" + getResources().getString(R.string.app_name) + "/states/";
        tempPauseImage = new File(stateSavePath + "temp/pause.jpg");
        tempPauseImage.mkdirs();
        setProgressBarVisibility(false);
        Log.d(log, "ARELViewActivity.onResume");
        if (udpThread == null) {
            udpThread = new UDPThread(this);
            udpThread.start();
        }
        if (tcpThread == null) {
            tcpThread = new TCPThread(this);
            tcpThread.start();
        }
        super.onResume();

        String currentConfig = ((File)getIntent().getSerializableExtra(getPackageName() + INTENT_EXTRA_AREL_SCENE)).getAbsolutePath();
        String currentProject = new File(currentConfig).getParent();
        createFuserLessTracking(currentProject);

    }

    @Override
    protected void onStop() {
        Log.d(log, "ARELViewActivity.onPause");
        udpThread.running = false;
        udpThread.interrupt();
        udpThread = null;
        tcpThread.running = false;
        tcpThread.interrupt();
        tcpThread = null;
        super.onStop();
    }

    public boolean loadNewProject() {
        String appname = getResources().getString(R.string.app_name);
        final File projectsFolder = new File(Environment.getExternalStorageDirectory()
                + "/" + appname);
        String[] projects = projectsFolder.list(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                File toTest = new File(file.getAbsolutePath() + "/" + s);
                if (toTest.isDirectory() && !s.equals("states")) {
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

        mARELInterpreter.loadARELFile(new File(newsetProjectPath + "/arelConfig.xml"));
        createFuserLessTracking(newsetProjectPath);


        return true;
    }

    public void reportNewProjectIncoming() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isPaused || stateLoaded) {
                    MenuItem item = mMenu.findItem(R.id.pause);
                    item.setIcon(android.R.drawable.ic_media_pause);
                    item.setTitle(R.string.pause);
                    isPaused = false;
                    stateLoaded = false;
                    mSurfaceView.queueEvent(new Runnable() {
                        @Override
                        public void run() {
                            startCamera();
                        }
                    });
                }
                setProgressBarVisibility(true);
                setProgress(0);
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
            final String fileName = v.getContentDescription().toString();
            final File toLoad = new File(fileName);
            // make metaio open said file and allow for resuming
            isPaused = false;
            metaioSDK.setTrackingConfiguration(noFuserTrackingFile);
            mSurfaceView.queueEvent(new Runnable() {
                @Override
                public void run() {
                    metaioSDK.setImage(fileName);
                }
            });
            Log.i("State", "Previous state loaded");

            pauseItem.setIcon(android.R.drawable.ic_media_play);
            pauseItem.setTitle(R.string.resume);
            stateLoaded = true;
        }
    }


    public class StateManagerDialog extends Dialog {

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
                        metaioSDK.setTrackingConfiguration(noFuserTrackingFile);
                        mSurfaceView.queueEvent(new Runnable() {
                            @Override
                            public void run() {
                                metaioSDK.setImage(toLoad.getAbsolutePath());
                            }
                        });
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
                Long currentTime = System.currentTimeMillis();
                SimpleDateFormat formatter = new SimpleDateFormat(
                        "yyyy-MM-dd-HH:mm:ss");
                String currentTimestamp = formatter.format(new Date(currentTime));
                final File toSave = new File(stateSavePath + currentTimestamp.toString() + ".jpg");
                metaioSDK.requestCameraImage(toSave.getAbsolutePath());

                //wait and do stuff
                uiHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {

                        activity.invalidateOptionsMenu();
                    }
                }, 500);

            } else {
                Long currentTime = System.currentTimeMillis();
                SimpleDateFormat formatter = new SimpleDateFormat(
                        "yyyy-MM-dd-HH:mm:ss");
                String currentTimestamp = formatter.format(new Date(currentTime));
                final File toSave = new File(stateSavePath + currentTimestamp.toString() + ".jpg");
                tempPauseImage.renameTo(toSave);
                uiHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {

                        activity.invalidateOptionsMenu();
                    }
                }, 500);

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
                metaioSDK.requestCameraImage(tempPauseImage.getAbsolutePath());
                metaioSDK.setTrackingConfiguration(noFuserTrackingFile);

                // wait and set image
                mSurfaceView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (tempPauseImage.exists())
                            metaioSDK.setImage(tempPauseImage.getAbsolutePath());
                        else
                            mSurfaceView.postDelayed(this, 100);
                    }
                }, 400);

            } else {
                item.setIcon(android.R.drawable.ic_media_pause);
                item.setTitle(R.string.pause);
                this.isPaused = false;
                metaioSDK.setTrackingConfiguration(defaultTrackingFile);
                this.startCamera();
            }
        } else {
            item.setIcon(android.R.drawable.ic_media_pause);
            item.setTitle(R.string.pause);
            this.isPaused = false;
            this.stateLoaded = false;
            metaioSDK.setTrackingConfiguration(defaultTrackingFile);
            mSurfaceView.queueEvent(new Runnable() {
                @Override
                public void run() {
                    startCamera();
                }
            });
        }

    }

    private void createFuserLessTracking(String pathToProject) {
        // take tracking config XML and convert to one that uses BestQualityFusers for every COS
        try {
            String AssetFolder = pathToProject + "/Assets";
            defaultTrackingFile = AssetFolder + "/TrackingData_Marker.xml";

            File file = new File(defaultTrackingFile);
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(file);

            // Change the content of node
            NodeList nodes = doc.getElementsByTagName("Fuser");
            for (int i = 0; i < nodes.getLength(); i++) {
                Node current = nodes.item(i);
                NamedNodeMap attr = current.getAttributes();
                Attr newFuserType = doc.createAttribute("type");
                newFuserType.setValue("BestQualityFuser");
                Node success = attr.setNamedItem(newFuserType);

            }

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");

            // initialize StreamResult with File object to save to file
            noFuserTrackingFile = AssetFolder + "/TrackingData_NoSmoothing.xml";
            StreamResult result = new StreamResult(noFuserTrackingFile);
            DOMSource source = new DOMSource(doc);
            transformer.transform(source, result);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
