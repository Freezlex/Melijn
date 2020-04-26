package me.melijn.melijnbot.commands.administration

import me.melijn.melijnbot.enums.MessageType
import me.melijn.melijnbot.objects.command.AbstractCommand
import me.melijn.melijnbot.objects.command.CommandCategory
import me.melijn.melijnbot.objects.command.CommandContext
import me.melijn.melijnbot.objects.utils.sendSyntax

class KickMessageCommand : AbstractCommand("command.kickmessage") {

    init {
        id = 163
        name = "kickMessage"
        aliases = arrayOf("km")
        children = arrayOf(
            LeaveMessageCommand.SetContentArg(root, MessageType.KICKED),
            LeaveMessageCommand.EmbedArg(root, MessageType.KICKED),
            LeaveMessageCommand.AttachmentsArg(root, MessageType.KICKED),
            LeaveMessageCommand.ViewArg(root, MessageType.BANNED)
        )
        commandCategory = CommandCategory.ADMINISTRATION
    }

    override suspend fun execute(context: CommandContext) {
        sendSyntax(context)
    }
}