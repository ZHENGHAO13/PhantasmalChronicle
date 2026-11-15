package com.phantasm.briefing.integration;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

public final class FTBIntegrationHelper {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FTB_QUESTS_MOD_ID = "ftbquests";
    private static volatile ReflectionAccess reflectionAccess;
    private static volatile boolean reflectionAccessResolved;
    private static volatile boolean reflectionAccessUnavailableLogged;
    private static volatile boolean completionAccessUnavailableLogged;

    private FTBIntegrationHelper() {
    }

    public static boolean isFTBQuestsLoaded() {
        return ModList.get().isLoaded(FTB_QUESTS_MOD_ID);
    }

    public static boolean hasCompletedQuest(ServerPlayer player, String questId) {
        if (!isFTBQuestsLoaded() || questId == null || questId.isBlank()) {
            return false;
        }

        return getReflectionAccess().map(access -> {
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
                return quest != null && access.isCompleted(teamData, quest.quest());
            } catch (ReflectiveOperationException | RuntimeException exception) {
                LOGGER.warn("[PhantasmBriefing] Failed to query FTB quest '{}' for player {}: {}", questId, player.getGameProfile().getName(), exception.getMessage());
                return false;
            }
        }).orElse(false);
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
                    LOGGER.warn("[PhantasmBriefing] FTB reflection bridge is unavailable; FTB-gated dialogue will fail safe until a compatible API path is found: {}", exception.getMessage());
                }
                return Optional.empty();
            } finally {
                reflectionAccessResolved = true;
            }
        }
    }

    public static void completeQuestSilently(ServerPlayer player, String questId) {
        if (!isFTBQuestsLoaded() || questId == null || questId.isBlank()) {
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

                access.completeQuest(teamData, quest);
                access.claimQuestRewards(teamData, quest.quest(), player);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                LOGGER.warn("[PhantasmBriefing] Failed to complete FTB quest '{}' for player {}: {}", questId, player.getGameProfile().getName(), exception.getMessage());
            }
        });
    }

    private static final class ReflectionAccess {
        private final Class<?> playerClass;
        private final Class<?> questObjectClass;
        private final Class<?> teamDataClass;
        private final Class<?> questObjectBaseClass;
        private final Method apiMethod;
        private final Method getQuestFileMethod;
        private final Method staticTeamDataMethod;
        private final Method questFileTeamDataMethod;
        private final Method getIdMethod;
        private final Method getQuestMethod;
        private final Method parseCodeStringMethod;
        private final Method isCompletedMethod;

        private ReflectionAccess() throws ReflectiveOperationException {
            Class<?> ftbApiClass = Class.forName("dev.ftb.mods.ftbquests.api.FTBQuestsAPI");
            Class<?> apiInterface = Class.forName("dev.ftb.mods.ftbquests.api.FTBQuestsAPI$API");
            Class<?> questFileClass = Class.forName("dev.ftb.mods.ftbquests.api.QuestFile");
            Class<?> baseQuestFileClass = Class.forName("dev.ftb.mods.ftbquests.quest.BaseQuestFile");
            this.questObjectBaseClass = Class.forName("dev.ftb.mods.ftbquests.quest.QuestObjectBase");
            this.playerClass = Class.forName("net.minecraft.world.entity.player.Player");
            Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
            this.questObjectClass = Class.forName("dev.ftb.mods.ftbquests.quest.QuestObject");
            this.teamDataClass = Class.forName("dev.ftb.mods.ftbquests.quest.TeamData");

            this.apiMethod = ftbApiClass.getMethod("api");
            this.getQuestFileMethod = apiInterface.getMethod("getQuestFile", boolean.class);
            this.staticTeamDataMethod = findMethod(this.teamDataClass, "get", this.playerClass);
            this.questFileTeamDataMethod = findQuestFileTeamDataMethod(
                    questFileClass,
                    this.playerClass,
                    entityClass
            );
            if (this.staticTeamDataMethod == null && this.questFileTeamDataMethod == null) {
                throw new NoSuchMethodException("No compatible FTB Quests team-data accessor found");
            }
            this.getIdMethod = findMethod(baseQuestFileClass, "getID", Object.class);
            this.getQuestMethod = baseQuestFileClass.getMethod("getQuest", long.class);
            this.parseCodeStringMethod = findMethod(this.questObjectBaseClass, "parseCodeString", String.class);
            this.isCompletedMethod = this.teamDataClass.getMethod("isCompleted", this.questObjectClass);
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
            if (longId <= 0L) {
                return null;
            }
            Object quest = this.getQuestMethod.invoke(questFile, longId);
            return quest == null ? null : new ResolvedQuest(longId, quest);
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

        private void completeQuest(Object teamData, ResolvedQuest quest) throws ReflectiveOperationException {
            CompletionAccess completionAccess = CompletionAccess.get();
            if (completionAccess == null) {
                return;
            }
            completionAccess.setCompletedMethod.invoke(teamData, quest.id(), new Date());
        }

        private void claimQuestRewards(Object teamData, Object quest, ServerPlayer player) throws ReflectiveOperationException {
            CompletionAccess completionAccess = CompletionAccess.get();
            if (completionAccess == null || completionAccess.getRewardsMethod == null) {
                return;
            }
            @SuppressWarnings("unchecked")
            Collection<Object> rewards = (Collection<Object>) completionAccess.getRewardsMethod.invoke(quest);
            long now = System.currentTimeMillis();
            for (Object reward : rewards) {
                if (completionAccess.claimRewardMethod != null) {
                    completionAccess.claimRewardMethod.invoke(teamData, player, reward, true, now);
                } else if (completionAccess.rewardClaimMethod != null && completionAccess.markRewardAsClaimedMethod != null) {
                    completionAccess.rewardClaimMethod.invoke(reward, player, false);
                    completionAccess.markRewardAsClaimedMethod.invoke(teamData, player.getUUID(), reward, now);
                }
            }
        }
    }

    private record ResolvedQuest(long id, Object quest) {
    }

    private static final class CompletionAccess {
        private static volatile CompletionAccess instance;
        private static volatile boolean resolved;

        private final Method setCompletedMethod;
        private final Method getRewardsMethod;
        private final Method claimRewardMethod;
        private final Method rewardClaimMethod;
        private final Method markRewardAsClaimedMethod;

        private CompletionAccess() throws ReflectiveOperationException {
            Class<?> teamDataClass = Class.forName("dev.ftb.mods.ftbquests.quest.TeamData");
            Class<?> questClass = Class.forName("dev.ftb.mods.ftbquests.quest.Quest");
            Class<?> rewardClass = Class.forName("dev.ftb.mods.ftbquests.quest.reward.Reward");

            this.setCompletedMethod = findRequiredMethod(teamDataClass, "setCompleted", long.class, Date.class);
            this.getRewardsMethod = findMethod(questClass, "getRewards");
            this.claimRewardMethod = findMethod(teamDataClass, "claimReward", ServerPlayer.class, rewardClass, boolean.class, long.class);
            this.rewardClaimMethod = findMethod(rewardClass, "claim", ServerPlayer.class, boolean.class);
            this.markRewardAsClaimedMethod = findMethod(teamDataClass, "markRewardAsClaimed", UUID.class, rewardClass, long.class);
        }

        private static CompletionAccess get() {
            CompletionAccess existing = instance;
            if (existing != null) {
                return existing;
            }
            if (resolved) {
                return null;
            }

            synchronized (CompletionAccess.class) {
                if (instance != null) {
                    return instance;
                }
                if (resolved) {
                    return null;
                }

                try {
                    instance = new CompletionAccess();
                    return instance;
                } catch (ReflectiveOperationException | RuntimeException exception) {
                    if (!completionAccessUnavailableLogged) {
                        completionAccessUnavailableLogged = true;
                        LOGGER.warn("[PhantasmBriefing] FTB completion bridge is unavailable; prerequisite checks may still work, but silent quest completion will be skipped: {}", exception.getMessage());
                    }
                    return null;
                } finally {
                    resolved = true;
                }
            }
        }
    }

    private static Method findRequiredMethod(Class<?> owner, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        return owner.getMethod(methodName, parameterTypes);
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
