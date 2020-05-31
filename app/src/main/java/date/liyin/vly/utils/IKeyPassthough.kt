package date.liyin.vly.utils

import android.view.KeyEvent
//按钮穿透
interface IKeyPassthough {
    fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean = false
    fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = false
    fun onBackPressed() = Unit
}