package me.melijn.melijnbot.commands.music

import me.melijn.llklient.io.filters.Timescale
import me.melijn.melijnbot.internals.command.AbstractCommand
import me.melijn.melijnbot.internals.command.CommandCategory
import me.melijn.melijnbot.internals.command.ICommandContext
import me.melijn.melijnbot.internals.command.RunCondition
import me.melijn.melijnbot.internals.utils.getLongFromArgNMessage
import me.melijn.melijnbot.internals.utils.message.sendRsp
import me.melijn.melijnbot.internals.utils.withVariable

class PitchCommand : AbstractCommand("command.pitch") {

    init {
        id = 184
        name = "pitch"
        runConditions = arrayOf(RunCondition.GUILD_SUPPORTER)
        commandCategory = CommandCategory.MUSIC
    }

    override suspend fun execute(context: ICommandContext) {
        val iPlayer = context.getGuildMusicPlayer().guildTrackManager.iPlayer
        if (context.args.isEmpty()) {
            val currentPitchPercent = (iPlayer.filters.timescale?.pitch ?: 1.0f) * 100
            val msg = context.getTranslation("$root.show")
                .withVariable("pitch", currentPitchPercent)
            sendRsp(context, msg)
            return
        }

        val pitch = getLongFromArgNMessage(context, 0, 0, ignore = arrayOf("%")) ?: return
        val player = context.getGuildMusicPlayer().guildTrackManager.iPlayer

        val ts = player.filters.timescale ?: Timescale()
        ts.pitch = pitch / 100.0f
        player.filters.timescale = ts
        player.filters.commit()

        val msg = context.getTranslation("$root.set")
            .withVariable("pitch", pitch)
        sendRsp(context, msg)
        return
    }
}