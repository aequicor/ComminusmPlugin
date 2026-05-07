package ru.kyamshanov.comminusm.command

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

/**
 * A command executor that delegates to multiple executors in sequence.
 * If the first executor handles the command, it returns true.
 * Otherwise, the second executor is called.
 *
 * Used to wrap existing command executors (like OrderCommand) with new functionality
 * (like commune info display) without modifying the original implementation.
 */
class DelegatingCommandExecutor(
    private val primary: CommandExecutor?,
    private val secondary: CommandExecutor
) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        // Try primary executor first
        if (primary != null && primary.onCommand(sender, command, label, args)) {
            return true
        }

        // Fall through to secondary
        return secondary.onCommand(sender, command, label, args)
    }
}
