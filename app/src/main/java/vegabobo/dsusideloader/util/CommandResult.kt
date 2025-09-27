package vegabobo.dsusideloader.util

/**
 * Simple wrapper for command execution results
 * Works with the existing CmdRunner utility
 */
data class CommandResult(
    val isSuccess: Boolean,
    val output: String,
    val exitCode: Int = if (isSuccess) 0 else 1,
) {
    companion object {
        fun success(output: String): CommandResult = CommandResult(true, output, 0)
        fun failure(output: String, exitCode: Int = 1): CommandResult = CommandResult(false, output, exitCode)

        /**
         * Execute a command using CmdRunner and return a CommandResult
         */
        fun execute(command: String): CommandResult {
            return try {
                val output = CmdRunner.run(command)
                success(output)
            } catch (e: Exception) {
                failure("Command failed: ${e.message}")
            }
        }
    }
}
