package com.phantasm.briefing.service;

import com.phantasm.briefing.data.NpcBindingSpec;
import com.phantasm.briefing.util.QuestNpcHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Consumer;
import javax.annotation.Nullable;

public final class NpcSpawnService {
    private NpcSpawnService() {
    }

    public static SpawnResult spawn(
            ServerLevel level,
            Vec3 position,
            float yaw,
            NpcBindingSpec binding,
            MobSpawnType spawnType,
            boolean noAi,
            boolean nameVisible,
            int villagerLevel,
            Consumer<LivingEntity> initializer
    ) {
        ResourceLocation entityTypeId = ResourceLocation.tryParse(binding.entityType());
        EntityType<?> entityType = entityTypeId == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(entityTypeId);
        if (entityType == null) {
            return SpawnResult.failure("未知实体类型：" + binding.entityType());
        }

        Entity created = entityType.create(level);
        if (!(created instanceof LivingEntity livingEntity)) {
            if (created != null) {
                created.discard();
            }
            return SpawnResult.failure("配置的实体不是生物：" + binding.entityType());
        }

        livingEntity.moveTo(position.x, position.y, position.z, yaw, 0.0F);
        livingEntity.setYHeadRot(yaw);
        livingEntity.setYBodyRot(yaw);
        if (!level.noCollision(livingEntity)) {
            livingEntity.discard();
            return SpawnResult.failure("生成位置被方块或实体阻挡");
        }

        BlockPos blockPos = BlockPos.containing(position);
        if (livingEntity instanceof Mob mob) {
            mob.finalizeSpawn(
                    level,
                    level.getCurrentDifficultyAt(blockPos),
                    spawnType,
                    null,
                    null
            );
            mob.setPersistenceRequired();
            mob.setNoAi(noAi);
        }

        String professionError = configureVillager(binding, livingEntity, villagerLevel);
        if (professionError != null) {
            livingEntity.discard();
            return SpawnResult.failure(professionError);
        }

        if (!binding.customName().isBlank()) {
            livingEntity.setCustomName(Component.literal(binding.customName()));
            livingEntity.setCustomNameVisible(nameVisible);
        }

        QuestNpcHelper.assignNpcIdentity(livingEntity, binding.bindingId());
        QuestNpcHelper.setUseNativeAi(livingEntity, !noAi);
        if (!QuestNpcHelper.assignQuestNode(livingEntity, binding.questNodeId())) {
            livingEntity.discard();
            return SpawnResult.failure("无法绑定任务节点：" + binding.questNodeId());
        }

        initializer.accept(livingEntity);
        if (!level.addFreshEntity(livingEntity)) {
            livingEntity.discard();
            return SpawnResult.failure("实体未能加入当前世界");
        }
        return SpawnResult.success(livingEntity);
    }

    @Nullable
    private static String configureVillager(
            NpcBindingSpec binding,
            LivingEntity livingEntity,
            int villagerLevel
    ) {
        if (binding.profession().isBlank()) {
            return null;
        }
        if (!(livingEntity instanceof Villager villager)) {
            return "非村民实体不能使用村民职业：" + binding.profession();
        }

        ResourceLocation professionId = ResourceLocation.tryParse(binding.profession());
        VillagerProfession profession = professionId == null
                ? null
                : ForgeRegistries.VILLAGER_PROFESSIONS.getValue(professionId);
        if (profession == null) {
            return "未知村民职业：" + binding.profession();
        }

        villager.setVillagerData(villager.getVillagerData()
                .setProfession(profession)
                .setLevel(Math.max(1, Math.min(5, villagerLevel))));
        return null;
    }

    public record SpawnResult(@Nullable LivingEntity entity, String error) {
        private static SpawnResult success(LivingEntity entity) {
            return new SpawnResult(entity, "");
        }

        private static SpawnResult failure(String error) {
            return new SpawnResult(null, error);
        }

        public boolean succeeded() {
            return this.entity != null;
        }
    }
}
