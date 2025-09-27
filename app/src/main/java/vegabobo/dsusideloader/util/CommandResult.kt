package vegabobo.dsusideloader.util

/**
 * Result of a command execution
 */
data class CommandResult(
    val isSuccess: Boolean,
    val output: String,
    val exitCode: Int = 0,
)

/**
 * Enhanced CmdRunner with result objects
 */
object EnhancedCmdRunner {

    /**
     * Run command and return structured result
     */
    fun runCommand(cmd: String): CommandResult {
        return try {
            val output = CmdRunner.run(cmd)
            CommandResult(
                isSuccess = true,
                output = output,
                exitCode = 0,
            )
        } catch (e: Exception) {
            CommandResult(
                isSuccess = false,
                output = e.message ?: "Command failed",
                exitCode = -1,
            )
        }
    }

    /**
     * Run command with callback for each line
     */
    fun runCommandWithCallback(cmd: String, onReceive: (String) -> Unit): CommandResult {
        return try {
            val outputLines = mutableListOf<String>()
            CmdRunner.runReadEachLine(cmd) { line ->
                outputLines.add(line)
                onReceive(line)
            }
            CommandResult(
                isSuccess = true,
                output = outputLines.joinToString("\n"),
                exitCode = 0,
            )
        } catch (e: Exception) {
            CommandResult(
                isSuccess = false,
                output = e.message ?: "Command failed",
                exitCode = -1,
            )
        }
    }
}
