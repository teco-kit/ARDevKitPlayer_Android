package teco.ardevkit.andardevkitplayer;

import com.metaio.sdk.ARELActivity;
import com.metaio.sdk.jni.IARELInterpreterCallback;
import com.metaio.sdk.jni.IMetaioSDKCallback;

/**
 * Created by dkarl on 19.03.15.
 */
public class CombinedCallbackActivity extends ARELActivity {


    @Override
    protected IMetaioSDKCallback getMetaioSDKCallbackHandler() {
        return null;
    }

    @Override
    protected IARELInterpreterCallback getARELInterpreterCallback() {
        return null;
    }


    @Override
    protected void loadContents() {
        loadARELScene();
    }
}
