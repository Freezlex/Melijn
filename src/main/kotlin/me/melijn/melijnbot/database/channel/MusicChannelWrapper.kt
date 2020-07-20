package me.melijn.melijnbot.database.channel

import com.google.common.cache.CacheBuilder
import me.melijn.melijnbot.database.IMPORTANT_CACHE
import me.melijn.melijnbot.internals.threading.TaskManager
import me.melijn.melijnbot.internals.utils.loadingCacheFrom
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class MusicChannelWrapper(val musicChannelDao: MusicChannelDao) {

    val musicChannelCache = CacheBuilder.newBuilder()
        .expireAfterAccess(IMPORTANT_CACHE, TimeUnit.MINUTES)
        .build(loadingCacheFrom<Long, Long> { guildId ->
            getChannelId(guildId)
        })

    private fun getChannelId(guildId: Long): CompletableFuture<Long> {
        val future = CompletableFuture<Long>()

       TaskManager.async {
            val channelId = musicChannelDao.get(guildId)
            future.complete(channelId)
        }

        return future
    }

    suspend fun removeChannel(guildId: Long) {
        musicChannelDao.remove(guildId)
        musicChannelCache.put(guildId, CompletableFuture.completedFuture(-1))
    }

    suspend fun setChannel(guildId: Long, channelId: Long) {
        musicChannelDao.set(guildId, channelId)
        musicChannelCache.put(guildId, CompletableFuture.completedFuture(channelId))
    }
}