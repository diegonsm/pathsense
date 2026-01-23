package com.example.pathsense.core

import android.content.Context
import java.io.File

fun assetToFilePath(context: Context, assetName: String): String {
    val outFile = File(context.filesDir, assetName)
    if (outFile.exists() && outFile.length() > 0) return outFile.absolutePath

    context.assets.open(assetName).use { input ->
        outFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    return outFile.absolutePath
}
