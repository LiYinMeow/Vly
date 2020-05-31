package date.liyin.vly;

import android.app.Application;
import android.content.pm.ApplicationInfo;

import com.lzf.easyfloat.EasyFloat;

public class MainApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        EasyFloat.init(this, 0 != (this.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE));
        // Enable EasyFloat
    }
}
