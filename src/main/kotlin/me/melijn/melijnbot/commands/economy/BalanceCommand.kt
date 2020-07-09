package me.melijn.melijnbot.commands.economy

import kotlinx.coroutines.future.await
import me.melijn.melijnbot.objects.command.AbstractCommand
import me.melijn.melijnbot.objects.command.CommandCategory
import me.melijn.melijnbot.objects.command.CommandContext
import me.melijn.melijnbot.objects.embed.Embedder
import me.melijn.melijnbot.objects.utils.retrieveUserByArgsNMessage
import me.melijn.melijnbot.objects.utils.sendEmbed
import me.melijn.melijnbot.objects.utils.withVariable

class BalanceCommand : AbstractCommand("command.balance") {

    init {
        id = 190
        name = "balance"
        aliases = arrayOf("bal", "money")
        commandCategory = CommandCategory.ECONOMY
    }

    override suspend fun execute(context: CommandContext) {
        if (context.args.isEmpty()) {
            val balance = context.daoManager.balanceWrapper.balanceCache[context.authorId].await()
            val description = context.getTranslation("$root.show.self")
                .withVariable("user", context.author.asTag)
                .withVariable("balance", balance)

            val eb = Embedder(context)
                .setDescription(description)
            sendEmbed(context, eb.build())

        } else {
            val user = retrieveUserByArgsNMessage(context, 0) ?: return
            val balance = context.daoManager.balanceWrapper.balanceCache[user.idLong].await()
            val description = context.getTranslation("$root.show.other")
                .withVariable("user", user.asTag)
                .withVariable("balance", balance)

            val eb = Embedder(context)
                .setDescription(description)
            sendEmbed(context, eb.build())
        }
    }
}