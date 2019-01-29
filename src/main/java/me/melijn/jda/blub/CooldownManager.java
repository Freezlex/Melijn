package me.melijn.jda.blub;

import me.melijn.jda.db.Variables;

import java.util.IdentityHashMap;
import java.util.Map;

public class CooldownManager {

    private final Variables variables;
    public CooldownManager(Variables variables) {
        this.variables = variables;
    }
    private final Map<Long, Map<Long, Map<Integer, Long>>> cooldowns = new IdentityHashMap<>();// Guild -> User -> command -> time used

    private void checkOldEntries() {
        long currentTime = System.currentTimeMillis();
        new IdentityHashMap<>(cooldowns).forEach((guildId, users) -> users.forEach((userId, commands) -> commands.forEach((commandId, time) -> {
            if (time > currentTime) return;
            Map<Integer, Long> freshCommands = cooldowns.get(guildId).get(userId);
            freshCommands.remove(commandId);
            if (freshCommands.isEmpty()) {
                Map<Long, Map<Integer, Long>> freshUsers = cooldowns.get(guildId);
                freshUsers.remove(userId);
                if (freshUsers.isEmpty()) {
                    cooldowns.remove(guildId);
                } else {
                    cooldowns.put(guildId, freshUsers);
                }
            } else {
                Map<Long, Map<Integer, Long>> freshUsers = cooldowns.get(guildId);
                freshUsers.put(userId, freshCommands);
                cooldowns.put(guildId, freshUsers);
            }
        })));
    }

    public void updateCooldown(long guildId, long userId, int commandId) {
        checkOldEntries();
        if (!variables.cooldowns.getUnchecked(guildId).containsKey(commandId)) return;
        Map<Long, Map<Integer, Long>> users = cooldowns.containsKey(guildId) ? cooldowns.get(guildId) : new IdentityHashMap<>();
        if (!users.containsKey(userId)) users.put(userId, new IdentityHashMap<>());
        Map<Integer, Long> commands = users.get(userId);
        commands.put(commandId, System.currentTimeMillis() + variables.cooldowns.getUnchecked(guildId).get(commandId));
        users.put(userId, commands);
        cooldowns.put(guildId, users);
    }

    public boolean isActive(long guildId, long userId, int commandId) {
        long currentTime = System.currentTimeMillis();
        return (cooldowns.containsKey(guildId) &&
                cooldowns.get(guildId).containsKey(userId) &&
                cooldowns.get(guildId).get(userId).containsKey(commandId) &&
                cooldowns.get(guildId).get(userId).get(commandId) > currentTime);
    }

    public long getTimeLeft(long guildId, long userId, int commandId) {
        return (cooldowns.containsKey(guildId) &&
                cooldowns.get(guildId).containsKey(userId) &&
                cooldowns.get(guildId).get(userId).containsKey(commandId)) ?
                cooldowns.get(guildId).get(userId).get(commandId) - System.currentTimeMillis() :
                0;
    }
}