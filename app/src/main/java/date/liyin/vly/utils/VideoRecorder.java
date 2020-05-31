package date.liyin.vly.utils;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.res.Configuration;
import android.media.CamcorderProfile;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.widget.ToggleButton;

import com.google.ar.sceneform.SceneView;
import com.lzf.easyfloat.EasyFloat;

import java.io.File;
import java.io.IOException;

import date.liyin.vly.R;

/**
 * Video Recorder class handles recording the contents of a SceneView. It uses MediaRecorder to
 * encode the video. The quality settings can be set explicitly or simply use the CamcorderProfile
 * class to select a predefined set of parameters.
 */
//录制库 详见 Google Sample
public class VideoRecorder {
    private static final String TAG = "VideoRecorder";
    private static final int DEFAULT_BITRATE = 10000000;
    private static final int DEFAULT_AUDIO_BITRATE = 96000;
    private static final int DEFAULT_AUDIO_SAMPLING = 44800;
    private static final int DEFAULT_AUDIO_CHANNEL = 1;
    private static final int DEFAULT_FRAMERATE = 30;
    private static final int[] FALLBACK_QUALITY_LEVELS = {
            CamcorderProfile.QUALITY_HIGH,
            CamcorderProfile.QUALITY_2160P,
            CamcorderProfile.QUALITY_1080P,
            CamcorderProfile.QUALITY_720P,
            CamcorderProfile.QUALITY_480P
    };
    // recordingVideoFlag is true when the media recorder is capturing video.
    private boolean recordingVideoFlag;
    private MediaRecorder mediaRecorder;
    private Activity activity;
    private Size videoSize;
    private String baseFile;
    private SceneView sceneView;
    private int videoCodec;
    private int bitRate = DEFAULT_BITRATE;
    private int frameRate = DEFAULT_FRAMERATE;
    private int audioBit = DEFAULT_AUDIO_BITRATE;
    private int audioSampling = DEFAULT_AUDIO_SAMPLING;
    private int audioCodec;
    private int audioChannel = DEFAULT_AUDIO_CHANNEL;
    private Surface encoderSurface;

    public VideoRecorder(Activity activity) {
        this.activity = activity;
        baseFile = Environment.DIRECTORY_MOVIES + File.separator + "VSR";
        recordingVideoFlag = false;
    }

    public void setBitRate(int bitRate) {
        this.bitRate = bitRate;
    }

    public void setFrameRate(int frameRate) {
        this.frameRate = frameRate;
    }

    public void setSceneView(SceneView sceneView) {
        this.sceneView = sceneView;
    }

    /**
     * Toggles the state of video recording.
     *
     * @return true if recording is now active.
     */
    public boolean onToggleRecord() {
        if (recordingVideoFlag) {
            stopRecordingVideo();
        } else {
            startRecordingVideo();
        }
        return recordingVideoFlag;
    }

    private void startRecordingVideo() {
        if (mediaRecorder == null) {
            mediaRecorder = new MediaRecorder();
        }

        try {
            setUpMediaRecorder();
        } catch (IOException e) {
            Log.e(TAG, "Exception setting up recorder", e);
            return;
        }

        // Set up Surface for the MediaRecorder
        encoderSurface = mediaRecorder.getSurface();

        sceneView.startMirroringToSurface(
                encoderSurface, 0, 0, videoSize.getWidth(), videoSize.getHeight());

        recordingVideoFlag = true;
    }

    private void stopRecordingVideo() {
        // UI
        recordingVideoFlag = false;

        if (encoderSurface != null) {
            sceneView.stopMirroringToSurface(encoderSurface);
            encoderSurface = null;
        }
        // Stop recording
        mediaRecorder.stop();
        mediaRecorder.reset();
    }

    private void setUpMediaRecorder() throws IOException {
        final ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "VSR_" + System.currentTimeMillis() + ".mp4");
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, MediaFormat.MIMETYPE_VIDEO_MPEG4);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, baseFile);
        }
        final ContentResolver resolver = activity.getContentResolver();
        final Uri contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        Uri uri = resolver.insert(contentUri, contentValues);
        if (uri == null) {
            throw new IOException("Failed to create new MediaStore record.");
        }
        ParcelFileDescriptor fileDescriptor = resolver.openFileDescriptor(uri, "w");
        try {
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            if (((ToggleButton) (EasyFloat.getFloatView().findViewById(R.id.sw_audio))).isChecked()) {
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            }
            mediaRecorder.setOutputFile(fileDescriptor.getFileDescriptor());
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setVideoEncodingBitRate(bitRate);
            mediaRecorder.setVideoFrameRate(frameRate);
            mediaRecorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
            mediaRecorder.setVideoEncoder(videoCodec);
            if (((ToggleButton) (EasyFloat.getFloatView().findViewById(R.id.sw_audio))).isChecked()) {
                mediaRecorder.setAudioEncodingBitRate(audioBit);
                mediaRecorder.setAudioEncoder(audioCodec);
                mediaRecorder.setAudioSamplingRate(audioSampling);
                mediaRecorder.setAudioChannels(audioChannel);
            }
            mediaRecorder.prepare();
            mediaRecorder.start();
        } catch (IOException e) {
            resolver.delete(uri, null, null);
            throw e;
        }
    }

    public void setVideoSize(int width, int height) {
        videoSize = new Size(width, height);
    }

    public void setVideoQuality(int quality, int orientation) {
        CamcorderProfile profile = null;
        if (CamcorderProfile.hasProfile(quality)) {
            profile = CamcorderProfile.get(quality);
        }
        if (profile == null) {
            // Select a quality  that is available on this device.
            for (int level : FALLBACK_QUALITY_LEVELS) {
                if (CamcorderProfile.hasProfile(level)) {
                    profile = CamcorderProfile.get(level);
                    break;
                }
            }
        }
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
        } else {
            setVideoSize(profile.videoFrameHeight, profile.videoFrameWidth);
        }
        setVideoCodec(profile.videoCodec);
        setBitRate(profile.videoBitRate);
        setFrameRate(profile.videoFrameRate);
        setAudioBit(profile.audioBitRate);
        setAudioSampling(profile.audioSampleRate);
        setAudioCodec(profile.audioCodec);
        setAudioChannel(profile.audioChannels);
    }

    public void setAudioCodec(int audioCodec) {
        this.audioCodec = audioCodec;
    }

    public void setAudioChannel(int audioChannel) {
        this.audioChannel = audioChannel;
    }

    public void setAudioBit(int audioBit) {
        this.audioBit = audioBit;
    }

    public void setAudioSampling(int audioSampling) {
        this.audioSampling = audioSampling;
    }

    public void setVideoCodec(int videoCodec) {
        this.videoCodec = videoCodec;
    }

    public boolean isRecording() {
        return recordingVideoFlag;
    }
}