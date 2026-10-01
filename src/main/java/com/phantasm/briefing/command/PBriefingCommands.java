package com.phantasm.briefing.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.phantasm.briefing.data.QuestMarkerSpec;
import com.phantasm.briefing.data.QuestJournalEntry;
import com.phantasm.briefing.data.NpcBindingDataManager;
import com.phantasm.briefing.data.NpcBindingSpec;
import com.phantasm.briefing.data.NpcStructureSpawnSpec;
import com.phantasm.briefing.network.ModNetwork;
import com.phantasm.briefing.network.packet.OpenQuestJournalS2CPacket;
import com.phantasm.briefing.network.packet.OpenQuestTreeS2CPacket;
import com.phantasm.briefing.network.packet.SetQuestTrackerHudVisibilityS2CPacket;
import com.phantasm.briefing.service.BriefingPlayerData;
import com.phantasm.briefing.service.QuestRuntimeService;
import com.phantasm.briefing.service.QuestEntityHintService;
import com.phantasm.briefing.service.DialogueFlowCoordinator;
import com.phantasm.briefing.service.DialogueShopService;
import com.phantasm.briefing.service.QuestJournalService;
import com.phantasm.briefing.service.QuestTreeService;
import com.phantasm.briefing.service.QuestTrackerService;
import com.phantasm.briefing.service.StructureSearchCompatService;
import com.phantasm.briefing.service.NpcSpawnService;
import com.phantasm.briefing.service.NpcStructureSpawnService;
import com.phantasm.briefing.util.QuestNpcHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;

public final class PBriefingCommands {
    private static final double TARGET_DISTANCE = 32.0D;

    private static final SimpleCommandExceptionType NO_ENTITY_TARGET =
            new SimpleCommandExceptionType(Component.literal("未找到可转化的生物目标。"));

    private static final SimpleCommandExceptionType INVALID_ENTITY_TARGET =
            new SimpleCommandExceptionType(Component.literal("目标必须是非玩家的 LivingEntity。"));

    private static final SimpleCommandExceptionType INVALID_QUEST_NODE_ID =
            new SimpleCommandExceptionType(Component.literal("questNodeId 不能为空。"));

    private static final SimpleCommandExceptionType CAPABILITY_WRITE_FAILED =
            new SimpleCommandExceptionType(Component.literal("任务 NPC 标记写入失败。"));

    private static final SimpleCommandExceptionType NPC_RESTORE_FAILED =
            new SimpleCommandExceptionType(Component.literal("目标不是可还原的转化型任务 NPC。"));

    private PBriefingCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pbriefing")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reload")
                        .executes(PBriefingCommands::executeReload))
                .then(Commands.literal("dialogue")
                        .then(Commands.literal("start")
                                .then(Commands.argument("targets", playersArgument())
                                        .then(Commands.argument("dialogueId", wordArgument())
                                                .executes(PBriefingCommands::executeDialogueStart)))))
                .then(Commands.literal("quest")
                        .then(Commands.literal("give")
                                .then(Commands.argument("targets", playersArgument())
                                        .then(Commands.argument("questId", idArgument())
                                                .executes(PBriefingCommands::executeQuestGive))))
                        .then(Commands.literal("complete")
                                .then(Commands.argument("targets", playersArgument())
                                        .then(Commands.argument("questId", idArgument())
                                                .executes(PBriefingCommands::executeQuestComplete))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("targets", playersArgument())
                                        .then(Commands.argument("questId", idArgument())
                                                .executes(PBriefingCommands::executeQuestReset))))
                        .then(Commands.literal("reset_all")
                                .executes(PBriefingCommands::executeQuestResetAllSelfOrAll)
                                .then(Commands.argument("targets", playersArgument())
                                        .executes(PBriefingCommands::executeQuestResetAllTargets)))
                        .then(Commands.literal("journal")
                                .executes(PBriefingCommands::executeQuestJournal))
                        .then(Commands.literal("tree")
                                .executes(PBriefingCommands::executeQuestTree))
                        .then(Commands.literal("tracker")
                                .executes(PBriefingCommands::executeQuestTracker))
                        .then(Commands.literal("structure_found")
                                .then(Commands.argument("targets", playersArgument())
                                        .then(Commands.argument("structureId", idArgument())
                                                .then(Commands.argument("dimension", idArgument())
                                                        .then(Commands.argument("x", doubleArgument())
                                                                .then(Commands.argument("y", doubleArgument())
                                                                        .then(Commands.argument("z", doubleArgument())
                                                                                .executes(PBriefingCommands::executeStructureFound)
                                                                                .then(Commands.argument("label", greedyStringArgument())
                                                                                        .executes(PBriefingCommands::executeStructureFound)))))))))
                        .then(Commands.literal("structure_spawn_npc")
                                .then(Commands.argument("targets", playersArgument())
                                        .then(Commands.argument("questId", idArgument())
                                                .then(Commands.argument("objectiveId", wordArgument())
                                                        .executes(PBriefingCommands::executeStructureSpawnNpc)
                                                        .then(Commands.argument("questNodeId", wordArgument())
                                                                .executes(PBriefingCommands::executeStructureSpawnNpc))))))
                        .then(Commands.literal("tracker_hud")
                                .then(Commands.literal("on")
                                        .executes(context -> executeQuestTrackerHud(context, true)))
                                .then(Commands.literal("off")
                                        .executes(context -> executeQuestTrackerHud(context, false)))))
                .then(Commands.literal("wallet")
                        .executes(PBriefingCommands::executeWalletOverview))
                .then(Commands.literal("shop")
                        .then(Commands.literal("open")
                                .then(Commands.argument("shopId", idArgument())
                                        .executes(PBriefingCommands::executeShopOpenSelf))
                                .then(Commands.argument("targets", playersArgument())
                                        .then(Commands.argument("shopId", idArgument())
                                                .executes(PBriefingCommands::executeShopOpenTargets)))))
                .then(Commands.literal("npc")
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("bindingId", wordArgument())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                NpcBindingDataManager.getInstance().getBindingIds(),
                                                builder
                                        ))
                                        .executes(context -> executeNpcSpawn(context, null))
                                        .then(Commands.argument("position", Vec3Argument.vec3())
                                                .executes(context -> executeNpcSpawn(
                                                        context,
                                                        Vec3Argument.getVec3(context, "position")
                                                )))))
                        .then(Commands.literal("refresh")
                                .executes(context -> executeNpcRefresh(context, null))
                                .then(Commands.argument("bindingId", wordArgument())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                NpcBindingDataManager.getInstance().getBindingIds(),
                                                builder
                                        ))
                                        .executes(context -> executeNpcRefresh(
                                                context,
                                                StringArgumentType.getString(context, "bindingId")
                                        ))))
                        .then(Commands.literal("respawn")
                                .executes(context -> executeNpcRespawn(context, null))
                                .then(Commands.argument("bindingId", wordArgument())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                NpcBindingDataManager.getInstance().getBindingIds(),
                                                builder
                                        ))
                                        .executes(context -> executeNpcRespawn(
                                                context,
                                                StringArgumentType.getString(context, "bindingId")
                                        ))))
                        .then(Commands.literal("purge_looked")
                                .executes(PBriefingCommands::executeNpcPurgeLooked)))
                .then(Commands.literal("setnpc")
                        .then(Commands.argument("questNodeId", wordArgument())
                                .executes(context -> executeSetNpc(context, StringArgumentType.getString(context, "questNodeId")))))
                .then(Commands.literal("removenpc")
                        .executes(PBriefingCommands::executeRemoveNpc)));
    }

    private static int executeReload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> literal("已开始重载 PhantasmBriefing 数据包内容。"), true);
        source.getServer().reloadResources(source.getServer().getPackRepository().getSelectedIds())
                .whenComplete((ignored, throwable) -> source.getServer().execute(() -> {
                    if (throwable != null) {
                        source.sendFailure(literal("PhantasmBriefing 数据重载失败，请检查日志。"));
                        return;
                    }
                    source.sendSuccess(() -> literal("PhantasmBriefing 数据重载完成。"), true);
                }));
        return 1;
    }

    private static int executeQuestJournal(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        List<QuestJournalEntry> entries = QuestJournalService.buildEntries(player);
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                OpenQuestJournalS2CPacket.forPlayer(player)
        );
        context.getSource().sendSuccess(() -> literal("已打开任务日志。"), false);
        return 1;
    }

    private static int executeQuestTree(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var snapshot = QuestTreeService.buildSnapshot(player);
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new OpenQuestTreeS2CPacket(snapshot.nodes(), snapshot.edges())
        );
        context.getSource().sendSuccess(() -> literal("已打开任务树。"), false);
        return 1;
    }

    private static int executeQuestGive(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = players(context);
        ResourceLocation questId = id(context, "questId");
        int changed = forPlayers(players, player -> QuestRuntimeService.give(player, questId.toString()));
        if (changed <= 0) {
            context.getSource().sendFailure(literal("无法发放任务：" + questId));
            return 0;
        }
        context.getSource().sendSuccess(() -> literal("已为 " + changed + " 名玩家发放任务：" + questId), true);
        return changed;
    }

    private static int executeQuestComplete(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = players(context);
        ResourceLocation questId = id(context, "questId");
        int changed = forPlayers(players, player -> QuestRuntimeService.complete(player, questId.toString()));
        if (changed <= 0) {
            context.getSource().sendFailure(literal("无法完成任务：" + questId));
            return 0;
        }
        context.getSource().sendSuccess(() -> literal("已为 " + changed + " 名玩家完成任务：" + questId), true);
        return changed;
    }

    private static int executeQuestReset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = players(context);
        ResourceLocation questId = id(context, "questId");
        int changed = forPlayers(players, player -> QuestRuntimeService.reset(player, questId.toString()));
        if (changed <= 0) {
            context.getSource().sendFailure(literal("无法重置任务：" + questId));
            return 0;
        }
        context.getSource().sendSuccess(
                () -> literal("已为 " + changed + " 名玩家重置内部任务状态：" + questId + "（不会回滚外部 FTB 完成态）"),
                true
        );
        return changed;
    }

    private static int executeQuestResetAllSelfOrAll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = resolveResetAllTargets(context);
        int changed = 0;
        for (ServerPlayer player : players) {
            changed += QuestRuntimeService.resetAll(player);
        }
        int affectedPlayers = players.size();
        int totalReset = changed;
        context.getSource().sendSuccess(
                () -> literal("已重置 " + affectedPlayers + " 名玩家的全部内部任务状态，共清理 " + totalReset + " 条任务记录。"),
                true
        );
        return changed;
    }

    private static int executeQuestResetAllTargets(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = players(context);
        int changed = 0;
        for (ServerPlayer player : players) {
            changed += QuestRuntimeService.resetAll(player);
        }
        int affectedPlayers = players.size();
        int totalReset = changed;
        context.getSource().sendSuccess(
                () -> literal("已重置 " + affectedPlayers + " 名玩家的全部内部任务状态，共清理 " + totalReset + " 条任务记录。"),
                true
        );
        return changed;
    }

    private static int executeQuestTracker(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        QuestTrackerService.syncToClient(player);
        context.getSource().sendSuccess(() -> literal("已同步当前任务追踪。"), false);
        return 1;
    }

    private static int executeStructureFound(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = players(context);
        ResourceLocation structureId = id(context, "structureId");
        ResourceLocation dimension = id(context, "dimension");
        double x = DoubleArgumentType.getDouble(context, "x");
        double y = DoubleArgumentType.getDouble(context, "y");
        double z = DoubleArgumentType.getDouble(context, "z");
        String label = readOptionalWord(context, "label");
        QuestMarkerSpec marker = new QuestMarkerSpec(label, dimension.toString(), x, y, z);

        int matched = 0;
        for (ServerPlayer player : players) {
            matched += StructureSearchCompatService.reportLocatedStructure(player, structureId.toString(), marker);
        }
        if (matched <= 0) {
            context.getSource().sendFailure(literal("没有匹配到需要结构目标的活动任务：" + structureId));
            return 0;
        }
        int totalPlayers = players.size();
        int totalMatched = matched;
        context.getSource().sendSuccess(
                () -> literal("已为 " + totalPlayers + " 名玩家注入结构定位结果，共更新 " + totalMatched + " 个任务目标。"),
                true
        );
        return matched;
    }

    private static int executeStructureSpawnNpc(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = players(context);
        ResourceLocation questId = id(context, "questId");
        String objectiveId = StringArgumentType.getString(context, "objectiveId");
        String questNodeId = readOptionalWord(context, "questNodeId");

        int spawned = forPlayers(players, player ->
                StructureSearchCompatService.spawnResolvedStructureNpc(player, questId.toString(), objectiveId, questNodeId)
        );
        if (spawned <= 0) {
            context.getSource().sendFailure(literal("无法生成结构任务 NPC，请确认该目标已写入运行时结构坐标。"));
            return 0;
        }
        context.getSource().sendSuccess(
                () -> literal("已为 " + spawned + " 名玩家对应的结构目标生成任务 NPC 锚点。"),
                true
        );
        return spawned;
    }

    private static int executeQuestTrackerHud(CommandContext<CommandSourceStack> context, boolean enabled) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        BriefingPlayerData.setTrackerHudEnabled(player, enabled);
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SetQuestTrackerHudVisibilityS2CPacket(enabled)
        );
        QuestEntityHintService.requestSync(player);
        context.getSource().sendSuccess(
                () -> literal(enabled ? "已开启任务追踪 HUD。" : "已关闭任务追踪 HUD，仅保留附近任务实体提示。"),
                false
        );
        return 1;
    }

    private static int executeWalletOverview(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        DialogueShopService.openWalletOverview(player);
        context.getSource().sendSuccess(() -> literal("已打开钱包总览。"), false);
        return 1;
    }

    private static int executeDialogueStart(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = players(context);
        String dialogueId = StringArgumentType.getString(context, "dialogueId");
        int opened = forPlayers(players, player -> DialogueFlowCoordinator.openDialogueForPlayer(player, dialogueId));
        if (opened <= 0) {
            context.getSource().sendFailure(literal("无法打开对话：" + dialogueId));
            return 0;
        }
        context.getSource().sendSuccess(() -> literal("已为 " + opened + " 名玩家打开对话：" + dialogueId), true);
        return opened;
    }

    private static int executeShopOpenSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ResourceLocation shopId = id(context, "shopId");
        if (!DialogueShopService.openShop(player, shopId.toString())) {
            context.getSource().sendFailure(literal("无法打开商店：" + shopId));
            return 0;
        }
        context.getSource().sendSuccess(() -> literal("已打开商店：" + shopId), false);
        return 1;
    }

    private static int executeShopOpenTargets(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> players = players(context);
        ResourceLocation shopId = id(context, "shopId");
        int opened = forPlayers(players, player -> DialogueShopService.openShop(player, shopId.toString()));
        if (opened <= 0) {
            context.getSource().sendFailure(literal("无法为目标玩家打开商店：" + shopId));
            return 0;
        }
        context.getSource().sendSuccess(() -> literal("已为 " + opened + " 名玩家打开商店：" + shopId), true);
        return opened;
    }

    private static int executeNpcSpawn(
            CommandContext<CommandSourceStack> context,
            @Nullable Vec3 requestedPosition
    ) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String bindingId = StringArgumentType.getString(context, "bindingId");
        NpcBindingDataManager bindingManager = NpcBindingDataManager.getInstance();
        NpcBindingSpec binding = bindingManager.getBinding(bindingId).orElse(null);
        if (binding == null) {
            List<String> availableBindings = bindingManager.getBindingIds();
            if (availableBindings.isEmpty()) {
                source.sendFailure(literal(
                        "未找到 NPC 绑定：" + bindingId
                                + "。当前没有加载任何 NPC 绑定，请先在编辑器保存并热加载。"
                ));
            } else {
                String availableText = String.join("、", availableBindings.stream().limit(8).toList());
                source.sendFailure(literal(
                        "未找到 NPC 绑定：" + bindingId + "。当前可用：" + availableText
                ));
            }
            return 0;
        }

        Vec3 position = requestedPosition;
        if (position == null) {
            ServerPlayer player = source.getPlayerOrException();
            Vec3 look = player.getLookAngle();
            double horizontalLength = Math.sqrt(look.x * look.x + look.z * look.z);
            double directionX = horizontalLength > 0.001D ? look.x / horizontalLength : 0.0D;
            double directionZ = horizontalLength > 0.001D ? look.z / horizontalLength : 1.0D;
            position = new Vec3(
                    player.getX() + directionX * 2.0D,
                    player.getY(),
                    player.getZ() + directionZ * 2.0D
            );
        }

        NpcStructureSpawnSpec settings = binding.spawnRules().stream().findFirst().orElse(null);
        boolean noAi = settings != null && settings.noAi();
        boolean nameVisible = settings == null || settings.nameVisible();
        int villagerLevel = settings == null ? 1 : settings.villagerLevel();
        NpcSpawnService.SpawnResult result = NpcSpawnService.spawn(
                source.getLevel(),
                position,
                source.getRotation().y,
                binding,
                MobSpawnType.COMMAND,
                noAi,
                nameVisible,
                villagerLevel,
                entity -> {
                }
        );
        if (!result.succeeded()) {
            source.sendFailure(literal("NPC 生成失败：" + result.error()));
            return 0;
        }

        Vec3 spawnedAt = position;
        source.sendSuccess(
                () -> literal(
                        "已生成 NPC “" + binding.bindingId() + "” 于 "
                                + String.format("%.1f, %.1f, %.1f", spawnedAt.x, spawnedAt.y, spawnedAt.z)
                ),
                true
        );
        return 1;
    }

    private static int executeNpcRefresh(
            CommandContext<CommandSourceStack> context,
            @Nullable String bindingId
    ) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int refreshed = NpcStructureSpawnService.refreshNearbyGeneratedNpcs(player, bindingId);
        context.getSource().sendSuccess(
                () -> Component.translatable("msg.phantasmbriefing.npc_refresh_success")
                        .append(Component.literal(Integer.toString(refreshed))),
                false
        );
        return Math.max(1, refreshed);
    }

    private static int executeNpcRespawn(
            CommandContext<CommandSourceStack> context,
            @Nullable String bindingId
    ) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int removed = NpcStructureSpawnService.respawnNearbyGeneratedNpcs(player, bindingId);
        context.getSource().sendSuccess(
                () -> Component.translatable("msg.phantasmbriefing.npc_respawn_success")
                        .append(Component.literal(Integer.toString(removed))),
                false
        );
        return Math.max(1, removed);
    }

    private static int executeNpcPurgeLooked(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Entity target = findLookTarget(player);
        if (target == null) {
            throw NO_ENTITY_TARGET.create();
        }
        if (!(target instanceof LivingEntity livingEntity) || target instanceof Player) {
            throw INVALID_ENTITY_TARGET.create();
        }

        NpcStructureSpawnService.ManualNpcRemoval result =
                NpcStructureSpawnService.removeManagedNpcManually(livingEntity);
        if (!result.removed()) {
            context.getSource().sendFailure(Component.literal(
                    "目标没有 PhantasmalChronicle 管理标记，已拒绝删除。"
            ));
            return 0;
        }

        QuestEntityHintService.requestSync(player);
        String bindingText = result.bindingId().isBlank() ? "(无 bindingId)" : result.bindingId();
        String registeredWarning = result.bindingStillRegistered()
                ? "；警告：该 Binding 当前仍存在，后续规则可能再次生成 NPC"
                : "";
        context.getSource().sendSuccess(
                () -> Component.literal(
                        "已强制清理准星目标 NPC，Binding=" + bindingText
                                + "，生成记录=" + result.removedSpawnRecords()
                                + "，结构完成记录=" + result.removedCompletionRecords()
                                + registeredWarning
                ),
                true
        );
        return 1;
    }

    private static int executeSetNpc(CommandContext<CommandSourceStack> context, String questNodeId) throws CommandSyntaxException {
        String normalizedId = QuestNpcHelper.sanitizeQuestNodeId(questNodeId);
        if (normalizedId.isBlank()) {
            throw INVALID_QUEST_NODE_ID.create();
        }

        ServerPlayer player = context.getSource().getPlayerOrException();
        Entity target = findLookTarget(player);
        if (target == null) {
            throw NO_ENTITY_TARGET.create();
        }

        if (!(target instanceof LivingEntity livingEntity) || target instanceof Player) {
            throw INVALID_ENTITY_TARGET.create();
        }

        if (!QuestNpcHelper.assignQuestNode(livingEntity, normalizedId)) {
            throw CAPABILITY_WRITE_FAILED.create();
        }

        context.getSource().sendSuccess(
                () -> literal("已将目标生物转换为任务 NPC，Quest Node ID = " + normalizedId),
                true
        );
        return 1;
    }

    private static int executeRemoveNpc(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Entity target = findLookTarget(player);
        if (target == null) {
            throw NO_ENTITY_TARGET.create();
        }

        if (!(target instanceof LivingEntity livingEntity) || target instanceof Player) {
            throw INVALID_ENTITY_TARGET.create();
        }

        if (!QuestNpcHelper.removeQuestNode(livingEntity)) {
            throw NPC_RESTORE_FAILED.create();
        }

        context.getSource().sendSuccess(
                () -> literal("已将目标生物恢复为普通生物，并保留其原本实体能力。"),
                true
        );
        return 1;
    }

    @Nullable
    private static Entity findLookTarget(ServerPlayer player) {
        Vec3 eyePosition = player.getEyePosition();
        Vec3 viewVector = player.getViewVector(1.0F);
        Vec3 reachEnd = eyePosition.add(viewVector.scale(TARGET_DISTANCE));
        AABB searchBox = player.getBoundingBox().expandTowards(viewVector.scale(TARGET_DISTANCE)).inflate(1.0D);
        EntityHitResult hitResult = ProjectileUtil.getEntityHitResult(
                player,
                eyePosition,
                reachEnd,
                searchBox,
                entity -> entity instanceof LivingEntity && entity.isAlive() && entity != player,
                TARGET_DISTANCE * TARGET_DISTANCE
        );
        return hitResult == null ? null : hitResult.getEntity();
    }

    private static int forPlayers(Collection<ServerPlayer> players, PlayerAction action) {
        int count = 0;
        for (ServerPlayer player : players) {
            if (action.run(player)) {
                count++;
            }
        }
        return count;
    }

    private static Collection<ServerPlayer> resolveResetAllTargets(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        try {
            return List.of(context.getSource().getPlayerOrException());
        } catch (CommandSyntaxException ignored) {
            return context.getSource().getServer().getPlayerList().getPlayers();
        }
    }

    private static String readOptionalWord(CommandContext<CommandSourceStack> context, String argumentName) {
        try {
            return StringArgumentType.getString(context, argumentName).trim();
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    @Nonnull
    private static Collection<ServerPlayer> players(@Nonnull CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return EntityArgument.getPlayers(context, "targets");
    }

    @Nonnull
    private static ResourceLocation id(@Nonnull CommandContext<CommandSourceStack> context, @Nonnull String argumentName) {
        return ResourceLocationArgument.getId(context, argumentName);
    }

    @Nonnull
    private static Component literal(@Nonnull String text) {
        return Component.literal(text);
    }

    @Nonnull
    private static ArgumentType<?> playersArgument() {
        return EntityArgument.players();
    }

    @Nonnull
    private static ArgumentType<String> wordArgument() {
        return StringArgumentType.word();
    }

    @Nonnull
    private static ArgumentType<String> greedyStringArgument() {
        return StringArgumentType.greedyString();
    }

    @Nonnull
    private static ArgumentType<ResourceLocation> idArgument() {
        return ResourceLocationArgument.id();
    }

    @Nonnull
    private static ArgumentType<Double> doubleArgument() {
        return DoubleArgumentType.doubleArg();
    }

    @FunctionalInterface
    private interface PlayerAction {
        boolean run(ServerPlayer player);
    }
}
