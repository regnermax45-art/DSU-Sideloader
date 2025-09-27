package vegabobo.dsusideloader.util

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

/**
 * Helper class for storage operations that works around private API limitations
 */
object StorageHelper {

    /**
     * Get workspace folder path
     */
    fun getWorkspaceFolder(context: Context): String {
        val externalDir = context.getExternalFilesDir(null)
        return if (externalDir != null) {
            File(externalDir, "workspace_dsuhelper").absolutePath
        } else {
            // Fallback to internal storage
            File(context.filesDir, "workspace_dsuhelper").absolutePath
        }
    }

    /**
     * Get available space in bytes
     */
    fun getAvailableSpace(context: Context): Long {
        return try {
            val workspaceFolder = getWorkspaceFolder(context)
            val stat = StatFs(workspaceFolder)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Get total space in bytes
     */
    fun getTotalSpace(context: Context): Long {
        return try {
            val workspaceFolder = getWorkspaceFolder(context)
            val stat = StatFs(workspaceFolder)
            stat.totalBytes
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Ensure workspace directory exists
     */
    fun ensureWorkspaceExists(context: Context): Boolean {
        return try {
            val workspaceDir = File(getWorkspaceFolder(context))
            if (!workspaceDir.exists()) {
                workspaceDir.mkdirs()
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if external storage is available
     */
    fun isExternalStorageAvailable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    /**
     * Get cache directory for temporary operations
     */
    fun getCacheDir(context: Context): String {
        return context.cacheDir.absolutePath
    }
}
