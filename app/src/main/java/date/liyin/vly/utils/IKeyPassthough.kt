package date.liyin.vly.utils

import android.view.KeyEvent

interface IKeyPassthough {
    fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean = false
    fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = false
    fun onBackPressed() = Unit
}