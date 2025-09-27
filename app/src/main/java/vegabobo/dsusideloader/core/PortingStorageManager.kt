package vegabobo.dsusideloader.core

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vegabobo.dsusideloader.service.PrivilegedProvider
import vegabobo.dsusideloader.util.CmdRunner
import java.io.File

/**
 * Extended storage manager for porting operations
 * Handles large-scale file operations and temporary storage management
 */
class PortingStorageManager(
    private val baseStorageManager: StorageManager
) {
    private val tag = this.javaClass.simpleName
    
    companion object {
        const val PORTING_FOLDER = "porting"
        const val SYSTEM_EXTRACTION_FOLDER = "system_extraction"
        const val PATCHING_FOLDER = "patching"
        const val MERGING_FOLDER = "merging"
        const val BACKUP_FOLDER = "backups"
        const val TEMP_FOLDER = "temp"
    }
    
    /**
     * Initialize porting storage directories
     */
    suspend fun initializePortingStorage(): Boolean = withContext(Dispatchers.IO) {
        try {
            val portingDir = getPortingDirectory()
            
            // Create all necessary subdirectories
            val directories = listOf(
                File(portingDir, SYSTEM_EXTRACTION_FOLDER),
                File(portingDir, PATCHING_FOLDER),
                File(portingDir, MERGING_FOLDER),
                File(portingDir, BACKUP_FOLDER),
                File(portingDir, TEMP_FOLDER)
            )
            
            directories.forEach { dir ->
                if (!dir.exists()) {
                    val created = dir.mkdirs()
                    if (created) {
                        Log.d(tag, "Created directory: ${dir.absolutePath}")
                    } else {
                        Log.e(tag, "Failed to create directory: ${dir.absolutePath}")
                        return@withContext false
                    }
                }
            }
            
            Log.d(tag, "Porting storage initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(tag, "Error initializing porting storage", e)
            false
        }
    }
    
    /**
     * Get main porting directory
     */
    fun getPortingDirectory(): File {
        return File(baseStorageManager.getWorkspaceFolder(), PORTING_FOLDER)
    }
    
    /**
     * Get system extraction directory
     */
    fun getSystemExtractionDirectory(): File {
        return File(getPortingDirectory(), SYSTEM_EXTRACTION_FOLDER)
    }
    
    /**
     * Get patching directory
     */
    fun getPatchingDirectory(): File {
        return File(getPortingDirectory(), PATCHING_FOLDER)
    }
    
    /**
     * Get merging directory
     */
    fun getMergingDirectory(): File {
        return File(getPortingDirectory(), MERGING_FOLDER)
    }
    
    /**
     * Get backup directory
     */
    fun getBackupDirectory(): File {
        return File(getPortingDirectory(), BACKUP_FOLDER)
    }
    
    /**
     * Get temporary directory
     */
    fun getTempDirectory(): File {
        return File(getPortingDirectory(), TEMP_FOLDER)
    }
    
    /**
     * Check available space for porting operations
     */
    suspend fun checkAvailableSpace(): Long = withContext(Dispatchers.IO) {
        try {
            val workspaceFolder = baseStorageManager.getWorkspaceFolder()
            val result = PrivilegedProvider.run {
                CmdRunner.runCommand("df $workspaceFolder")
            }
            
            if (result.isSuccess && result.output.isNotEmpty()) {
                val lines = result.output.lines()
                if (lines.size >= 2) {
                    val parts = lines[1].split(Regex("\\s+"))
                    if (parts.size >= 4) {
                        // Available space is typically in the 4th column (in KB)
                        val availableKB = parts[3].toLongOrNull() ?: 0L
                        return@withContext availableKB * 1024L // Convert to bytes
                    }
                }
            }
            
            // Fallback: use Java's built-in method
            File(workspaceFolder).freeSpace
        } catch (e: Exception) {
            Log.e(tag, "Error checking available space", e)
            0L
        }
    }
    
    /**
     * Calculate total space used by porting operations
     */
    suspend fun calculatePortingSpaceUsage(): Long = withContext(Dispatchers.IO) {
        try {
            val portingDir = getPortingDirectory()
            if (!portingDir.exists()) {
                return@withContext 0L
            }
            
            val result = PrivilegedProvider.run {
                CmdRunner.runCommand("du -sb ${portingDir.absolutePath}")
            }
            
            if (result.isSuccess && result.output.isNotEmpty()) {
                val sizeStr = result.output.split("\t")[0]
                sizeStr.toLongOrNull() ?: 0L
            } else {
                // Fallback: calculate manually
                calculateDirectorySize(portingDir)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error calculating porting space usage", e)
            0L
        }
    }
    
    /**
     * Clean up temporary files from porting operations
     */
    suspend fun cleanupTempFiles(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "Cleaning up temporary porting files")
            
            val tempDir = getTempDirectory()
            if (tempDir.exists()) {
                val deleted = tempDir.deleteRecursively()
                if (deleted) {
                    tempDir.mkdirs() // Recreate empty temp directory
                    Log.d(tag, "Temporary files cleaned up successfully")
                } else {
                    Log.w(tag, "Failed to delete some temporary files")
                }
                return@withContext deleted
            }
            
            true
        } catch (e: Exception) {
            Log.e(tag, "Error cleaning up temporary files", e)
            false
        }
    }
    
    /**
     * Clean up all porting files (including backups)
     */
    suspend fun cleanupAllPortingFiles(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "Cleaning up all porting files")
            
            val portingDir = getPortingDirectory()
            if (portingDir.exists()) {
                val deleted = portingDir.deleteRecursively()
                if (deleted) {
                    Log.d(tag, "All porting files cleaned up successfully")
                } else {
                    Log.w(tag, "Failed to delete some porting files")
                }
                return@withContext deleted
            }
            
            true
        } catch (e: Exception) {
            Log.e(tag, "Error cleaning up porting files", e)
            false
        }
    }
    
    /**
     * Create backup of important files before porting
     */
    suspend fun createPortingBackup(sourceFiles: List<String>): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "Creating porting backup")
            
            val backupDir = getBackupDirectory()
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }
            
            val timestamp = System.currentTimeMillis()
            val backupSubDir = File(backupDir, "backup_$timestamp")
            backupSubDir.mkdirs()
            
            var allSuccessful = true
            
            for (sourceFile in sourceFiles) {
                val source = File(sourceFile)
                if (source.exists()) {
                    val backup = File(backupSubDir, source.name)
                    try {
                        source.copyTo(backup, overwrite = true)
                        Log.d(tag, "Backed up: ${source.name}")
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to backup: ${source.name}", e)
                        allSuccessful = false
                    }
                }
            }
            
            if (allSuccessful) {
                Log.d(tag, "Porting backup created successfully")
            } else {
                Log.w(tag, "Some files failed to backup")
            }
            
            allSuccessful
        } catch (e: Exception) {
            Log.e(tag, "Error creating porting backup", e)
            false
        }
    }
    
    /**
     * Monitor disk space during porting operations
     */
    suspend fun monitorDiskSpace(minimumFreeSpaceBytes: Long): Boolean = withContext(Dispatchers.IO) {
        val availableSpace = checkAvailableSpace()
        val hasEnoughSpace = availableSpace >= minimumFreeSpaceBytes
        
        if (!hasEnoughSpace) {
            val availableMB = availableSpace / (1024L * 1024L)
            val requiredMB = minimumFreeSpaceBytes / (1024L * 1024L)
            Log.w(tag, "Insufficient disk space. Available: ${availableMB}MB, Required: ${requiredMB}MB")
        }
        
        hasEnoughSpace
    }
    
    /**
     * Get storage statistics for porting operations
     */
    suspend fun getPortingStorageStats(): PortingStorageStats = withContext(Dispatchers.IO) {
        val totalSpace = File(baseStorageManager.getWorkspaceFolder()).totalSpace
        val availableSpace = checkAvailableSpace()
        val usedSpace = totalSpace - availableSpace
        val portingSpaceUsage = calculatePortingSpaceUsage()
        
        PortingStorageStats(
            totalSpace = totalSpace,
            availableSpace = availableSpace,
            usedSpace = usedSpace,
            portingSpaceUsage = portingSpaceUsage,
            systemExtractionSize = calculateDirectorySize(getSystemExtractionDirectory()),
            patchingSize = calculateDirectorySize(getPatchingDirectory()),
            mergingSize = calculateDirectorySize(getMergingDirectory()),
            backupSize = calculateDirectorySize(getBackupDirectory()),
            tempSize = calculateDirectorySize(getTempDirectory())
        )
    }
    
    /**
     * Optimize storage by compressing old files
     */
    suspend fun optimizeStorage(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(tag, "Optimizing porting storage")
            
            // Compress old backup files
            val backupDir = getBackupDirectory()
            if (backupDir.exists()) {
                val backupDirs = backupDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
                
                for (dir in backupDirs) {
                    val age = System.currentTimeMillis() - dir.lastModified()
                    val dayInMillis = 24 * 60 * 60 * 1000L
                    
                    // Compress backups older than 1 day
                    if (age > dayInMillis) {
                        val compressedFile = File(backupDir, "${dir.name}.tar.gz")
                        val compressCommand = "tar -czf ${compressedFile.absolutePath} -C ${backupDir.absolutePath} ${dir.name}"
                        
                        val result = PrivilegedProvider.run {
                            CmdRunner.runCommand(compressCommand)
                        }
                        
                        if (result.isSuccess && compressedFile.exists()) {
                            dir.deleteRecursively()
                            Log.d(tag, "Compressed old backup: ${dir.name}")
                        }
                    }
                }
            }
            
            // Clean up temporary files older than 1 hour
            val tempDir = getTempDirectory()
            if (tempDir.exists()) {
                val tempFiles = tempDir.listFiles() ?: emptyArray()
                val hourInMillis = 60 * 60 * 1000L
                
                for (file in tempFiles) {
                    val age = System.currentTimeMillis() - file.lastModified()
                    if (age > hourInMillis) {
                        if (file.isDirectory()) {
                            file.deleteRecursively()
                        } else {
                            file.delete()
                        }
                        Log.d(tag, "Cleaned up old temp file: ${file.name}")
                    }
                }
            }
            
            Log.d(tag, "Storage optimization completed")
            true
        } catch (e: Exception) {
            Log.e(tag, "Error optimizing storage", e)
            false
        }
    }
    
    /**
     * Calculate directory size recursively
     */
    private fun calculateDirectorySize(directory: File): Long {
        if (!directory.exists()) return 0L
        
        return try {
            directory.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        } catch (e: Exception) {
            Log.e(tag, "Error calculating directory size: ${directory.absolutePath}", e)
            0L
        }
    }
}

/**
 * Porting storage statistics
 */
data class PortingStorageStats(
    val totalSpace: Long,
    val availableSpace: Long,
    val usedSpace: Long,
    val portingSpaceUsage: Long,
    val systemExtractionSize: Long,
    val patchingSize: Long,
    val mergingSize: Long,
    val backupSize: Long,
    val tempSize: Long
) {
    val totalSpaceGB: Float
        get() = totalSpace / (1024f * 1024f * 1024f)
    
    val availableSpaceGB: Float
        get() = availableSpace / (1024f * 1024f * 1024f)
    
    val usedSpaceGB: Float
        get() = usedSpace / (1024f * 1024f * 1024f)
    
    val portingSpaceUsageGB: Float
        get() = portingSpaceUsage / (1024f * 1024f * 1024f)
    
    val usagePercentage: Float
        get() = if (totalSpace > 0) (usedSpace.toFloat() / totalSpace.toFloat()) * 100f else 0f
    
    val portingUsagePercentage: Float
        get() = if (totalSpace > 0) (portingSpaceUsage.toFloat() / totalSpace.toFloat()) * 100f else 0f
}

