// Copyright 2007-2014 metaio GmbH. All rights reserved.
package teco.ardevkit.dkarl.andardevkitplayer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import com.metaio.sdk.MetaioDebug;
import com.metaio.tools.io.AssetsManager;

public class MainActivity extends Activity
{
    //TODO: Select the template, i.e. Native Android or AREL
    public static final boolean NATIVE = false;

    /**
     * Task that will extract all the assets
     */
    private AssetsExtracter mTask;
    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        this.context = this.getApplicationContext();

        setContentView(R.layout.main);

        // Enable metaio SDK debug log messages based on build configuration
        MetaioDebug.enableLogging(BuildConfig.DEBUG);

        // extract all the assets
        mTask = new AssetsExtracter();
        mTask.execute(0);

    }

    /**
     * This task extracts all the assets to an external or internal location
     * to make them accessible to metaio SDK
     */
    private class AssetsExtracter extends AsyncTask<Integer, Integer, Boolean>
    {

        @Override
        protected void onPreExecute()
        {
        }

        @Override
        protected Boolean doInBackground(Integer... params)
        {
            try
            {
                // Extract all assets and overwrite existing files if debug build
                AssetsManager.extractAllAssets(getApplicationContext(), BuildConfig.DEBUG);
            }
            catch (IOException e)
            {
                MetaioDebug.log(Log.ERROR, "Error extracting assets: "+e.getMessage());
                MetaioDebug.printStackTrace(Log.ERROR, e);
                return false;
            }

            return true;
        }

        @Override
        protected void onPostExecute(Boolean result)
        {
                String appname = context.getResources().getString(R.string.app_name);
                File projectFolder = new File(Environment.getExternalStorageDirectory() +
                        "/" + appname);
                String[] projects = projectFolder.list(new FilenameFilter() {
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
                if (!projectFolder.exists() || projects.length == 0) {
                    File firstSceneFolder = new File(projectFolder.getAbsolutePath() + "/2015-01-01_10_00_00/");
                    firstSceneFolder.mkdirs();

                    File arelConfig = new File(firstSceneFolder + "/arelConfig.xml");
                    if (!arelConfig.exists()) {
                        try {
                            InputStream emptyArel = getAssets().open("emptyArel.xml");
                            FileOutputStream fileOut = new FileOutputStream(arelConfig);
                            byte[] buffer = new byte[emptyArel.available()];
                            emptyArel.read(buffer);
                            fileOut.write(buffer);
                            fileOut.close();
                            MediaScannerConnection.scanFile(getApplicationContext(), new String[]{arelConfig.toString()}, null, null);

                        } catch (IOException e) {
                            e.printStackTrace();
                        }


                    }
                    projects = new String[] {"2015-01-01_10_00_00"};
                }


            // create AREL template and present it            Log.d("test", "just testing to see that logging works, getting desperate");


            String newestProjectPath = projectFolder.getAbsolutePath() + "/" + projects[projects.length -1];

                final String arelConfigFilePath = newestProjectPath + "/arelConfig.xml";
                MetaioDebug.log("arelConfig to be passed to intent: "+arelConfigFilePath);
                Intent intent = new Intent(getApplicationContext(), teco.ardevkit.dkarl.andardevkitplayer.ARELViewActivity.class);
                intent.putExtra(getPackageName()+".AREL_SCENE", arelConfigFilePath);
                startActivity(intent);



            finish();
        }

    }

}
