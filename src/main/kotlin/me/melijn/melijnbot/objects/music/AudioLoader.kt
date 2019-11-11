package me.melijn.melijnbot.objects.music

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.wrapper.spotify.model_objects.specification.ArtistSimplified
import com.wrapper.spotify.model_objects.specification.PlaylistTrack
import com.wrapper.spotify.model_objects.specification.Track
import com.wrapper.spotify.model_objects.specification.TrackSimplified
import kotlinx.coroutines.runBlocking
import me.melijn.melijnbot.objects.command.CommandContext
import me.melijn.melijnbot.objects.embed.Embedder
import me.melijn.melijnbot.objects.translation.PLACEHOLDER_USER
import me.melijn.melijnbot.objects.translation.SC_SELECTOR
import me.melijn.melijnbot.objects.translation.YT_SELECTOR
import me.melijn.melijnbot.objects.translation.YT_VID_URL_BASE
import me.melijn.melijnbot.objects.utils.*
import net.dv8tion.jda.api.entities.Message
import java.lang.Integer.min

const val QUEUE_LIMIT = 150
const val DONATE_QUEUE_LIMIT = 1000

class AudioLoader(private val musicPlayerManager: MusicPlayerManager) {

    val root = "message.music"
    private val audioPlayerManager = musicPlayerManager.audioPlayerManager
    private val ytSearch = YTSearch()
    private val spotifyTrackDiff = 2000


//    fun audioLoadResultHandler(
//        failed: (FriendlyException) -> Unit,
//        loaded: (AudioTrack) -> Unit,
//        noMatches: () -> Unit,
//        playListLoaded: (AudioPlaylist) -> Unit
//    ) = object : AudioLoadResultHandler {
//        override fun loadFailed(exception: FriendlyException) = failed(exception)
//        override fun trackLoaded(track: AudioTrack) = loaded(track)
//        override fun noMatches() = noMatches()
//        override fun playlistLoaded(playlist: AudioPlaylist) = playListLoaded(playlist)
//    }

    fun loadNewTrackNMessage(context: CommandContext, source: String, isPlaylist: Boolean = false) {
        val guild = context.guild
        val guildMusicPlayer = musicPlayerManager.getGuildMusicPlayer(guild)
        val rawInput = source
            .replace(YT_SELECTOR, "")
            .replace(SC_SELECTOR, "")

        if (guildMusicPlayer.queueIsFull(context, 1)) return
        val resultHandler = object : AudioLoadResultHandler {
            override fun loadFailed(exception: FriendlyException) {
                sendMessageLoadFailed(context, exception)
            }

            override fun trackLoaded(track: AudioTrack) {
                track.userData = TrackUserData(context.author)
                if (guildMusicPlayer.safeQueue(context, track)) {
                    sendMessageAddedTrack(context, track)
                }
            }

            override fun noMatches() {
                sendMessageNoMatches(context, rawInput)
            }

            override fun playlistLoaded(playlist: AudioPlaylist) {
                val tracks = playlist.tracks
                if (isPlaylist) {
                    var notAdded = 0

                    for (track in tracks) {
                        track.userData = TrackUserData(context.author)

                        if (!guildMusicPlayer.safeQueueSilent(context.daoManager, track)) notAdded++
                    }
                    sendMessageAddedTracks(context, tracks.subList(0, tracks.size - 1 - notAdded))
                } else {
                    val track = tracks[0]
                    track.userData = TrackUserData(context.author)
                    guildMusicPlayer.safeQueue(context, track)
                    sendMessageAddedTrack(context, track)
                }
            }
        }

        if (source.startsWith(YT_SELECTOR)) {
            ytSearch.search(rawInput) { videoId ->
                if (videoId == null) {
                    sendMessageNoMatches(context, rawInput)
                } else {
                    audioPlayerManager.loadItemOrdered(guildMusicPlayer, YT_VID_URL_BASE + videoId, resultHandler)
                }
            }
        } else {
            audioPlayerManager.loadItemOrdered(guildMusicPlayer, source, resultHandler)
        }
    }


    private fun sendMessageLoadFailed(context: CommandContext, exception: FriendlyException) = runBlocking {
        val msg = context.getTranslation("$root.loadfailed")
            .replace("%cause%", exception.message ?: "/")
        sendMsg(context, msg)
    }

    fun sendMessageNoMatches(context: CommandContext, input: String) = runBlocking {
        val msg = context.getTranslation("$root.nomatches")
            .replace("%source%", input)
        sendMsg(context, msg)
    }


    fun sendMessageAddedTrack(context: CommandContext, audioTrack: AudioTrack) = runBlocking {
        val title = context.getTranslation("$root.addedtrack.title")
            .replace(PLACEHOLDER_USER, context.author.asTag)
        val description = context.getTranslation("$root.addedtrack.description")
            .replace("%position%", getQueuePosition(context, audioTrack).toString())
            .replace("%title%", audioTrack.info.title)
            .replace("%duration%", getDurationString(audioTrack.duration))
            .replace("%url%", audioTrack.info.uri)

        val eb = Embedder(context)
        eb.setTitle(title)
        eb.setDescription(description)

        sendEmbed(context, eb.build())
    }

    fun sendMessageAddedTracks(context: CommandContext, audioTracks: List<AudioTrack>) = runBlocking {
        val title = context.getTranslation("$root.addedtracks.title")
            .replace(PLACEHOLDER_USER, context.author.asTag)
        val description = context.getTranslation("$root.addedtracks.description")
            .replace("%size%", audioTracks.size.toString())
            .replace("%positionFirst%", getQueuePosition(context, audioTracks[0]).toString())
            .replace("%positionLast%", (getQueuePosition(context, audioTracks[0]) + audioTracks.size).toString())

        val eb = Embedder(context)
        eb.setTitle(title)
        eb.setDescription(description)

        sendEmbed(context, eb.build())
    }

    private fun getQueuePosition(context: CommandContext, audioTrack: AudioTrack): Int =
        context.musicPlayerManager.getGuildMusicPlayer(context.guild).guildTrackManager.getPosition(audioTrack)

    fun loadSpotifyTrack(
        context: CommandContext,
        query: String,
        artists: Array<ArtistSimplified>?,
        durationMs: Int,
        silent: Boolean = false,
        loaded: ((Boolean) -> Unit)? = null
    ) {
        val player: GuildMusicPlayer = context.guildMusicPlayer
        val title: String = query.replaceFirst("$SC_SELECTOR|$YT_SELECTOR".toRegex(), "")
        val source = StringBuilder(query)
        val artistNames = mutableListOf<String>()
        if (player.queueIsFull(context, 1, silent)) {
            loaded?.invoke(false)
            return
        }
        appendArtists(artists, source, artistNames)

        audioPlayerManager.loadItemOrdered(player, source.toString(), object : AudioLoadResultHandler {
            override fun trackLoaded(track: AudioTrack) {
                if ((durationMs + spotifyTrackDiff > track.duration && track.duration > durationMs - spotifyTrackDiff)
                    || track.info.title.contains(title, true)) {
                    if (player.safeQueue(context, track)) {
                        if (!silent) {
                            sendMessageAddedTrack(context, track)
                        }
                        loaded?.invoke(true)
                    } else {
                        loaded?.invoke(false)
                    }
                } else {
                    loadSpotifyTrackOther(context, query, artists, durationMs, title, silent, loaded)
                }
            }

            override fun playlistLoaded(playlist: AudioPlaylist) {
                val tracks: List<AudioTrack> = playlist.tracks
                for (track in tracks.subList(0, min(tracks.size, 5))) {
                    if ((durationMs + spotifyTrackDiff > track.duration && track.duration > durationMs - spotifyTrackDiff)
                        || track.info.title.contains(title, true)) {
                        if (player.safeQueue(context, track)) {
                            if (!silent) {
                                sendMessageAddedTrack(context, track)
                            }
                            loaded?.invoke(true)
                        } else {
                            loaded?.invoke(false)
                        }
                        return
                    }
                }
                loadSpotifyTrackOther(context, query, artists, durationMs, title, silent, loaded)
            }

            override fun noMatches() {
                loadSpotifyTrackOther(context, query, artists, durationMs, title, silent, loaded)
            }

            override fun loadFailed(exception: FriendlyException) {
                if (!silent) {
                    sendMessageLoadFailed(context, exception)
                }
                loaded?.invoke(false)
            }
        })
    }

    private fun loadSpotifyTrackOther(
        context: CommandContext,
        query: String,
        artists: Array<ArtistSimplified>?,
        durationMs: Int,
        title: String,
        silent: Boolean = false,
        loaded: ((Boolean) -> Unit)? = null
    ) {
        if (query.startsWith(YT_SELECTOR)) {
            if (artists != null) {
                loadSpotifyTrack(context, query, null, durationMs, silent, loaded)
            } else {
                val newQuery = query.replaceFirst(YT_SELECTOR, SC_SELECTOR)
                loadSpotifyTrack(context, newQuery, artists, durationMs, silent, loaded)
            }
        } else if (query.startsWith(SC_SELECTOR)) {
            if (artists != null) {
                loadSpotifyTrack(context, query, null, durationMs, silent, loaded)
            } else {
                if (!silent) sendMessageNoMatches(context, title)
                loaded?.invoke(false)
            }
        }
    }

    private fun appendArtists(artists: Array<ArtistSimplified>?, source: StringBuilder, artistNames: MutableList<String>) {
        if (artists != null) {
            if (artists.isNotEmpty()) source.append(" ")
            val artistString = artists.joinToString(", ", transform = { artist ->
                artistNames.add(artist.name)
                artist.name
            })
            source.append(artistString)
        }
    }

    fun loadSpotifyPlaylist(context: CommandContext, tracks: Array<PlaylistTrack>) = runBlocking {
        if (tracks.size + context.guildMusicPlayer.guildTrackManager.tracks.size > QUEUE_LIMIT) {
            val msg = context.getTranslation("$root.queuelimit")
                .replace("%amount%", QUEUE_LIMIT.toString())
            sendMsg(context, msg)
            return@runBlocking
        }

        val loadedTracks = mutableListOf<Track>()
        val failedTracks = mutableListOf<Track>()
        val msg = context.getTranslation("command.play.loadingtrack" + if (tracks.size > 1) "s" else "")
            .replace("%trackCount%", tracks.size.toString())

        val message = sendMsg(context, msg)
        for (track in tracks.map { playListTrack -> playListTrack.track }) {
            loadSpotifyTrack(context, YT_SELECTOR + track.name, track.artists, track.durationMs, true) {
                if (it) {
                    loadedTracks.add(track)
                } else {
                    failedTracks.add(track)
                }
                println(loadedTracks.size + failedTracks.size)
                if (loadedTracks.size + failedTracks.size == tracks.size) {
                    runBlocking {
                        val newMsg = context.getTranslation("command.play.loadedtrack" + if (tracks.size > 1) "s" else "")
                            .replace("%loadedCount%", loadedTracks.size.toString())
                            .replace("%failedCount%", failedTracks.size.toString())
                        message[0].editMessage(newMsg).await()
                    }
                }
            }
        }
    }

    fun loadSpotifyAlbum(context: CommandContext, simpleTracks: Array<TrackSimplified>) = runBlocking {
        if (simpleTracks.size + context.guildMusicPlayer.guildTrackManager.tracks.size > QUEUE_LIMIT) {
            val msg = context.getTranslation("$root.queuelimit")
                .replace("%amount%", QUEUE_LIMIT.toString())
                .replace("%donateAmount%", DONATE_QUEUE_LIMIT.toString())
            sendMsg(context, msg)
            return@runBlocking
        }

        val loadedTracks = mutableListOf<TrackSimplified>()
        val failedTracks = mutableListOf<TrackSimplified>()
        val msg = context.getTranslation("command.play.loadingtrack" + if (simpleTracks.size > 1) "s" else "")
            .replace("%trackCount%", simpleTracks.size.toString())
        val message = sendMsg(context, msg)
        for (track in simpleTracks) {
            loadSpotifyTrack(context, YT_SELECTOR + track.name, track.artists, track.durationMs, true) {
                if (it) {
                    loadedTracks.add(track)
                } else {
                    failedTracks.add(track)
                }
                println(loadedTracks.size + failedTracks.size)
                if (loadedTracks.size + failedTracks.size == simpleTracks.size) {
                    runBlocking {
                        val newMsg = context.getTranslation("command.play.loadedtrack" + if (simpleTracks.size > 1) "s" else "")
                            .replace("%loadedCount%", loadedTracks.size.toString())
                            .replace("%failedCount%", failedTracks.size.toString())
                        message[0].editMessage(newMsg).await()
                    }
                }
            }
        }
    }

    fun loadNewTrackPickerNMessage(context: CommandContext, query: String) {
        val guildMusicPlayer = context.guildMusicPlayer
        val rawInput = query
            .replace(YT_SELECTOR, "")
            .replace(SC_SELECTOR, "")

        if (guildMusicPlayer.queueIsFull(context, 1)) return
        val resultHandler = object : AudioLoadResultHandler {
            override fun loadFailed(exception: FriendlyException) {
                sendMessageLoadFailed(context, exception)
            }

            override fun trackLoaded(track: AudioTrack) {
                prepareSearchMenu(context, listOf(track))
            }

            override fun noMatches() {
                sendMessageNoMatches(context, rawInput)
            }

            override fun playlistLoaded(playlist: AudioPlaylist) {
                prepareSearchMenu(context, playlist.tracks)
            }
        }

        audioPlayerManager.loadItemOrdered(guildMusicPlayer, query, resultHandler)
    }

    private fun prepareSearchMenu(context: CommandContext, trackList: List<AudioTrack>) {
        val guildMusicPlayer = context.guildMusicPlayer
        if (guildMusicPlayer.queueIsFull(context, 1)) return

        val tracks = trackList.filterIndexed { index, _ -> index < 5 }.toMutableList()

        for ((index, track) in tracks.withIndex()) {
            track.userData = TrackUserData(context.author)
            tracks[index] = track
        }

        context.taskManager.async {
            val msg = sendMessageSearchMenu(context, tracks).last()
            guildMusicPlayer.searchMenus[msg.idLong] = tracks

            if (tracks.size > 0) msg.addReaction("\u0031\u20E3").queue()
            if (tracks.size > 1) msg.addReaction("\u0032\u20E3").queue()
            if (tracks.size > 2) msg.addReaction("\u0033\u20E3").queue()
            if (tracks.size > 3) msg.addReaction("\u0034\u20E3").queue()
            if (tracks.size > 4) msg.addReaction("\u0035\u20E3").queue()
            if (tracks.size > 5) msg.addReaction("\u0036\u20E3").queue()
            msg.addReaction("\u274C").queue()
        }
    }

    private suspend fun sendMessageSearchMenu(context: CommandContext, tracks: List<AudioTrack>): List<Message> {
        val title = context.getTranslation("$root.searchmenu")
        var menu = ""
        for ((index, track) in tracks.withIndex()) {
            menu += "\n[${index + 1}](${track.info.uri}) - ${track.info.title} `[${getDurationString(track.duration)}]`"
        }
        val eb = Embedder(context)
        eb.setTitle(title)
        eb.setDescription(menu)
        return sendEmbed(context, eb.build())
    }
}