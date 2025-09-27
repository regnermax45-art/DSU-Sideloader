package vegabobo.dsusideloader.porting

import android.os.Build
import android.util.Log
import vegabobo.dsusideloader.model.*
import vegabobo.dsusideloader.service.PrivilegedProvider
import vegabobo.dsusideloader.util.CmdRunner

/**
 * Device Profile Manager for hardware-specific configurations and optimizations
 * Provides device-specific settings for optimal GSI porting
 */
class DeviceProfileManager {
    private val tag = this.javaClass.simpleName
    
    /**
     * Get device profile for current device
     */
    suspend fun getDeviceProfile(): DeviceProfile {
        val deviceModel = Build.MODEL
        val deviceCodename = getDeviceCodename()
        
        Log.d(tag, "Getting device profile for: $deviceModel ($deviceCodename)")
        
        return when {
            isPixel7Pro(deviceModel, deviceCodename) -> getPixel7ProProfile()
            isPixel7(deviceModel, deviceCodename) -> getPixel7Profile()
            isPixel6Pro(deviceModel, deviceCodename) -> getPixel6ProProfile()
            isPixel6(deviceModel, deviceCodename) -> getPixel6Profile()
            isPixelDevice(deviceModel) -> getGenericPixelProfile()
            else -> getGenericDeviceProfile()
        }
    }
    
    /**
     * Get Pixel 7 Pro specific device profile
     */
    private suspend fun getPixel7ProProfile(): DeviceProfile {
        return DeviceProfile(
            deviceName = "Pixel 7 Pro",
            codename = "cheetah",
            manufacturer = "Google",
            model = "Pixel 7 Pro",
            androidVersion = Build.VERSION.RELEASE,
            securityPatchLevel = getSecurityPatchLevel(),
            buildFingerprint = Build.FINGERPRINT,
            
            // Hardware specifications
            soc = "Google Tensor G2",
            architecture = "arm64-v8a",
            cpuCores = 8,
            ramSize = 12L * 1024L * 1024L * 1024L, // 12GB
            storageSize = 128L * 1024L * 1024L * 1024L, // 128GB base model
            
            // Feature support
            supportedFeatures = listOf(
                "android.hardware.camera.flash",
                "android.hardware.camera.front",
                "android.hardware.camera.any",
                "android.hardware.fingerprint",
                "android.hardware.sensor.accelerometer",
                "android.hardware.sensor.gyroscope",
                "android.hardware.sensor.light",
                "android.hardware.sensor.proximity",
                "android.hardware.sensor.barometer",
                "android.hardware.sensor.compass",
                "android.hardware.location.gps",
                "android.hardware.wifi",
                "android.hardware.wifi.direct",
                "android.hardware.bluetooth",
                "android.hardware.bluetooth_le",
                "android.hardware.telephony.gsm",
                "android.hardware.telephony.cdma",
                "android.hardware.telephony.ims",
                "android.hardware.nfc",
                "android.hardware.nfc.hce",
                "android.hardware.usb.host",
                "android.hardware.usb.accessory",
                "android.hardware.audio.low_latency",
                "android.hardware.audio.pro",
                "android.hardware.microphone",
                "android.hardware.screen.landscape",
                "android.hardware.screen.portrait",
                "android.hardware.vulkan.level",
                "android.hardware.vulkan.version",
                "android.hardware.opengles.aep"
            ),
            
            hardwareFeatures = mapOf(
                "5g_support" to true,
                "wireless_charging" to true,
                "reverse_wireless_charging" to true,
                "face_unlock" to true,
                "fingerprint_unlock" to true,
                "always_on_display" to true,
                "adaptive_refresh_rate" to true,
                "hdr_display" to true,
                "stereo_speakers" to true,
                "ip68_rating" to true,
                "esim_support" to true,
                "dual_sim" to true,
                "ultra_wide_camera" to true,
                "telephoto_camera" to true,
                "night_sight" to true,
                "magic_eraser" to true,
                "live_translate" to true,
                "car_crash_detection" to true,
                "titan_m_security" to true
            ),
            
            // Porting compatibility
            portingSupported = true,
            knownIssues = listOf(
                "Camera HAL may require specific Tensor G2 optimizations",
                "5G modem drivers need careful integration",
                "Adaptive refresh rate requires display HAL patches",
                "Titan M security chip may cause bootloader verification issues"
            ),
            
            recommendedPatches = listOf(
                PatchType.CAMERA_HAL,
                PatchType.GRAPHICS_DRIVERS,
                PatchType.SENSOR_DRIVERS,
                PatchType.CONNECTIVITY_DRIVERS,
                PatchType.PERFORMANCE_TWEAKS,
                PatchType.BATTERY_OPTIMIZATION,
                PatchType.THERMAL_MANAGEMENT,
                PatchType.PIXEL_FEATURES,
                PatchType.GOOGLE_SERVICES
            )
        )
    }
    
    /**
     * Get Pixel 7 device profile
     */
    private suspend fun getPixel7Profile(): DeviceProfile {
        return DeviceProfile(
            deviceName = "Pixel 7",
            codename = "panther",
            manufacturer = "Google",
            model = "Pixel 7",
            androidVersion = Build.VERSION.RELEASE,
            securityPatchLevel = getSecurityPatchLevel(),
            buildFingerprint = Build.FINGERPRINT,
            
            // Hardware specifications
            soc = "Google Tensor G2",
            architecture = "arm64-v8a",
            cpuCores = 8,
            ramSize = 8L * 1024L * 1024L * 1024L, // 8GB
            storageSize = 128L * 1024L * 1024L * 1024L, // 128GB base model
            
            // Feature support (similar to 7 Pro but without some premium features)
            supportedFeatures = listOf(
                "android.hardware.camera.flash",
                "android.hardware.camera.front",
                "android.hardware.camera.any",
                "android.hardware.fingerprint",
                "android.hardware.sensor.accelerometer",
                "android.hardware.sensor.gyroscope",
                "android.hardware.sensor.light",
                "android.hardware.sensor.proximity",
                "android.hardware.sensor.barometer",
                "android.hardware.sensor.compass",
                "android.hardware.location.gps",
                "android.hardware.wifi",
                "android.hardware.bluetooth",
                "android.hardware.telephony.gsm",
                "android.hardware.nfc",
                "android.hardware.usb.host",
                "android.hardware.audio.low_latency",
                "android.hardware.microphone"
            ),
            
            hardwareFeatures = mapOf(
                "5g_support" to true,
                "wireless_charging" to true,
                "face_unlock" to true,
                "fingerprint_unlock" to true,
                "always_on_display" to true,
                "hdr_display" to true,
                "stereo_speakers" to true,
                "ip68_rating" to true,
                "esim_support" to true,
                "dual_sim" to true,
                "ultra_wide_camera" to true,
                "telephoto_camera" to false, // No telephoto on regular Pixel 7
                "night_sight" to true,
                "magic_eraser" to true,
                "live_translate" to true,
                "car_crash_detection" to true,
                "titan_m_security" to true
            ),
            
            portingSupported = true,
            knownIssues = listOf(
                "Camera HAL may require specific Tensor G2 optimizations",
                "5G modem drivers need careful integration",
                "Titan M security chip may cause bootloader verification issues"
            ),
            
            recommendedPatches = listOf(
                PatchType.CAMERA_HAL,
                PatchType.GRAPHICS_DRIVERS,
                PatchType.SENSOR_DRIVERS,
                PatchType.CONNECTIVITY_DRIVERS,
                PatchType.PERFORMANCE_TWEAKS,
                PatchType.PIXEL_FEATURES,
                PatchType.GOOGLE_SERVICES
            )
        )
    }
    
    /**
     * Get Pixel 6 Pro device profile
     */
    private suspend fun getPixel6ProProfile(): DeviceProfile {
        return DeviceProfile(
            deviceName = "Pixel 6 Pro",
            codename = "raven",
            manufacturer = "Google",
            model = "Pixel 6 Pro",
            androidVersion = Build.VERSION.RELEASE,
            securityPatchLevel = getSecurityPatchLevel(),
            buildFingerprint = Build.FINGERPRINT,
            
            soc = "Google Tensor",
            architecture = "arm64-v8a",
            cpuCores = 8,
            ramSize = 12L * 1024L * 1024L * 1024L,
            storageSize = 128L * 1024L * 1024L * 1024L,
            
            supportedFeatures = listOf(
                "android.hardware.camera.flash",
                "android.hardware.camera.front",
                "android.hardware.fingerprint",
                "android.hardware.sensor.accelerometer",
                "android.hardware.location.gps",
                "android.hardware.wifi",
                "android.hardware.bluetooth",
                "android.hardware.nfc"
            ),
            
            hardwareFeatures = mapOf(
                "5g_support" to true,
                "wireless_charging" to true,
                "face_unlock" to true,
                "fingerprint_unlock" to true,
                "telephoto_camera" to true,
                "ultra_wide_camera" to true,
                "titan_m_security" to true
            ),
            
            portingSupported = true,
            knownIssues = listOf(
                "First generation Tensor may have stability issues",
                "Camera HAL requires Tensor-specific optimizations"
            ),
            
            recommendedPatches = listOf(
                PatchType.CAMERA_HAL,
                PatchType.GRAPHICS_DRIVERS,
                PatchType.PERFORMANCE_TWEAKS,
                PatchType.PIXEL_FEATURES
            )
        )
    }
    
    /**
     * Get Pixel 6 device profile
     */
    private suspend fun getPixel6Profile(): DeviceProfile {
        return DeviceProfile(
            deviceName = "Pixel 6",
            codename = "oriole",
            manufacturer = "Google",
            model = "Pixel 6",
            androidVersion = Build.VERSION.RELEASE,
            securityPatchLevel = getSecurityPatchLevel(),
            buildFingerprint = Build.FINGERPRINT,
            
            soc = "Google Tensor",
            architecture = "arm64-v8a",
            cpuCores = 8,
            ramSize = 8L * 1024L * 1024L * 1024L,
            storageSize = 128L * 1024L * 1024L * 1024L,
            
            supportedFeatures = listOf(
                "android.hardware.camera.flash",
                "android.hardware.camera.front",
                "android.hardware.fingerprint",
                "android.hardware.sensor.accelerometer",
                "android.hardware.location.gps",
                "android.hardware.wifi",
                "android.hardware.bluetooth",
                "android.hardware.nfc"
            ),
            
            hardwareFeatures = mapOf(
                "5g_support" to true,
                "wireless_charging" to true,
                "face_unlock" to true,
                "fingerprint_unlock" to true,
                "telephoto_camera" to false,
                "ultra_wide_camera" to true,
                "titan_m_security" to true
            ),
            
            portingSupported = true,
            knownIssues = listOf(
                "First generation Tensor may have stability issues",
                "Camera HAL requires Tensor-specific optimizations"
            ),
            
            recommendedPatches = listOf(
                PatchType.CAMERA_HAL,
                PatchType.GRAPHICS_DRIVERS,
                PatchType.PERFORMANCE_TWEAKS,
                PatchType.PIXEL_FEATURES
            )
        )
    }
    
    /**
     * Get generic Pixel device profile for other Pixel devices
     */
    private suspend fun getGenericPixelProfile(): DeviceProfile {
        return DeviceProfile(
            deviceName = Build.MODEL,
            codename = getDeviceCodename(),
            manufacturer = "Google",
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            securityPatchLevel = getSecurityPatchLevel(),
            buildFingerprint = Build.FINGERPRINT,
            
            soc = "Unknown",
            architecture = Build.SUPPORTED_ABIS[0],
            cpuCores = Runtime.getRuntime().availableProcessors(),
            ramSize = 0L,
            storageSize = 0L,
            
            supportedFeatures = listOf(
                "android.hardware.camera.any",
                "android.hardware.location.gps",
                "android.hardware.wifi",
                "android.hardware.bluetooth"
            ),
            
            hardwareFeatures = mapOf(
                "pixel_device" to true
            ),
            
            portingSupported = true,
            knownIssues = listOf(
                "Generic Pixel profile - may require device-specific adjustments"
            ),
            
            recommendedPatches = listOf(
                PatchType.CAMERA_HAL,
                PatchType.GRAPHICS_DRIVERS,
                PatchType.PIXEL_FEATURES
            )
        )
    }
    
    /**
     * Get generic device profile for non-Pixel devices
     */
    private suspend fun getGenericDeviceProfile(): DeviceProfile {
        return DeviceProfile(
            deviceName = Build.MODEL,
            codename = getDeviceCodename(),
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            securityPatchLevel = getSecurityPatchLevel(),
            buildFingerprint = Build.FINGERPRINT,
            
            soc = "Unknown",
            architecture = Build.SUPPORTED_ABIS[0],
            cpuCores = Runtime.getRuntime().availableProcessors(),
            ramSize = 0L,
            storageSize = 0L,
            
            supportedFeatures = listOf(
                "android.hardware.camera.any",
                "android.hardware.location.gps",
                "android.hardware.wifi"
            ),
            
            hardwareFeatures = mapOf(),
            
            portingSupported = false, // Conservative default for unknown devices
            knownIssues = listOf(
                "Generic device profile - porting may not be fully supported",
                "Device-specific drivers and HALs may not be available",
                "Hardware features may not work correctly"
            ),
            
            recommendedPatches = listOf(
                PatchType.GRAPHICS_DRIVERS,
                PatchType.PERFORMANCE_TWEAKS
            )
        )
    }
    
    /**
     * Check if device is Pixel 7 Pro
     */
    private fun isPixel7Pro(model: String, codename: String): Boolean {
        return model.contains("Pixel 7 Pro", ignoreCase = true) || 
               codename.equals("cheetah", ignoreCase = true)
    }
    
    /**
     * Check if device is Pixel 7
     */
    private fun isPixel7(model: String, codename: String): Boolean {
        return (model.contains("Pixel 7", ignoreCase = true) && !model.contains("Pro", ignoreCase = true)) ||
               codename.equals("panther", ignoreCase = true)
    }
    
    /**
     * Check if device is Pixel 6 Pro
     */
    private fun isPixel6Pro(model: String, codename: String): Boolean {
        return model.contains("Pixel 6 Pro", ignoreCase = true) ||
               codename.equals("raven", ignoreCase = true)
    }
    
    /**
     * Check if device is Pixel 6
     */
    private fun isPixel6(model: String, codename: String): Boolean {
        return (model.contains("Pixel 6", ignoreCase = true) && !model.contains("Pro", ignoreCase = true)) ||
               codename.equals("oriole", ignoreCase = true)
    }
    
    /**
     * Check if device is any Pixel device
     */
    private fun isPixelDevice(model: String): Boolean {
        return model.contains("Pixel", ignoreCase = true) ||
               Build.MANUFACTURER.equals("Google", ignoreCase = true)
    }
    
    /**
     * Get device codename
     */
    private suspend fun getDeviceCodename(): String {
        return try {
            val result = PrivilegedProvider.run {
                CmdRunner.runCommand("getprop ro.product.device")
            }
            if (result.isSuccess && result.output.isNotEmpty()) {
                result.output.trim()
            } else {
                Build.DEVICE
            }
        } catch (e: Exception) {
            Log.e(tag, "Error getting device codename", e)
            Build.DEVICE
        }
    }
    
    /**
     * Get security patch level
     */
    private suspend fun getSecurityPatchLevel(): String {
        return try {
            val result = PrivilegedProvider.run {
                CmdRunner.runCommand("getprop ro.build.version.security_patch")
            }
            if (result.isSuccess && result.output.isNotEmpty()) {
                result.output.trim()
            } else {
                Build.VERSION.SECURITY_PATCH
            }
        } catch (e: Exception) {
            Log.e(tag, "Error getting security patch level", e)
            Build.VERSION.SECURITY_PATCH
        }
    }
    
    /**
     * Get available device profiles
     */
    fun getAvailableProfiles(): List<String> {
        return listOf(
            "pixel_7_pro",
            "pixel_7",
            "pixel_6_pro",
            "pixel_6",
            "generic_pixel",
            "generic_device"
        )
    }
    
    /**
     * Get device profile by name
     */
    suspend fun getDeviceProfileByName(profileName: String): DeviceProfile {
        return when (profileName) {
            "pixel_7_pro" -> getPixel7ProProfile()
            "pixel_7" -> getPixel7Profile()
            "pixel_6_pro" -> getPixel6ProProfile()
            "pixel_6" -> getPixel6Profile()
            "generic_pixel" -> getGenericPixelProfile()
            "generic_device" -> getGenericDeviceProfile()
            else -> getDeviceProfile() // Auto-detect
        }
    }
}

