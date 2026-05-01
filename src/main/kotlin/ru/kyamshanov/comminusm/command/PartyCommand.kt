package ru.kyamshanov.comminusm.command

import net.kyori.adventure.text.Component
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import ru.kyamshanov.comminusm.config.PluginConfig
import ru.kyamshanov.comminusm.gui.AdminMenu
import ru.kyamshanov.comminusm.gui.PartyMenu
import ru.kyamshanov.comminusm.service.OrderService
import ru.kyamshanov.comminusm.service.WorkFrontService
import ru.kyamshanov.comminusm.service.WorkdaysService

class PartyCommand(
    private val config: PluginConfig,
    private val workdaysService: WorkdaysService?,
    private val orderService: OrderService?,
    private val workFrontService: WorkFrontService?
) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("\u042d\u0442\u0443 \u043a\u043e\u043c\u0430\u043d\u0434\u0443 \u043c\u043e\u0436\u0435\u0442 \u0438\u0441\u043f\u043e\u043b\u044c\u0437\u043e\u0432\u0430\u0442\u044c \u0442\u043e\u043b\u044c\u043a\u043e \u0438\u0433\u0440\u043e\u043a!")
            return true
        }

        if (args.isNotEmpty() && args[0].equals("admin", ignoreCase = true)) {
            if (!sender.hasPermission("comminusm.admin")) {
                sender.sendMessage(Component.text("\u00a7c\u041d\u0435\u0434\u043e\u0441\u0442\u0430\u0442\u043e\u0447\u043d\u043e \u043f\u0440\u0430\u0432, \u0442\u043e\u0432\u0430\u0440\u0438\u0449!"))
                return true
            }
            AdminMenu(config, orderService, workFrontService, workdaysService).open(sender)
            return true
        }

        PartyMenu(config, workdaysService, orderService, workFrontService).open(sender)
        return true
    }
}
