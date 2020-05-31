package date.liyin.vly.utils

import com.chibatching.kotpref.KotprefModel
//SharedPreference
object GlobalSetting : KotprefModel() {
    var doDifferenceSufaceCorrection by booleanPref(default = true) //移动使用强制同平面矫正
    var audio by booleanPref(default = false)
    var walk by booleanPref(default = false)
}