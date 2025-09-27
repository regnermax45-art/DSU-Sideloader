package vegabobo.dsusideloader.porting

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vegabobo.dsusideloader.core.StorageManager
import vegabobo.dsusideloader.model.*
import vegabobo.dsusideloader.service.PrivilegedProvider
import vegabobo.dsusideloader.util.CmdRunner
import java.io.File
import java.security.MessageDigest

/**
 * Extracts system components from the current device for porting operations
 * Requires root access to read system partitions and extract components
 */
class SystemExtractor(
    private val session: Session,
    private val storageManager: StorageManager
) {
    private val tag = this.javaClass.simpleName
    
    /**
     * Extract all system components needed for porting
     */
    suspend fun extractSystemComponents(): SystemComponents = withContext(Dispatchers.IO) {
        Log.d(tag, "Starting system component extraction")
        
        val extractionDir = File(storageManager.getWorkspaceFolder(), "system_extraction")
        if (!extractionDir.exists()) {
            extractionDir.mkdirs()
        }
        
        val components = SystemComponents(
            systemPartition = extractPartition("system", extractionDir),
            vendorPartition = extractPartition("vendor", extractionDir),
            productPartition = extractPartition("product", extractionDir),
            bootPartition = if (session.preferences.useBuiltinInstaller) 
                extractPartition("boot", extractionDir) else null,
            
            halComponents = extractHalComponents(),
            drivers = extractDriverInfo(),
            buildProperties = extractBuildProperties(),
            systemProperties = extractSystemProperties(),
            selinuxPolicies = extractSelinuxPolicies(),
            securityPatches = extractSecurityPatches()
        )
        
        Log.d(tag, "System component extraction completed")
        components
    }
    
    /**
     * Extract a specific partition from the device
     */
    private suspend fun extractPartition(partitionName: String, extractionDir: File): ExtractedPartition? {
        return try {
            Log.d(tag, "Extracting $partitionName partition")
            
            // Find partition block device
            val partitionPath = findPartitionPath(partitionName)
            if (partitionPath == null) {
                Log.w(tag, "Partition $partitionName not found")
                return null
            }
            
            // Get partition info
            val partitionInfo = getPartitionInfo(partitionPath)
            val outputFile = File(extractionDir, "${partitionName}.img")
            
            // Extract partition using dd
            val extractCommand = "dd if=$partitionPath of=${outputFile.absolutePath} bs=1M"
            val result = PrivilegedProvider.run {
                CmdRunner.runCommand(extractCommand)
            }
            
            if (result.isSuccess && outputFile.exists()) {
                val checksum = calculateChecksum(outputFile)
                
                ExtractedPartition(
                    name = partitionName,
                    path = partitionPath,
                    size = outputFile.length(),
                    mountPoint = partitionInfo.mountPoint,
                    fileSystem = partitionInfo.fileSystem,
                    extractedPath = outputFile.absolutePath,
                    checksum = checksum
                )
            } else {
                Log.e(tag, "Failed to extract $partitionName partition: ${result.output}")
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "Error extracting $partitionName partition", e)
            null
        }
    }
    
    /**
     * Find the block device path for a partition
     */
    private suspend fun findPartitionPath(partitionName: String): String? {
        val possiblePaths = listOf(
            "/dev/block/by-name/$partitionName",
            "/dev/block/bootdevice/by-name/$partitionName",
            "/dev/block/platform/*/by-name/$partitionName"
        )
        
        for (path in possiblePaths) {
            val result = PrivilegedProvider.run {
                CmdRunner.runCommand("ls $path")
            }
            if (result.isSuccess) {
                return path
            }
        }
        
        // Try to find via /proc/mounts
        val mountsResult = PrivilegedProvider.run {
            CmdRunner.runCommand("cat /proc/mounts | grep $partitionName")
        }
        
        if (mountsResult.isSuccess && mountsResult.output.isNotEmpty()) {
            val mountLine = mountsResult.output.lines().firstOrNull()
            return mountLine?.split(" ")?.firstOrNull()
        }
        
        return null
    }
    
    /**
     * Get partition information
     */
    private suspend fun getPartitionInfo(partitionPath: String): PartitionInfo {
        val mountResult = PrivilegedProvider.run {
            CmdRunner.runCommand("cat /proc/mounts | grep $partitionPath")
        }
        
        val mountPoint = if (mountResult.isSuccess && mountResult.output.isNotEmpty()) {
            mountResult.output.split(" ").getOrNull(1) ?: "unknown"
        } else "unknown"
        
        val fsResult = PrivilegedProvider.run {
            CmdRunner.runCommand("blkid $partitionPath")
        }
        
        val fileSystem = if (fsResult.isSuccess && fsResult.output.contains("TYPE=")) {
            fsResult.output.substringAfter("TYPE=\"").substringBefore("\"")
        } else "unknown"
        
        return PartitionInfo(mountPoint, fileSystem)
    }
    
    /**
     * Extract Hardware Abstraction Layer components
     */
    private suspend fun extractHalComponents(): List<HalComponent> {
        val halComponents = mutableListOf<HalComponent>()
        
        try {
            // Check vendor/lib64/hw for HAL libraries
            val halDirs = listOf(
                "/vendor/lib64/hw",
                "/vendor/lib/hw",
                "/system/lib64/hw",
                "/system/lib/hw"
            )
            
            for (halDir in halDirs) {
                val result = PrivilegedProvider.run {
                    CmdRunner.runCommand("ls -la $halDir/*.so 2>/dev/null || true")
                }
                
                if (result.isSuccess && result.output.isNotEmpty()) {
                    result.output.lines().forEach { line ->
                        if (line.contains(".so")) {
                            val fileName = line.substringAfterLast(" ")
                            if (fileName.contains("android.hardware")) {
                                val component = parseHalComponent(fileName, halDir)
                                if (component != null) {
                                    halComponents.add(component)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error extracting HAL components", e)
        }
        
        return halComponents
    }
    
    /**
     * Parse HAL component from filename
     */
    private fun parseHalComponent(fileName: String, directory: String): HalComponent? {
        return try {
            val parts = fileName.split("@")
            if (parts.size >= 2) {
                val name = parts[0].replace("android.hardware.", "")
                val version = parts[1].substringBefore("-")
                
                HalComponent(
                    name = name,
                    version = version,
                    path = "$directory/$fileName",
                    interface = parts[0],
                    vendor = "unknown",
                    isEssential = isEssentialHal(name)
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check if HAL is essential for device operation
     */
    private fun isEssentialHal(halName: String): Boolean {
        val essentialHals = setOf(
            "camera", "audio", "graphics", "sensors", "gnss", "radio", "wifi", "bluetooth"
        )
        return essentialHals.any { halName.contains(it, ignoreCase = true) }
    }
    
    /**
     * Extract driver information
     */
    private suspend fun extractDriverInfo(): List<DriverInfo> {
        val drivers = mutableListOf<DriverInfo>()
        
        try {
            // Get loaded kernel modules
            val modulesResult = PrivilegedProvider.run {
                CmdRunner.runCommand("cat /proc/modules")
            }
            
            if (modulesResult.isSuccess) {
                modulesResult.output.lines().forEach { line ->
                    val parts = line.split(" ")
                    if (parts.isNotEmpty()) {
                        drivers.add(
                            DriverInfo(
                                name = parts[0],
                                version = "unknown",
                                path = "/system/lib/modules/${parts[0]}.ko",
                                isKernelModule = true
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error extracting driver info", e)
        }
        
        return drivers
    }
    
    /**
     * Extract build properties
     */
    private suspend fun extractBuildProperties(): Map<String, String> {
        val properties = mutableMapOf<String, String>()
        
        try {
            val result = PrivilegedProvider.run {
                CmdRunner.runCommand("cat /system/build.prop")
            }
            
            if (result.isSuccess) {
                result.output.lines().forEach { line ->
                    if (line.contains("=") && !line.startsWith("#")) {
                        val parts = line.split("=", limit = 2)
                        if (parts.size == 2) {
                            properties[parts[0].trim()] = parts[1].trim()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error extracting build properties", e)
        }
        
        return properties
    }
    
    /**
     * Extract system properties
     */
    private suspend fun extractSystemProperties(): Map<String, String> {
        val properties = mutableMapOf<String, String>()
        
        try {
            val result = PrivilegedProvider.run {
                CmdRunner.runCommand("getprop")
            }
            
            if (result.isSuccess) {
                result.output.lines().forEach { line ->
                    if (line.contains("]: [")) {
                        val key = line.substringAfter("[").substringBefore("]")
                        val value = line.substringAfterLast("[").substringBeforeLast("]")
                        properties[key] = value
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error extracting system properties", e)
        }
        
        return properties
    }
    
    /**
     * Extract SELinux policies
     */
    private suspend fun extractSelinuxPolicies(): List<String> {
        val policies = mutableListOf<String>()
        
        try {
            val result = PrivilegedProvider.run {
                CmdRunner.runCommand("ls /system/etc/selinux/ /vendor/etc/selinux/ 2>/dev/null || true")
            }
            
            if (result.isSuccess) {
                policies.addAll(result.output.lines().filter { it.isNotEmpty() })
            }
        } catch (e: Exception) {
            Log.e(tag, "Error extracting SELinux policies", e)
        }
        
        return policies
    }
    
    /**
     * Extract security patch information
     */
    private suspend fun extractSecurityPatches(): List<SecurityPatch> {
        val patches = mutableListOf<SecurityPatch>()
        
        try {
            val patchLevelResult = PrivilegedProvider.run {
                CmdRunner.runCommand("getprop ro.build.version.security_patch")
            }
            
            if (patchLevelResult.isSuccess && patchLevelResult.output.isNotEmpty()) {
                patches.add(
                    SecurityPatch(
                        id = "android_security_patch",
                        level = patchLevelResult.output.trim(),
                        description = "Android Security Patch Level",
                        patchDate = patchLevelResult.output.trim(),
                        isApplied = true
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Error extracting security patches", e)
        }
        
        return patches
    }
    
    /**
     * Calculate checksum for extracted file
     */
    private suspend fun calculateChecksum(file: File): String = withContext(Dispatchers.IO) {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(tag, "Error calculating checksum", e)
            "unknown"
        }
    }
}

/**
 * Partition information helper class
 */
private data class PartitionInfo(
    val mountPoint: String,
    val fileSystem: String
)

