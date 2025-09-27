package vegabobo.dsusideloader.util

import android.util.Log
import vegabobo.dsusideloader.service.PrivilegedProvider
import java.io.File

/**
 * Utility functions for working with Android partitions
 */
object PartitionUtils {
    private const val TAG = "PartitionUtils"
    
    /**
     * Get list of available partitions on the device
     */
    suspend fun getAvailablePartitions(): List<PartitionInfo> {
        val partitions = mutableListOf<PartitionInfo>()
        
        try {
            // Read from /proc/mounts to get mounted partitions
            val mountsResult = PrivilegedProvider.run {
                CmdRunner.runCommand("cat /proc/mounts")
            }
            
            if (mountsResult.isSuccess) {
                mountsResult.output.lines().forEach { line ->
                    val parts = line.split(" ")
                    if (parts.size >= 6) {
                        val device = parts[0]
                        val mountPoint = parts[1]
                        val fileSystem = parts[2]
                        val options = parts[3]
                        
                        // Filter for relevant partitions
                        if (isRelevantPartition(device, mountPoint)) {
                            partitions.add(
                                PartitionInfo(
                                    device = device,
                                    mountPoint = mountPoint,
                                    fileSystem = fileSystem,
                                    options = options,
                                    size = getPartitionSize(device)
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available partitions", e)
        }
        
        return partitions
    }
    
    /**
     * Check if partition is relevant for porting operations
     */
    private fun isRelevantPartition(device: String, mountPoint: String): Boolean {
        val relevantMountPoints = setOf(
            "/system", "/vendor", "/product", "/odm", "/system_ext"
        )
        
        val relevantDevicePatterns = listOf(
            "system", "vendor", "product", "odm", "system_ext", "boot"
        )
        
        return relevantMountPoints.contains(mountPoint) ||
               relevantDevicePatterns.any { pattern ->
                   device.contains(pattern, ignoreCase = true)
               }
    }
    
    /**
     * Get partition size in bytes
     */
    private suspend fun getPartitionSize(device: String): Long {
        return try {
            val result = PrivilegedProvider.run {
                CmdRunner.runCommand("blockdev --getsize64 $device")
            }
            
            if (result.isSuccess && result.output.isNotEmpty()) {
                result.output.trim().toLongOrNull() ?: 0L
            } else {
                0L
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting partition size for $device", e)
            0L
        }
    }
    
    /**
     * Find partition by name
     */
    suspend fun findPartitionByName(partitionName: String): PartitionInfo? {
        val partitions = getAvailablePartitions()
        return partitions.find { partition ->
            partition.device.contains(partitionName, ignoreCase = true) ||
            partition.mountPoint.contains(partitionName, ignoreCase = true)
        }
    }
    
    /**
     * Check if partition is mounted
     */
    suspend fun isPartitionMounted(partitionName: String): Boolean {
        return try {
            val result = PrivilegedProvider.run {
                CmdRunner.runCommand("mount | grep $partitionName")
            }
            result.isSuccess && result.output.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if partition is mounted: $partitionName", e)
            false
        }
    }
    
    /**
     * Get partition usage information
     */
    suspend fun getPartitionUsage(mountPoint: String): PartitionUsage? {
        return try {
            val result = PrivilegedProvider.run {
                CmdRunner.runCommand("df $mountPoint")
            }
            
            if (result.isSuccess && result.output.isNotEmpty()) {
                val lines = result.output.lines()
                if (lines.size >= 2) {
                    val parts = lines[1].split(Regex("\\s+"))
                    if (parts.size >= 6) {
                        PartitionUsage(
                            totalSize = parts[1].toLongOrNull() ?: 0L,
                            usedSize = parts[2].toLongOrNull() ?: 0L,
                            availableSize = parts[3].toLongOrNull() ?: 0L,
                            usagePercentage = parts[4].replace("%", "").toIntOrNull() ?: 0
                        )
                    } else null
                } else null
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting partition usage for $mountPoint", e)
            null
        }
    }
    
    /**
     * Create backup of partition
     */
    suspend fun createPartitionBackup(
        partitionDevice: String,
        backupPath: String,
        onProgress: (Int) -> Unit = {}
    ): Boolean {
        return try {
            Log.d(TAG, "Creating backup of $partitionDevice to $backupPath")
            
            val backupFile = File(backupPath)
            backupFile.parentFile?.mkdirs()
            
            // Use dd to create backup with progress monitoring
            val command = "dd if=$partitionDevice of=$backupPath bs=1M"
            val result = PrivilegedProvider.run {
                CmdRunner.runCommand(command)
            }
            
            if (result.isSuccess && backupFile.exists()) {
                Log.d(TAG, "Partition backup created successfully: $backupPath")
                true
            } else {
                Log.e(TAG, "Failed to create partition backup: ${result.output}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating partition backup", e)
            false
        }
    }
    
    /**
     * Restore partition from backup
     */
    suspend fun restorePartitionFromBackup(
        backupPath: String,
        partitionDevice: String
    ): Boolean {
        return try {
            Log.d(TAG, "Restoring partition $partitionDevice from $backupPath")
            
            val backupFile = File(backupPath)
            if (!backupFile.exists()) {
                Log.e(TAG, "Backup file does not exist: $backupPath")
                return false
            }
            
            val command = "dd if=$backupPath of=$partitionDevice bs=1M"
            val result = PrivilegedProvider.run {
                CmdRunner.runCommand(command)
            }
            
            if (result.isSuccess) {
                Log.d(TAG, "Partition restored successfully from backup")
                true
            } else {
                Log.e(TAG, "Failed to restore partition: ${result.output}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring partition from backup", e)
            false
        }
    }
    
    /**
     * Verify partition integrity
     */
    suspend fun verifyPartitionIntegrity(partitionDevice: String): Boolean {
        return try {
            // Check if partition device exists and is readable
            val result = PrivilegedProvider.run {
                CmdRunner.runCommand("test -r $partitionDevice && echo 'readable' || echo 'not readable'")
            }
            
            result.isSuccess && result.output.contains("readable")
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying partition integrity", e)
            false
        }
    }
}

/**
 * Partition information data class
 */
data class PartitionInfo(
    val device: String,
    val mountPoint: String,
    val fileSystem: String,
    val options: String,
    val size: Long
) {
    val name: String
        get() = device.substringAfterLast("/")
    
    val sizeInMB: Long
        get() = size / (1024L * 1024L)
    
    val sizeInGB: Long
        get() = size / (1024L * 1024L * 1024L)
}

/**
 * Partition usage information
 */
data class PartitionUsage(
    val totalSize: Long,
    val usedSize: Long,
    val availableSize: Long,
    val usagePercentage: Int
) {
    val totalSizeInMB: Long
        get() = totalSize / 1024L
    
    val usedSizeInMB: Long
        get() = usedSize / 1024L
    
    val availableSizeInMB: Long
        get() = availableSize / 1024L
}

