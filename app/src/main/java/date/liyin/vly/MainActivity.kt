package date.liyin.vly

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import date.liyin.vly.utils.IKeyPassthough
//唯一一个 Activity
class MainActivity : AppCompatActivity() {
    lateinit var currentFragment: Fragment
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return try {
            if (keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event)
            if (currentFragment is IKeyPassthough)
                if ((currentFragment as IKeyPassthough).onKeyDown(
                        keyCode,
                        event
                    )
                ) true else super.onKeyDown(keyCode, event)
            else
                super.onKeyDown(keyCode, event)
        } catch (e: Exception) {
            super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return try {
            if (keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyUp(keyCode, event)
            if (currentFragment is IKeyPassthough)
                if ((currentFragment as IKeyPassthough).onKeyUp(
                        keyCode,
                        event
                    )
                ) true else super.onKeyUp(keyCode, event)
            else
                super.onKeyUp(keyCode, event)
        } catch (e: Exception) {
            super.onKeyUp(keyCode, event)
        }
    }
}
