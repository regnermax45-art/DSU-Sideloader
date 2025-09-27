package vegabobo.dsusideloader.installer.privileged

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import vegabobo.dsusideloader.core.StorageManager
import vegabobo.dsusideloader.model.Session
import vegabobo.dsusideloader.porting.GsiPortingEngine
import vegabobo.dsusideloader.porting.PortingException
import vegabobo.dsusideloader.util.StorageHelper

/**
 * Installation handler that integrates GSI porting with DSU installation
 * Extends the standard installation process with porting capabilities
 */
class PortingInstallationHandler(
    private val context: Context,
    private val session: Session,
    private val storageManager: StorageManager,
) : DsuInstallationHandler(session) {

    private val tag = this.javaClass.simpleName
    private val portingEngine = GsiPortingEngine(context, session, storageManager)
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Start installation with porting support
     */
    fun startPortingInstallation(
        onProgress: (String, Int) -> Unit = { _, _ -> },
        onComplete: (Boolean, String?) -> Unit = { _, _ -> },
    ) {
        Log.d(tag, "Starting porting installation process")

        coroutineScope.launch {
            try {
                if (session.preferences.enablePorting) {
                    // Step 1: Validate storage requirements
                    validateStorageRequirements(onProgress)

                    // Step 2: Start GSI porting process
                    startGsiPorting(onProgress, onComplete)
                } else {
                    // Fallback to standard installation
                    Log.d(tag, "Porting disabled, using standard installation")
                    startInstallation()
                    onComplete(true, null)
                }
            } catch (e: Exception) {
                Log.e(tag, "Porting installation failed", e)
                onComplete(false, e.message)
            }
        }
    }

    /**
     * Validate storage requirements before starting porting
     */
    private suspend fun validateStorageRequirements(onProgress: (String, Int) -> Unit) {
        onProgress("Validating storage requirements...", 5)

        val gsiSize = session.dsuInstallation.fileSize
        val storageRequirements = portingEngine.getStorageRequirements(gsiSize)

        val availableSpace = StorageHelper.getAvailableSpace(context)

        if (availableSpace < storageRequirements.totalRequired) {
            val requiredGB = storageRequirements.totalRequired / (1024L * 1024L * 1024L)
            val availableGB = availableSpace / (1024L * 1024L * 1024L)

            throw PortingException(
                "Insufficient storage space. Required: ${requiredGB}GB, Available: ${availableGB}GB",
            )
        }

        Log.d(tag, "Storage validation passed. Required: ${storageRequirements.totalRequired} bytes")
    }

    /**
     * Start the GSI porting process
     */
    private suspend fun startGsiPorting(
        onProgress: (String, Int) -> Unit,
        onComplete: (Boolean, String?) -> Unit,
    ) {
        val gsiImagePath = session.dsuInstallation.uri.path
        if (gsiImagePath == null) {
            throw PortingException("Invalid GSI image path")
        }

        // Monitor porting progress
        val progressJob = coroutineScope.launch {
            portingEngine.portingProgress.collect { progress ->
                onProgress(progress.currentStage, progress.percentage)
            }
        }

        // Start porting process
        portingEngine.startPorting(
            gsiImagePath = gsiImagePath,
            portingPreferences = session.preferences.portingPreferences,
            onComplete = { success, result ->
                progressJob.cancel()

                if (success && result != null) {
                    Log.d(tag, "Porting completed successfully, starting DSU installation")

                    // Update session with ported image
                    session.dsuInstallation.uri = android.net.Uri.parse("file://$result")
                    session.dsuInstallation.fileSize = java.io.File(result).length()

                    // Start standard DSU installation with ported image
                    coroutineScope.launch {
                        try {
                            startInstallation()
                            onComplete(true, "Porting and installation completed successfully")
                        } catch (e: Exception) {
                            Log.e(tag, "DSU installation failed after porting", e)
                            onComplete(false, "Installation failed: ${e.message}")
                        }
                    }
                } else {
                    Log.e(tag, "Porting failed: $result")
                    onComplete(false, "Porting failed: $result")
                }
            },
        )
    }

    /**
     * Cancel ongoing porting operation
     */
    fun cancelPorting() {
        Log.d(tag, "Cancelling porting operation")
        portingEngine.cancelPorting()
    }

    /**
     * Get current porting state
     */
    fun getPortingState() = portingEngine.portingState

    /**
     * Get current porting progress
     */
    fun getPortingProgress() = portingEngine.portingProgress

    /**
     * Check if porting is supported for current device
     */
    suspend fun isPortingSupported(): Boolean {
        return try {
            val deviceProfileManager = vegabobo.dsusideloader.porting.DeviceProfileManager()
            val deviceProfile = deviceProfileManager.getDeviceProfile()
            deviceProfile.isPortingSupported()
        } catch (e: Exception) {
            Log.e(tag, "Error checking porting support", e)
            false
        }
    }

    /**
     * Get recommended porting settings for current device
     */
    suspend fun getRecommendedPortingSettings(): vegabobo.dsusideloader.model.PortingPreferences {
        return try {
            val deviceProfileManager = vegabobo.dsusideloader.porting.DeviceProfileManager()
            val deviceProfile = deviceProfileManager.getDeviceProfile()

            vegabobo.dsusideloader.model.PortingPreferences(
                enableUltraDeepPatches = true,
                patchLevel = deviceProfile.getRecommendedPatchLevel(),
                selectedPatches = deviceProfile.recommendedPatches,
                deviceProfile = when {
                    deviceProfile.codename.equals("cheetah", ignoreCase = true) -> "pixel_7_pro"
                    deviceProfile.codename.equals("panther", ignoreCase = true) -> "pixel_7"
                    deviceProfile.codename.equals("raven", ignoreCase = true) -> "pixel_6_pro"
                    deviceProfile.codename.equals("oriole", ignoreCase = true) -> "pixel_6"
                    deviceProfile.manufacturer.equals("Google", ignoreCase = true) -> "generic_pixel"
                    else -> "generic_device"
                },
                optimizeForDevice = true,
                preserveSystemApps = true,
                extractSystemPartition = true,
                extractVendorPartition = true,
                extractBootPartition = false,
                extractProductPartition = true,
                mergeStrategy = vegabobo.dsusideloader.model.MergeStrategy.INTELLIGENT,
                preserveGsiFeatures = true,
                enableHardwareOptimization = true,
                compressionLevel = 6,
                enableVerification = true,
                createBackup = false,
            )
        } catch (e: Exception) {
            Log.e(tag, "Error getting recommended settings", e)
            vegabobo.dsusideloader.model.PortingPreferences() // Default settings
        }
    }

    /**
     * Estimate porting time based on GSI size and device performance
     */
    fun estimatePortingTime(gsiSizeBytes: Long): Long {
        // Base time estimates in minutes
        val baseExtractionTime = 5L // System extraction
        val basePatchingTime = 10L // Patching process
        val baseMergingTime = 8L // Image merging
        val baseCompressionTime = 3L // Compression

        // Scale based on GSI size (assuming 4GB baseline)
        val baselineSize = 4L * 1024L * 1024L * 1024L // 4GB
        val sizeMultiplier = (gsiSizeBytes.toDouble() / baselineSize.toDouble()).coerceAtLeast(1.0)

        val totalMinutes = ((baseExtractionTime + basePatchingTime + baseMergingTime + baseCompressionTime) * sizeMultiplier).toLong()

        return totalMinutes * 60L // Convert to seconds
    }

    /**
     * Get porting statistics
     */
    fun getPortingStatistics(): PortingStatistics {
        // This would be populated during actual porting operations
        return PortingStatistics(
            totalPortingOperations = 0,
            successfulPortings = 0,
            failedPortings = 0,
            averagePortingTime = 0L,
            totalDataProcessed = 0L,
        )
    }
}

/**
 * Porting statistics data class
 */
data class PortingStatistics(
    val totalPortingOperations: Int,
    val successfulPortings: Int,
    val failedPortings: Int,
    val averagePortingTime: Long, // in seconds
    val totalDataProcessed: Long, // in bytes
) {
    val successRate: Float
        get() = if (totalPortingOperations > 0) {
            (successfulPortings.toFloat() / totalPortingOperations.toFloat()) * 100f
        } else {
            0f
        }
}
