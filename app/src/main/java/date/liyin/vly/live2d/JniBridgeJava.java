package date.liyin.vly.live2d;

import android.app.Activity;
import android.content.Context;

import java.util.Arrays;
//JNI 访问 Live2D 详见官方 Sample
public class JniBridgeJava {
    private static final String LIBRARY_NAME = "VLY";
    private static Activity _activityInstance;
    private static Context _context;
    private static byte[] data = new byte[1];
    private static FileGetter fileGetter = null;

    static {
        System.loadLibrary(LIBRARY_NAME);
    }

    // Native -----------------------------------------------------------------
    public static native void nativeOnStart();

    public static native void nativeOnPause();

    public static native void nativeOnStop();

    public static native void nativeOnDestroy();

    public static native void nativeOnSurfaceCreated();

    public static native void nativeOnSurfaceChanged(int width, int height);

    public static native void nativeOnDrawFrame();

    public static native boolean isLoaded();

    public static native String nativeGetL2DName();

    public static native String[] nativeGetFaceList();

    public static native String[] nativeGetMotionList();

    public static native void nativeSetFace(String name);

    public static native void nativePlayAnimation(String animation);

    public static native void nativeStopAnimation();

    public static native void nativeSetModelJSONName(String name);

    public static native void nativeSetConfigMode(boolean isConfig);

    public static native void nativeSetModelX(float x);

    public static native void nativeSetModelY(float y);

    public static native void nativeSetModelScale(float scale);

    public static void setModelDefault() {
        nativeSetModelX(0.0f);
        nativeSetModelY(0.0f);
        nativeSetModelScale(1.0f);
    }

    public static void SetContext(Context context) {
        _context = context;
    }

    // Java -----------------------------------------------------------------

    public static void SetActivityInstance(Activity activity) {
        _activityInstance = activity;
    }

    public static void setFileGetter(FileGetter fileGetter) {
        JniBridgeJava.fileGetter = fileGetter;
    }

    public static byte[] LoadFile(String filePath) {
        if (fileGetter == null) return null;
        return fileGetter.requestFile(filePath);
    }

    public static void MoveTaskToBack() {
        _activityInstance.moveTaskToBack(true);
    }

    public static void PushLastData(byte[] data) {
        JniBridgeJava.data = Arrays.copyOf(data, data.length);
    }

    public static byte[] getLastData() {
        return data;
    }

    public interface FileGetter {
        byte[] requestFile(String path);
    }

}
