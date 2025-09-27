package vegabobo.dsusideloader.model

/**
 * GSI Porting Models for Ultra Deep Patching System
 * Designed specifically for Pixel 7 Pro devices
 */

// Basic enums for porting configuration
enum class MergeStrategy {
    REPLACE,
    MERGE,
    SKIP,
}

enum class PatchLevel {
    BASIC,
    DEEP,
    ULTRA_DEEP,
}

enum class DeviceProfile {
    PIXEL_7_PRO,
    GENERIC,
}

// Simple data classes without complex dependencies
data class PortingPreferences(
    val patchLevel: PatchLevel = PatchLevel.ULTRA_DEEP,
    val deviceProfile: DeviceProfile = DeviceProfile.PIXEL_7_PRO,
    val enableSystemPatches: Boolean = true,
    val enableVendorPatches: Boolean = true,
    val enableBootPatches: Boolean = true,
    val mergeStrategy: MergeStrategy = MergeStrategy.MERGE,
    val preserveOriginalImage: Boolean = true,
)

// Exception class for porting operations
class PortingException(message: String, cause: Throwable? = null) : Exception(message, cause)

// Result wrapper for porting operations
data class PortingResult<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
) {
    companion object {
        fun <T> success(data: T): PortingResult<T> = PortingResult(true, data, null)
        fun <T> failure(error: String): PortingResult<T> = PortingResult(false, null, error)
    }
}
