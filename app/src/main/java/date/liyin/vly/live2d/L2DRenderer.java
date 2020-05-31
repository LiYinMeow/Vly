package date.liyin.vly.live2d;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.os.SystemClock;
import android.util.Log;
import android.view.TextureView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import date.liyin.vly.gles.EglCore;
import date.liyin.vly.gles.WindowSurface;

/**
 * Handles GL rendering and SurfaceTexture callbacks.
 * <p>
 * We don't create a Looper, so the SurfaceTexture-by-way-of-TextureView callbacks
 * happen on the UI thread.
 */
//Live2D 渲染器 详见官方 Sample
public class L2DRenderer extends Thread implements TextureView.SurfaceTextureListener {
    private static volatile boolean sReleaseInCallback = true;
    private static String TAG = "L2DRenderer";
    private Object mLock = new Object();        // guards mSurfaceTexture, mDone
    private SurfaceTexture mSurfaceTexture;
    private EglCore mEglCore;
    private boolean mDone;
    private int width = 1024;
    private int height = 4096;
    private Bitmap bmp = null;
    private Activity activity = null;
    private Runnable onLoaded;

    public L2DRenderer() {
        super("TextureViewGL Renderer");
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    public Bitmap getLastFrame() {
        return bmp;
    }

    public void setOnLoaded(Runnable onLoaded) {
        this.onLoaded = onLoaded;
    }

    public void setSurfaceTextureSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void setSurfaceTexture(SurfaceTexture mSurfaceTexture) {
        this.mSurfaceTexture = mSurfaceTexture;
    }

    @Override
    public void run() {
        while (true) {
            SurfaceTexture surfaceTexture = null;

            // Latch the SurfaceTexture when it becomes available.  We have to wait for
            // the TextureView to create it.
            synchronized (mLock) {
                while (!mDone && (surfaceTexture = mSurfaceTexture) == null) {
                    try {
                        mLock.wait();
                    } catch (InterruptedException ie) {
                        throw new RuntimeException(ie);     // not expected
                    }
                }
                if (mDone) {
                    break;
                }
            }
            Log.d(TAG, "Got surfaceTexture=" + surfaceTexture);
            // Create an EGL surface for our new SurfaceTexture.  We're not on the same
            // thread as the SurfaceTexture, which is a concern for the *consumer*, which
            // wants to call updateTexImage().  Because we're the *producer*, i.e. the
            // one generating the frames, we don't need to worry about being on the same
            // thread.
            mEglCore = new EglCore(null, EglCore.FLAG_TRY_GLES3);
            WindowSurface windowSurface = new WindowSurface(mEglCore, mSurfaceTexture);
            windowSurface.makeCurrent();
            JniBridgeJava.SetActivityInstance(activity);
            JniBridgeJava.SetContext(activity);
            JniBridgeJava.nativeOnStart();
            JniBridgeJava.nativeOnSurfaceCreated();
            JniBridgeJava.nativeOnSurfaceChanged(width, height);
            while (!JniBridgeJava.isLoaded()) {
                SystemClock.sleep(50);
            }
            try {
                if (onLoaded != null) onLoaded.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
            // Render frames until we're told to stop or the SurfaceTexture is destroyed.
            doAnimation(windowSurface);
            JniBridgeJava.nativeOnPause();
            JniBridgeJava.nativeOnStop();
            JniBridgeJava.nativeOnDestroy();
            windowSurface.release();
            mEglCore.release();
            if (!sReleaseInCallback) {
                Log.i(TAG, "Releasing SurfaceTexture in renderer thread");
                surfaceTexture.release();
            }
        }

//        Log.d(TAG, "Renderer thread exiting");
    }

    /**
     * Draws updates as fast as the system will allow.
     * <p>
     * In 4.4, with the synchronous buffer queue queue, the frame rate will be limited.
     * In previous (and future) releases, with the async queue, many of the frames we
     * render may be dropped.
     * <p>
     * The correct thing to do here is use Choreographer to schedule frame updates off
     * of vsync, but that's not nearly as much fun.
     */
    private void doAnimation(WindowSurface eglSurface) {
        Log.d(TAG, "Animating " + width + "x" + height + " EGL surface");

        while (true) {
            // Check to see if the TextureView's SurfaceTexture is still valid.
            synchronized (mLock) {
                SurfaceTexture surfaceTexture = mSurfaceTexture;
                if (surfaceTexture == null) {
                    Log.d(TAG, "doAnimation exiting");
                    return;
                }
                if (mDone) {
                    break;
                }
            }
            JniBridgeJava.nativeOnDrawFrame();

            // ==== Bitmap ===
            //获取离屏数据等待渲染到 ImageView
            try {
                byte[] bdata = JniBridgeJava.getLastData();
                if (bdata.length > 0) {
                    Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    ByteBuffer byteBuffer = ByteBuffer.allocate(width * height * 4);
                    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                    byteBuffer.put(bdata);
                    byteBuffer.rewind();
                    bmp.copyPixelsFromBuffer(byteBuffer);
                    Matrix matrix = new Matrix();
                    matrix.postRotate(180);
                    Bitmap scaledBitmap = Bitmap.createScaledBitmap(bmp, width, height, true);
                    this.bmp = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix, true);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            // ============

        }
    }

    /**
     * Tells the thread to stop running.
     */
    public void halt() {
        synchronized (mLock) {
            mDone = true;
            mLock.notify();
        }
        JniBridgeJava.nativeOnStop();
    }

    @Override   // will be called on UI thread
    public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
        Log.d(TAG, "onSurfaceTextureAvailable(" + width + "x" + height + ")");
        synchronized (mLock) {
            mSurfaceTexture = st;
            mLock.notify();
        }
    }

    @Override   // will be called on UI thread
    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int width, int height) {
        Log.d(TAG, "onSurfaceTextureSizeChanged(" + width + "x" + height + ")");
        this.height = height;
        this.width = width;
    }

    @Override   // will be called on UI thread
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        Log.d(TAG, "onSurfaceTextureDestroyed");

        // We set the SurfaceTexture reference to null to tell the Renderer thread that
        // it needs to stop.  The renderer might be in the middle of drawing, so we want
        // to return false here so that the caller doesn't try to release the ST out
        // from under us.
        //
        // In theory.
        //
        // In 4.4, the buffer queue was changed to be synchronous, which means we block
        // in dequeueBuffer().  If the renderer has been running flat out and is currently
        // sleeping in eglSwapBuffers(), it's going to be stuck there until somebody
        // tears down the SurfaceTexture.  So we need to tear it down here to ensure
        // that the renderer thread will break.  If we don't, the thread sticks there
        // forever.
        //
        // The only down side to releasing it here is we'll get some complaints in logcat
        // when eglSwapBuffers() fails.
        synchronized (mLock) {
            mSurfaceTexture = null;
        }
        if (sReleaseInCallback) {
            Log.i(TAG, "Allowing TextureView to release SurfaceTexture");
        }
        return sReleaseInCallback;
    }

    @Override   // will be called on UI thread
    public void onSurfaceTextureUpdated(SurfaceTexture st) {
        //Log.d(TAG, "onSurfaceTextureUpdated");
    }
}