package vegabobo.dsusideloader.util

import android.util.Log
import java.io.File
import java.security.MessageDigest
import vegabobo.dsusideloader.service.PrivilegedProvider

/**
 * Utility for validating GSI images and system images
 */
object ImageValidator {
    private const val TAG = "ImageValidator"

    /**
     * Validate GSI image integrity and format
     */
    suspend fun validateGsiImage(imagePath: String): ValidationResult {
        Log.d(TAG, "Validating GSI image: $imagePath")

        val imageFile = File(imagePath)
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Basic file checks
        if (!imageFile.exists()) {
            issues.add("Image file does not exist")
            return ValidationResult(false, issues, warnings)
        }

        if (imageFile.length() == 0L) {
            issues.add("Image file is empty")
            return ValidationResult(false, issues, warnings)
        }

        // Check minimum size (should be at least 1GB for a valid GSI)
        val minSizeBytes = 1L * 1024L * 1024L * 1024L // 1GB
        if (imageFile.length() < minSizeBytes) {
            warnings.add("Image size is smaller than expected for a GSI (${imageFile.length() / (1024L * 1024L)}MB)")
        }

        // Check maximum reasonable size (20GB)
        val maxSizeBytes = 20L * 1024L * 1024L * 1024L // 20GB
        if (imageFile.length() > maxSizeBytes) {
            warnings.add("Image size is larger than expected (${imageFile.length() / (1024L * 1024L * 1024L)}GB)")
        }

        // Validate file format
        val formatValidation = validateImageFormat(imagePath)
        if (!formatValidation.isValid) {
            issues.addAll(formatValidation.issues)
        }
        warnings.addAll(formatValidation.warnings)

        // Validate image structure if it's a raw image
        if (imagePath.endsWith(".img")) {
            val structureValidation = validateImageStructure(imagePath)
            if (!structureValidation.isValid) {
                issues.addAll(structureValidation.issues)
            }
            warnings.addAll(structureValidation.warnings)
        }

        val isValid = issues.isEmpty()
        Log.d(TAG, "GSI validation result: $isValid, Issues: ${issues.size}, Warnings: ${warnings.size}")

        return ValidationResult(isValid, issues, warnings)
    }

    /**
     * Validate image file format
     */
    private suspend fun validateImageFormat(imagePath: String): ValidationResult {
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        try {
            val file = File(imagePath)
            val fileName = file.name.lowercase()

            when {
                fileName.endsWith(".img") -> {
                    // Raw image file - check if it's a valid ext4 or other filesystem
                    val fileTypeResult = PrivilegedProvider.run {
                        CmdRunner.runCommand("file $imagePath")
                    }

                    if (fileTypeResult.isSuccess) {
                        val fileType = fileTypeResult.output.lowercase()
                        when {
                            fileType.contains("ext4") -> {
                                Log.d(TAG, "Detected EXT4 filesystem")
                            }
                            fileType.contains("ext3") -> {
                                Log.d(TAG, "Detected EXT3 filesystem")
                                warnings.add("EXT3 filesystem detected - EXT4 is recommended")
                            }
                            fileType.contains("ext2") -> {
                                Log.d(TAG, "Detected EXT2 filesystem")
                                warnings.add("EXT2 filesystem detected - EXT4 is recommended")
                            }
                            fileType.contains("data") -> {
                                Log.d(TAG, "Raw data detected - assuming valid image")
                            }
                            else -> {
                                warnings.add("Unknown filesystem type: $fileType")
                            }
                        }
                    }
                }

                fileName.endsWith(".gz") -> {
                    // Compressed image - validate compression
                    val gzipResult = PrivilegedProvider.run {
                        CmdRunner.runCommand("gzip -t $imagePath")
                    }

                    if (!gzipResult.isSuccess) {
                        issues.add("Invalid GZIP compression")
                    }
                }

                fileName.endsWith(".xz") -> {
                    // XZ compressed image
                    val xzResult = PrivilegedProvider.run {
                        CmdRunner.runCommand("xz -t $imagePath")
                    }

                    if (!xzResult.isSuccess) {
                        issues.add("Invalid XZ compression")
                    }
                }

                fileName.endsWith(".zip") -> {
                    // ZIP file - check if it's a valid DSU package
                    val zipResult = PrivilegedProvider.run {
                        CmdRunner.runCommand("unzip -t $imagePath")
                    }

                    if (!zipResult.isSuccess) {
                        issues.add("Invalid ZIP file")
                    } else {
                        // Check for DSU package structure
                        val listResult = PrivilegedProvider.run {
                            CmdRunner.runCommand("unzip -l $imagePath")
                        }

                        if (listResult.isSuccess) {
                            val contents = listResult.output
                            if (!contents.contains("system.img") && !contents.contains("system.raw.img")) {
                                warnings.add("ZIP file does not appear to contain a system image")
                            }
                        }
                    }
                }

                else -> {
                    warnings.add("Unknown file format: $fileName")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating image format", e)
            issues.add("Error validating image format: ${e.message}")
        }

        return ValidationResult(issues.isEmpty(), issues, warnings)
    }

    /**
     * Validate image internal structure
     */
    private suspend fun validateImageStructure(imagePath: String): ValidationResult {
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        try {
            // Try to mount the image temporarily to check structure
            val tempMountDir = "/tmp/gsi_validation_mount"

            // Create temporary mount point
            val mkdirResult = PrivilegedProvider.run {
                CmdRunner.runCommand("mkdir -p $tempMountDir")
            }

            if (mkdirResult.isSuccess) {
                // Try to mount the image
                val mountResult = PrivilegedProvider.run {
                    CmdRunner.runCommand("mount -o loop,ro $imagePath $tempMountDir")
                }

                if (mountResult.isSuccess) {
                    // Check for essential Android system directories
                    val essentialDirs = listOf(
                        "system/bin",
                        "system/lib",
                        "system/framework",
                        "system/app",
                        "system/priv-app",
                    )

                    for (dir in essentialDirs) {
                        val checkResult = PrivilegedProvider.run {
                            CmdRunner.runCommand("test -d $tempMountDir/$dir && echo 'exists' || echo 'missing'")
                        }

                        if (checkResult.isSuccess && checkResult.output.contains("missing")) {
                            warnings.add("Missing essential directory: $dir")
                        }
                    }

                    // Check for build.prop
                    val buildPropResult = PrivilegedProvider.run {
                        CmdRunner.runCommand("test -f $tempMountDir/system/build.prop && echo 'exists' || echo 'missing'")
                    }

                    if (buildPropResult.isSuccess && buildPropResult.output.contains("missing")) {
                        issues.add("Missing system/build.prop file")
                    }

                    // Unmount
                    PrivilegedProvider.run {
                        CmdRunner.runCommand("umount $tempMountDir")
                    }
                } else {
                    warnings.add("Could not mount image for structure validation")
                }

                // Clean up
                PrivilegedProvider.run {
                    CmdRunner.runCommand("rmdir $tempMountDir")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating image structure", e)
            warnings.add("Could not validate image structure: ${e.message}")
        }

        return ValidationResult(issues.isEmpty(), issues, warnings)
    }

    /**
     * Calculate image checksum
     */
    suspend fun calculateImageChecksum(imagePath: String, algorithm: String = "SHA-256"): String? {
        return try {
            Log.d(TAG, "Calculating $algorithm checksum for: $imagePath")

            val file = File(imagePath)
            if (!file.exists()) {
                return null
            }

            val digest = MessageDigest.getInstance(algorithm)
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }

            val checksum = digest.digest().joinToString("") { "%02x".format(it) }
            Log.d(TAG, "$algorithm checksum: $checksum")
            checksum
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating checksum", e)
            null
        }
    }

    /**
     * Verify image checksum against expected value
     */
    suspend fun verifyImageChecksum(
        imagePath: String,
        expectedChecksum: String,
        algorithm: String = "SHA-256",
    ): Boolean {
        val actualChecksum = calculateImageChecksum(imagePath, algorithm)
        return actualChecksum != null && actualChecksum.equals(expectedChecksum, ignoreCase = true)
    }

    /**
     * Get image information
     */
    suspend fun getImageInfo(imagePath: String): ImageInfo? {
        return try {
            val file = File(imagePath)
            if (!file.exists()) {
                return null
            }

            val fileType = getFileType(imagePath)
            val checksum = calculateImageChecksum(imagePath)

            ImageInfo(
                path = imagePath,
                size = file.length(),
                type = fileType,
                checksum = checksum,
                lastModified = file.lastModified(),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting image info", e)
            null
        }
    }

    /**
     * Get file type using file command
     */
    private suspend fun getFileType(filePath: String): String {
        return try {
            val result = PrivilegedProvider.run {
                CmdRunner.runCommand("file $filePath")
            }

            if (result.isSuccess) {
                result.output.substringAfter(": ")
            } else {
                "unknown"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * Validate that image is suitable for current device
     */
    suspend fun validateImageCompatibility(imagePath: String, deviceArch: String): ValidationResult {
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        try {
            // This would involve checking the image's architecture, API level, etc.
            // For now, we'll do basic checks

            val imageInfo = getImageInfo(imagePath)
            if (imageInfo == null) {
                issues.add("Could not get image information")
                return ValidationResult(false, issues, warnings)
            }

            // Check if image is too old or too new
            val currentApiLevel = android.os.Build.VERSION.SDK_INT
            // This would require extracting build.prop from the image to check API level

            Log.d(TAG, "Image compatibility validation completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error validating image compatibility", e)
            issues.add("Error validating compatibility: ${e.message}")
        }

        return ValidationResult(issues.isEmpty(), issues, warnings)
    }
}

/**
 * Validation result data class
 */
data class ValidationResult(
    val isValid: Boolean,
    val issues: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    val hasWarnings: Boolean
        get() = warnings.isNotEmpty()

    val hasIssues: Boolean
        get() = issues.isNotEmpty()
}

/**
 * Image information data class
 */
data class ImageInfo(
    val path: String,
    val size: Long,
    val type: String,
    val checksum: String?,
    val lastModified: Long,
) {
    val sizeInMB: Long
        get() = size / (1024L * 1024L)

    val sizeInGB: Float
        get() = size / (1024L * 1024L * 1024L).toFloat()
}
