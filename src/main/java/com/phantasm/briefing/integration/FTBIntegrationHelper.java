package com.phantasm.briefing.integration;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class FTBIntegrationHelper {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final FtbCompletionTracker COMPLETION_CACHE = new FtbCompletionTracker();
    private static volatile BiConsumer<ServerPlayer, String> completionListener;
    private static final String FTB_QUESTS_MOD_ID = "ftbquests";
    private static volatile ReflectionAccess reflectionAccess;
    private static volatile boolean reflectionAccessResolved;
    private static volatile boolean reflectionAccessUnavailableLogged;

    private FTBIntegrationHelper() {
    }

    public static void registerQuestCompletionListener(BiConsumer<ServerPlayer, String> listener) {
        completionListener = listener;
        if (isFTBQuestsLoaded()) {
            FTBQuestEventBridge.register();
        }
    }

    public static void clearCompletionCache(ServerPlayer player) {
        if (player == null) {
            return;
        }
        COMPLETION_CACHE.clear(player.getUUID());
    }

    public static void onQuestCompleted(ServerPlayer player, String questId) {
        if (player == null) {
            return;
        }
        String normalizedQuestId = FtbCompletionTracker.normalizeQuestId(questId);
        if (normalizedQuestId.isBlank()) {
            return;
        }

        if (!COMPLETION_CACHE.markCompleted(player.getUUID(), normalizedQuestId)) {
            return;
        }
        BiConsumer<ServerPlayer, String> listener = completionListener;
        if (listener != null) {
            listener.accept(player, normalizedQuestId);
        }
    }

    public static void onQuestStarted(ServerPlayer player, String questId) {
        if (player != null) {
            COMPLETION_CACHE.markIncomplete(player.getUUID(), questId);
        }
    }

    public static boolean sameQuestId(String firstQuestId, String secondQuestId) {
        return FtbCompletionTracker.sameQuestId(firstQuestId, secondQuestId);
    }

    public static boolean isQuestCompletedCached(ServerPlayer player, String questId) {
        return isFTBQuestsLoaded()
                && player != null
                && COMPLETION_CACHE.isCompleted(player.getUUID(), questId);
    }

    public static void refreshCompletionCache(ServerPlayer player, Collection<String> questIds) {
        if (player == null) {
            return;
        }
        if (!isFTBQuestsLoaded() || questIds == null || questIds.isEmpty()) {
            COMPLETION_CACHE.clear(player.getUUID());
            return;
        }

        List<String> completedQuestIds = new ArrayList<>();
        for (String questId : questIds) {
            if (hasCompletedQuest(player, questId)) {
                completedQuestIds.add(questId);
            }
        }
        COMPLETION_CACHE.replaceCompleted(player.getUUID(), completedQuestIds);
    }

    public static boolean isFTBQuestsLoaded() {
        return ModList.get().isLoaded(FTB_QUESTS_MOD_ID);
    }


    public static boolean isBridgeAvailable() {
        return !isFTBQuestsLoaded() || getReflectionAccess().isPresent();
    }

    public static boolean hasCompletedQuest(ServerPlayer player, String questId) {
        if (!isFTBQuestsLoaded() || player == null || questId == null || questId.isBlank()) {
            return false;
        }

        boolean completed = getReflectionAccess().map(access -> {
            try {
                Object questFile = access.getServerQuestFile();
                if (questFile == null) {
                    return false;
                }
                Object teamData = access.getTeamData(questFile, player).orElse(null);
                if (teamData == null) {
                    return false;
                }

                ResolvedQuest quest = access.resolveQuest(questFile, questId);
                return quest != null
                        && (access.isCompleted(teamData, quest.quest())
                        || access.isCompletedRaw(teamData, quest.quest()));
            } catch (ReflectiveOperationException | RuntimeException exception) {
                LOGGER.warn(
                        "[PhantasmBriefing] Failed to query FTB quest '{}' for player {}: {}",
                        questId,
                        player.getGameProfile().getName(),
                        exception.getMessage()
                );
                return false;
            }
        }).orElse(false);
        if (completed) {
            COMPLETION_CACHE.markCompleted(player.getUUID(), questId);
        }
        return completed;
    }

    public static List<FtbQuestInfo> listAvailableQuests() {
        if (!isFTBQuestsLoaded()) {
            return List.of();
        }

        return getReflectionAccess().map(access -> {
            try {
                Object questFile = access.getServerQuestFile();
                return questFile == null ? List.<FtbQuestInfo>of() : access.listQuests(questFile);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                LOGGER.warn("[PhantasmBriefing] Failed to enumerate FTB quests: {}", exception.getMessage());
                return List.<FtbQuestInfo>of();
            }
        }).orElse(List.of());
    }

    public static void completeQuestSilently(ServerPlayer player, String questId) {
        if (!isFTBQuestsLoaded() || player == null || questId == null || questId.isBlank()) {
            return;
        }

        getReflectionAccess().ifPresent(access -> {
            try {
                Object questFile = access.getServerQuestFile();
                if (questFile == null) {
                    return;
                }
                Object teamData = access.getTeamData(questFile, player).orElse(null);
                if (teamData == null) {
                    return;
                }

                ResolvedQuest quest = access.resolveQuest(questFile, questId);
                if (quest == null || access.isCompleted(teamData, quest.quest())) {
                    return;
                }

                if (!access.areDependenciesComplete(teamData, quest.quest())) {
                    LOGGER.warn(
                            "[PhantasmBriefing] FTB quest '{}' was not synchronized for player {} because its prerequisites are not complete",
                            questId,
                            player.getGameProfile().getName()
                    );
                    return;
                }

                Collection<?> tasks = access.getTasks(quest.quest());
                if (tasks.isEmpty()) {
                    LOGGER.warn(
                            "[PhantasmBriefing] FTB quest '{}' has no tasks; refusing to mark the quest completed directly so FTB reward semantics are preserved",
                            questId
                    );
                    return;
                }

                for (Object task : tasks) {
                    if (!access.isOptionalForProgression(teamData, task) && !access.isCompleted(teamData, task)) {
                        access.completeTask(teamData, task);
                    }
                }

                if (!access.isCompleted(teamData, quest.quest())) {
                    LOGGER.warn(
                            "[PhantasmBriefing] FTB quest '{}' did not complete after its current tasks were progressed; FTB dependencies or progression rules still block completion",
                            questId
                    );
                }
            } catch (ReflectiveOperationException | RuntimeException exception) {
                LOGGER.warn(
                        "[PhantasmBriefing] Failed to progress FTB quest '{}' for player {}: {}",
                        questId,
                        player.getGameProfile().getName(),
                        exception.getMessage()
                );
            }
        });
    }

    private static Optional<ReflectionAccess> getReflectionAccess() {
        if (!isFTBQuestsLoaded()) {
            return Optional.empty();
        }

        ReflectionAccess existing = reflectionAccess;
        if (existing != null) {
            return Optional.of(existing);
        }
        if (reflectionAccessResolved) {
            return Optional.empty();
        }

        synchronized (FTBIntegrationHelper.class) {
            if (reflectionAccess != null) {
                return Optional.of(reflectionAccess);
            }
            if (reflectionAccessResolved) {
                return Optional.empty();
            }

            try {
                reflectionAccess = new ReflectionAccess();
                return Optional.of(reflectionAccess);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                if (!reflectionAccessUnavailableLogged) {
                    reflectionAccessUnavailableLogged = true;
                    LOGGER.warn(
                            "[PhantasmBriefing] FTB reflection bridge is unavailable; FTB integration will fail safe until a compatible API path is found: {}",
                            exception.getMessage()
                    );
                }
                return Optional.empty();
            } finally {
                reflectionAccessResolved = true;
            }
        }
    }

    public record FtbQuestInfo(String id, String title) {
    }

    private record ResolvedQuest(long id, Object quest) {
    }

    private static final class ReflectionAccess {
        private final Class<?> playerClass;
        private final Class<?> questObjectClass;
        private final Class<?> questObjectBaseClass;
        private final Class<?> taskClass;
        private final Method apiMethod;
        private final Method getQuestFileMethod;
        private final Method staticTeamDataMethod;
        private final Method questFileTeamDataMethod;
        private final Method getIdMethod;
        private final Method getQuestMethod;
        private final Method parseCodeStringMethod;
        private final Method isCompletedMethod;
        private final Method isCompletedRawMethod;
        private final Method areDependenciesCompleteMethod;
        private final Method getTasksMethod;
        private final Method getMaxProgressMethod;
        private final Method isOptionalForProgressionMethod;
        private final Method setProgressMethod;
        private final Method forAllQuestsMethod;
        private final Method getCodeStringMethod;
        private final Method getRawTitleMethod;

        private ReflectionAccess() throws ReflectiveOperationException {
            Class<?> ftbApiClass = Class.forName("dev.ftb.mods.ftbquests.api.FTBQuestsAPI");
            Class<?> apiInterface = Class.forName("dev.ftb.mods.ftbquests.api.FTBQuestsAPI$API");
            Class<?> questFileClass = Class.forName("dev.ftb.mods.ftbquests.api.QuestFile");
            Class<?> baseQuestFileClass = Class.forName("dev.ftb.mods.ftbquests.quest.BaseQuestFile");
            Class<?> questClass = Class.forName("dev.ftb.mods.ftbquests.quest.Quest");
            Class<?> teamDataClass = Class.forName("dev.ftb.mods.ftbquests.quest.TeamData");
            this.questObjectBaseClass = Class.forName("dev.ftb.mods.ftbquests.quest.QuestObjectBase");
            this.playerClass = Class.forName("net.minecraft.world.entity.player.Player");
            Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
            this.questObjectClass = Class.forName("dev.ftb.mods.ftbquests.quest.QuestObject");
            this.taskClass = Class.forName("dev.ftb.mods.ftbquests.quest.task.Task");

            this.apiMethod = ftbApiClass.getMethod("api");
            this.getQuestFileMethod = apiInterface.getMethod("getQuestFile", boolean.class);
            this.staticTeamDataMethod = findMethod(teamDataClass, "get", this.playerClass);
            this.questFileTeamDataMethod = findQuestFileTeamDataMethod(questFileClass, this.playerClass, entityClass);
            if (this.staticTeamDataMethod == null && this.questFileTeamDataMethod == null) {
                throw new NoSuchMethodException("No compatible FTB Quests team-data accessor found");
            }

            this.getIdMethod = findMethod(baseQuestFileClass, "getID", Object.class);
            this.getQuestMethod = baseQuestFileClass.getMethod("getQuest", long.class);
            this.parseCodeStringMethod = findMethod(this.questObjectBaseClass, "parseCodeString", String.class);
            this.isCompletedMethod = teamDataClass.getMethod("isCompleted", this.questObjectClass);
            this.isCompletedRawMethod = this.questObjectClass.getMethod("isCompletedRaw", teamDataClass);
            this.areDependenciesCompleteMethod = teamDataClass.getMethod("areDependenciesComplete", questClass);
            this.getTasksMethod = questClass.getMethod("getTasks");
            this.getMaxProgressMethod = this.taskClass.getMethod("getMaxProgress");
            this.isOptionalForProgressionMethod = this.questObjectClass.getMethod("isOptionalForProgression", teamDataClass);
            this.setProgressMethod = teamDataClass.getMethod("setProgress", this.taskClass, long.class);
            this.forAllQuestsMethod = questFileClass.getMethod("forAllQuests", Consumer.class);
            this.getCodeStringMethod = this.questObjectBaseClass.getMethod("getCodeString");
            this.getRawTitleMethod = this.questObjectBaseClass.getMethod("getRawTitle");
        }

        private Object getServerQuestFile() throws ReflectiveOperationException {
            Object api = this.apiMethod.invoke(null);
            return this.getQuestFileMethod.invoke(api, false);
        }

        private Optional<?> getTeamData(Object questFile, ServerPlayer player) throws ReflectiveOperationException {
            Object result = this.staticTeamDataMethod != null
                    ? this.staticTeamDataMethod.invoke(null, player)
                    : this.questFileTeamDataMethod.invoke(questFile, player);
            if (result instanceof Optional<?> optional) {
                return optional;
            }
            return Optional.ofNullable(result);
        }

        private ResolvedQuest resolveQuest(Object questFile, String questId) throws ReflectiveOperationException {
            long longId = this.resolveQuestId(questFile, questId);
            if (longId > 0L) {
                Object quest = this.getQuestMethod.invoke(questFile, longId);
                if (quest != null) {
                    return new ResolvedQuest(longId, quest);
                }
            }

            Object fallbackQuest = this.findQuestByCodeString(questFile, questId);
            if (fallbackQuest == null) {
                return null;
            }
            String codeString = String.valueOf(this.getCodeStringMethod.invoke(fallbackQuest));
            long fallbackId = this.resolveQuestId(questFile, codeString);
            return fallbackId > 0L ? new ResolvedQuest(fallbackId, fallbackQuest) : null;
        }

        private Object findQuestByCodeString(Object questFile, String questId) throws ReflectiveOperationException {
            String normalizedTarget = normalizeQuestCodeString(questId);
            if (normalizedTarget.isBlank()) {
                return null;
            }

            Object[] matched = new Object[1];
            Consumer<Object> consumer = quest -> {
                if (matched[0] != null) {
                    return;
                }
                try {
                    String codeString = String.valueOf(this.getCodeStringMethod.invoke(quest));
                    if (normalizedTarget.equals(normalizeQuestCodeString(codeString))) {
                        matched[0] = quest;
                    }
                } catch (ReflectiveOperationException | RuntimeException exception) {
                    throw new QuestEnumerationException(exception);
                }
            };

            try {
                this.forAllQuestsMethod.invoke(questFile, consumer);
            } catch (ReflectiveOperationException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof QuestEnumerationException enumerationException
                        && enumerationException.getCause() instanceof ReflectiveOperationException reflectiveException) {
                    throw reflectiveException;
                }
                throw exception;
            }
            return matched[0];
        }

        private static String normalizeQuestCodeString(String value) {
            String normalized = value == null ? "" : value.trim();
            if (normalized.startsWith("#")) {
                normalized = normalized.substring(1);
            }
            return normalized.toUpperCase(java.util.Locale.ROOT);
        }

        private long resolveQuestId(Object questFile, String questId) throws ReflectiveOperationException {
            if (this.parseCodeStringMethod != null) {
                Object parsedId = this.parseCodeStringMethod.invoke(null, questId);
                if (parsedId instanceof Long longId && longId > 0L) {
                    return longId;
                }
            }

            if (this.getIdMethod != null) {
                Object rawId = this.getIdMethod.invoke(questFile, questId);
                if (rawId instanceof Long longId && longId > 0L) {
                    return longId;
                }
            }

            return 0L;
        }

        private boolean isCompleted(Object teamData, Object questObject) throws ReflectiveOperationException {
            return (boolean) this.isCompletedMethod.invoke(teamData, questObject);
        }

        private boolean isCompletedRaw(Object teamData, Object questObject) throws ReflectiveOperationException {
            return (boolean) this.isCompletedRawMethod.invoke(questObject, teamData);
        }

        private boolean areDependenciesComplete(Object teamData, Object quest) throws ReflectiveOperationException {
            return (boolean) this.areDependenciesCompleteMethod.invoke(teamData, quest);
        }

        private Collection<?> getTasks(Object quest) throws ReflectiveOperationException {
            Object result = this.getTasksMethod.invoke(quest);
            return result instanceof Collection<?> collection ? collection : List.of();
        }

        private boolean isOptionalForProgression(Object teamData, Object task) throws ReflectiveOperationException {
            return (boolean) this.isOptionalForProgressionMethod.invoke(task, teamData);
        }

        private void completeTask(Object teamData, Object task) throws ReflectiveOperationException {
            long maxProgress = ((Number) this.getMaxProgressMethod.invoke(task)).longValue();
            this.setProgressMethod.invoke(teamData, task, maxProgress);
        }

        private List<FtbQuestInfo> listQuests(Object questFile) throws ReflectiveOperationException {
            List<FtbQuestInfo> result = new ArrayList<>();
            Consumer<Object> consumer = quest -> {
                try {
                    String id = String.valueOf(this.getCodeStringMethod.invoke(quest));
                    Object rawTitleValue = this.getRawTitleMethod.invoke(quest);
                    String rawTitle = rawTitleValue == null ? "" : String.valueOf(rawTitleValue);
                    String title = rawTitle.isBlank() ? id : rawTitle;
                    result.add(new FtbQuestInfo(id, title));
                } catch (ReflectiveOperationException | RuntimeException exception) {
                    throw new QuestEnumerationException(exception);
                }
            };
            try {
                this.forAllQuestsMethod.invoke(questFile, consumer);
            } catch (ReflectiveOperationException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof QuestEnumerationException enumerationException
                        && enumerationException.getCause() instanceof ReflectiveOperationException reflectiveException) {
                    throw reflectiveException;
                }
                throw exception;
            }
            result.sort(Comparator.comparing(FtbQuestInfo::title, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(FtbQuestInfo::id));
            return List.copyOf(result);
        }
    }

    private static final class QuestEnumerationException extends RuntimeException {
        private QuestEnumerationException(Throwable cause) {
            super(cause);
        }
    }

    private static Method findMethod(Class<?> owner, String methodName, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method findQuestFileTeamDataMethod(
            Class<?> questFileClass,
            Class<?> playerClass,
            Class<?> entityClass
    ) {
        Method method = findMethod(questFileClass, "getTeamData", playerClass);
        if (method != null) {
            return method;
        }
        method = findMethod(questFileClass, "getOrCreateTeamData", playerClass);
        return method != null ? method : findMethod(questFileClass, "getOrCreateTeamData", entityClass);
    }
}
