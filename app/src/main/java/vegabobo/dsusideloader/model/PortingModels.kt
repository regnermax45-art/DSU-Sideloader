package vegabobo.dsusideloader.model

/**
 * Porting preferences and configuration
 */
data class PortingPreferences(
    // Patching options
    val enableUltraDeepPatches: Boolean = true,
    val patchLevel: PatchLevel = PatchLevel.DEEP,
    val selectedPatches: List<PatchType> = listOf(),

    // Device optimization
    val deviceProfile: String = "pixel_7_pro",
    val optimizeForDevice: Boolean = true,
    val preserveSystemApps: Boolean = true,

    // System extraction options
    val extractSystemPartition: Boolean = true,
    val extractVendorPartition: Boolean = true,
    val extractBootPartition: Boolean = false,
    val extractProductPartition: Boolean = true,

    // Merging options
    val mergeStrategy: MergeStrategy = MergeStrategy.INTELLIGENT,
    val preserveGsiFeatures: Boolean = true,
    val enableHardwareOptimization: Boolean = true,

    // Advanced options
    val compressionLevel: Int = 6,
    val enableVerification: Boolean = true,
    val createBackup: Boolean = false,
)

/**
 * Patch levels for different depths of modification
 */
enum class PatchLevel {
    SURFACE, // Basic compatibility patches
    DEEP, // System-level modifications
    ULTRA_DEEP, // Kernel and low-level patches
}

/**
 * Types of patches that can be applied
 */
enum class PatchType {
    // Hardware compatibility
    CAMERA_HAL,
    AUDIO_HAL,
    GRAPHICS_DRIVERS,
    SENSOR_DRIVERS,
    CONNECTIVITY_DRIVERS,

    // System optimizations
    PERFORMANCE_TWEAKS,
    BATTERY_OPTIMIZATION,
    THERMAL_MANAGEMENT,
    MEMORY_MANAGEMENT,

    // Security patches
    SELINUX_POLICIES,
    SECURITY_PATCHES,
    BOOTLOADER_PATCHES,

    // Feature additions
    PIXEL_FEATURES,
    GOOGLE_SERVICES,
    SYSTEM_UI_MODS,
    FRAMEWORK_MODS,

    // Custom patches
    USER_DEFINED,
}

/**
 * Strategies for merging system and GSI components
 */
enum class MergeStrategy {
    REPLACE, // Replace GSI components with system ones
    MERGE, // Merge compatible components
    INTELLIGENT, // AI-driven merging based on compatibility
    PRESERVE_GSI, // Preserve GSI, add system drivers only
    PRESERVE_SYSTEM, // Preserve system, add GSI features only
}

/**
 * System components extracted from current device
 */
data class SystemComponents(
    val systemPartition: ExtractedPartition? = null,
    val vendorPartition: ExtractedPartition? = null,
    val bootPartition: ExtractedPartition? = null,
    val productPartition: ExtractedPartition? = null,
    val odmPartition: ExtractedPartition? = null,

    // Hardware abstraction layers
    val halComponents: List<HalComponent> = listOf(),

    // Driver information
    val drivers: List<DriverInfo> = listOf(),

    // System properties
    val buildProperties: Map<String, String> = mapOf(),
    val systemProperties: Map<String, String> = mapOf(),

    // Security information
    val selinuxPolicies: List<String> = listOf(),
    val securityPatches: List<SecurityPatch> = listOf(),
)

/**
 * Information about an extracted partition
 */
data class ExtractedPartition(
    val name: String,
    val path: String,
    val size: Long,
    val mountPoint: String,
    val fileSystem: String,
    val extractedPath: String,
    val checksum: String,
)

/**
 * Hardware Abstraction Layer component information
 */
data class HalComponent(
    val name: String,
    val version: String,
    val path: String,
    val interfaceName: String,
    val vendor: String,
    val isEssential: Boolean = false,
)

/**
 * Porting preferences and configuration
 */
data class PortingPreferences(
    val patchingLevel: PatchingLevel = PatchingLevel.ULTRA_DEEP,
    val mergeStrategy: MergeStrategy = MergeStrategy.INTELLIGENT,
    val preserveUserData: Boolean = true,
    val enableHardwareOptimizations: Boolean = true,
    val enablePerformanceTuning: Boolean = true,
    val customPatches: List<String> = emptyList(),
    val skipValidation: Boolean = false,
    val compressionLevel: Int = 6,
    val parallelProcessing: Boolean = true,
    val maxConcurrentOperations: Int = 4,
)

/**
 * Driver information
 */
data class DriverInfo(
    val name: String,
    val version: String,
    val path: String,
    val deviceNodes: List<String> = listOf(),
    val dependencies: List<String> = listOf(),
    val isKernelModule: Boolean = false,
)

/**
 * Security patch information
 */
data class SecurityPatch(
    val id: String,
    val level: String,
    val description: String,
    val patchDate: String,
    val isApplied: Boolean = false,
)

/**
 * Device profile for hardware-specific optimizations
 */
data class DeviceProfile(
    val deviceName: String,
    val codename: String,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val securityPatchLevel: String,
    val buildFingerprint: String,

    // Hardware specifications
    val soc: String,
    val architecture: String,
    val cpuCores: Int,
    val ramSize: Long,
    val storageSize: Long,

    // Feature support
    val supportedFeatures: List<String> = listOf(),
    val hardwareFeatures: Map<String, Boolean> = mapOf(),

    // Porting compatibility
    val portingSupported: Boolean = true,
    val knownIssues: List<String> = listOf(),
    val recommendedPatches: List<PatchType> = listOf(),
) {
    fun isPortingSupported(): Boolean = portingSupported

    fun getRecommendedPatchLevel(): PatchLevel {
        return when {
            knownIssues.isNotEmpty() -> PatchLevel.ULTRA_DEEP
            hardwareFeatures.values.any { !it } -> PatchLevel.DEEP
            else -> PatchLevel.SURFACE
        }
    }
}

/**
 * Patch definition for applying modifications
 */
data class PatchDefinition(
    val id: String,
    val name: String,
    val description: String,
    val type: PatchType,
    val level: PatchLevel,
    val targetFiles: List<String> = listOf(),
    val patchData: ByteArray? = null,
    val scriptPath: String? = null,
    val dependencies: List<String> = listOf(),
    val deviceCompatibility: List<String> = listOf(),
    val isReversible: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PatchDefinition

        if (id != other.id) return false
        if (name != other.name) return false
        if (type != other.type) return false
        if (level != other.level) return false
        if (patchData != null) {
            if (other.patchData == null) return false
            if (!patchData.contentEquals(other.patchData)) return false
        } else if (other.patchData != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + level.hashCode()
        result = 31 * result + (patchData?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Result of a porting operation
 */
data class PortingResult(
    val success: Boolean,
    val outputImagePath: String? = null,
    val appliedPatches: List<String> = listOf(),
    val warnings: List<String> = listOf(),
    val errors: List<String> = listOf(),
    val processingTime: Long = 0L,
    val finalImageSize: Long = 0L,
    val compressionRatio: Float = 0f,
)
