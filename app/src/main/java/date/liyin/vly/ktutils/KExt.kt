package date.liyin.vly.ktutils

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

fun InputStream.zipInputStream() = ZipInputStream(this)
fun InputStream.streamTo(filePath: File) {
    FileOutputStream(filePath).use { it.write(this.readBytes()) }
}