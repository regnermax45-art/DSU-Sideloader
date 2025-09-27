package vegabobo.dsusideloader.porting

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import vegabobo.dsusideloader.core.StorageManager
import vegabobo.dsusideloader.model.Session

/**
 * Core GSI Porting Engine that orchestrates the entire porting process
 * Transforms GSI images using current system components and ultra deep patches
 */
class GsiPortingEngine(
    private val context: Context,
    private val session: Session,
    private val storageManager: StorageManager,
) {
    private val tag = this.javaClass.simpleName
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Components
    private val systemExtractor = SystemExtractor(context, session, storageManager)
    private val patchingFramework = PatchingFramework(context, session, storageManager)
    private val imageMerger = ImageMerger(context, session, storageManager)
    private val deviceProfileManager = DeviceProfileManager(context)

    // State management
    private val _portingState = MutableStateFlow(PortingState.IDLE)
    val portingState: StateFlow<PortingState> = _portingState

    private val _portingProgress = MutableStateFlow(PortingProgress())
    val portingProgress: StateFlow<PortingProgress> = _portingProgress

    /**
     * Start the complete GSI porting process
     */
    fun startPorting(
        gsiImagePath: String,
        portingPreferences: PortingPreferences,
        onComplete: (Boolean, String?) -> Unit,
    ) {
        Log.d(tag, "Starting GSI porting process for: $gsiImagePath")

        coroutineScope.launch {
            try {
                _portingState.value = PortingState.RUNNING
                updateProgress("Initializing porting process...", 0)

                // Step 1: Validate device compatibility
                if (!validateDeviceCompatibility()) {
                    throw PortingException("Device not compatible with porting process")
                }
                updateProgress("Device validation complete", 10)

                // Step 2: Extract current system components
                updateProgress("Extracting system components...", 20)
                val systemComponents = systemExtractor.extractSystemComponents()
                updateProgress("System extraction complete", 40)

                // Step 3: Apply ultra deep patches to GSI
                updateProgress("Applying ultra deep patches...", 50)
                val patchedGsiPath = patchingFramework.applyPatches(
                    gsiImagePath,
                    systemComponents,
                    portingPreferences,
                )
                updateProgress("Patching complete", 70)

                // Step 4: Merge system components with patched GSI
                updateProgress("Merging system components...", 80)
                val finalImagePath = imageMerger.mergeImages(
                    patchedGsiPath,
                    systemComponents,
                    portingPreferences,
                )
                updateProgress("Image merging complete", 90)

                // Step 5: Validate final image
                updateProgress("Validating final image...", 95)
                if (!validateFinalImage(finalImagePath)) {
                    throw PortingException("Final image validation failed")
                }

                updateProgress("Porting process complete!", 100)
                _portingState.value = PortingState.COMPLETED

                // Update session with ported image
                session.dsuInstallation.uri = android.net.Uri.parse("file://$finalImagePath")
                session.dsuInstallation.fileSize = java.io.File(finalImagePath).length()

                onComplete(true, finalImagePath)
                Log.d(tag, "GSI porting completed successfully: $finalImagePath")
            } catch (e: Exception) {
                Log.e(tag, "GSI porting failed", e)
                _portingState.value = PortingState.ERROR
                updateProgress("Porting failed: ${e.message}", -1)
                onComplete(false, e.message)
            }
        }
    }

    /**
     * Cancel ongoing porting operation
     */
    fun cancelPorting() {
        Log.d(tag, "Cancelling porting operation")
        _portingState.value = PortingState.CANCELLED
        // TODO: Implement cleanup of temporary files
    }

    /**
     * Validate device compatibility for porting
     */
    private suspend fun validateDeviceCompatibility(): Boolean {
        val deviceProfile = deviceProfileManager.getDeviceProfile()
        return deviceProfile.isPortingSupported()
    }

    /**
     * Validate the final ported image
     */
    private suspend fun validateFinalImage(imagePath: String): Boolean {
        val imageFile = java.io.File(imagePath)
        if (!imageFile.exists() || imageFile.length() == 0L) {
            return false
        }

        // TODO: Add more comprehensive image validation
        // - Check image format
        // - Verify partition structure
        // - Validate system integrity

        return true
    }

    /**
     * Update porting progress
     */
    private fun updateProgress(message: String, percentage: Int) {
        _portingProgress.value = PortingProgress(
            currentStage = message,
            percentage = percentage,
            isError = percentage < 0,
        )
        Log.d(tag, "Progress: $percentage% - $message")
    }

    /**
     * Get estimated storage requirements for porting operation
     */
    fun getStorageRequirements(gsiImageSize: Long): StorageRequirements {
        // Estimate storage needed:
        // - Original GSI: gsiImageSize
        // - System extraction: ~4GB
        // - Patched GSI: gsiImageSize * 1.2 (with patches)
        // - Final merged image: gsiImageSize * 1.3
        // - Temporary files: gsiImageSize * 0.5

        val systemExtractionSize = 4L * 1024L * 1024L * 1024L // 4GB
        val patchedGsiSize = (gsiImageSize * 1.2).toLong()
        val finalImageSize = (gsiImageSize * 1.3).toLong()
        val temporaryFilesSize = (gsiImageSize * 0.5).toLong()

        val totalRequired = systemExtractionSize + patchedGsiSize + finalImageSize + temporaryFilesSize

        return StorageRequirements(
            totalRequired = totalRequired,
            systemExtraction = systemExtractionSize,
            patchedGsi = patchedGsiSize,
            finalImage = finalImageSize,
            temporaryFiles = temporaryFilesSize,
        )
    }
}

/**
 * Porting operation states
 */
enum class PortingState {
    IDLE,
    RUNNING,
    COMPLETED,
    ERROR,
    CANCELLED,
}

/**
 * Porting progress information
 */
data class PortingProgress(
    val currentStage: String = "",
    val percentage: Int = 0,
    val isError: Boolean = false,
)

/**
 * Storage requirements for porting operation
 */
data class StorageRequirements(
    val totalRequired: Long,
    val systemExtraction: Long,
    val patchedGsi: Long,
    val finalImage: Long,
    val temporaryFiles: Long,
)

/**
 * Custom exception for porting operations
 */
class PortingException(message: String, cause: Throwable? = null) : Exception(message, cause)
