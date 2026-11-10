package com.tr4nce.rabbitcore.block;

import com.tr4nce.rabbitcore.block.entity.RabbitChestBlockEntity;
import com.tr4nce.rabbitcore.registry.ModBlockEntities;
import com.tr4nce.rabbitcore.world.storage.ChestRegistryData;
import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;
import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

public class RabbitChestBlock extends ChestBlock {
    public RabbitChestBlock(Properties properties) {
        // 使用原版箱子的属性创建
        super(properties, ModBlockEntities.RABBIT_CHEST::get);

        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(TYPE, ChestType.SINGLE)
                .setValue(WATERLOGGED, false)
        );
    }

    @Override
    public @NotNull BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new RabbitChestBlockEntity(pos, state);
    }

    @Override
    public @NotNull BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction direction = context.getHorizontalDirection().getOpposite();
        FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());
        return this.defaultBlockState()
                .setValue(FACING, direction)
                .setValue(TYPE, ChestType.SINGLE) // 关键：强制单箱子
                .setValue(WATERLOGGED, fluidstate.getType() == Fluids.WATER);
    }

    @Override
    public DoubleBlockCombiner.@NotNull NeighborCombineResult<? extends ChestBlockEntity> combine(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, boolean ignoreBlockedChest) {
        // 获取方块实体
        BlockEntity blockEntity = level.getBlockEntity(pos);

        // 确保它是我们的箱子实体
        if (blockEntity instanceof RabbitChestBlockEntity rabbitChest) {
            // 返回 Single 结果，告诉游戏这就只是一个单箱子，可以打开
            return new DoubleBlockCombiner.NeighborCombineResult.Single<>(rabbitChest);
        }

        // 如果出错，返回 None
        return DoubleBlockCombiner.Combiner::acceptNone;
    }

    @Override
    public @NotNull BlockState updateShape(BlockState state, @NotNull Direction facing, @NotNull BlockState facingState, @NotNull LevelAccessor level, @NotNull BlockPos currentPos, @NotNull BlockPos facingPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        // 直接返回当前状态，不进行合并检查
        return state;
    }

    @Override
    public @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
        // 先执行原版箱子的打开逻辑（打开GUI，播放动画）
        InteractionResult result = super.useWithoutItem(state, level, pos, player, hit);
        // 如果在服务端且交互成功
        if (!level.isClientSide && result.consumesAction() && player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            triggerQuestCompletion(serverLevel, pos, serverPlayer);
        }
        return result;
    }

    private void triggerQuestCompletion(ServerLevel level, BlockPos pos, ServerPlayer player) {
        GlobalPos gPos = GlobalPos.of(level.dimension(), pos);
        ChestRegistryData data = ChestRegistryData.get(level);
        ChestRegistryData.ChestInfo info = data.getAllChests().get(gPos);

        if (info == null || info.questId == null || info.questId.isEmpty()) {
            return;
        }

        String questId = info.questId;

        // 获取服务端任务文件，避免在服务端引用客户端类
        BaseQuestFile file;
        try {
            // 优先尝试基于服务端的获取方式（如果当前 FTBQuests 版本支持）
            file = FTBQuestsAPI.api().getQuestFile(false);
        } catch (Throwable t) {
            // 无论何种原因拿不到任务文件，都直接返回，避免崩服
            return;
        }

        if (file == null) {
            return;
        }

        TeamData teamData = file.getOrCreateTeamData(player.getUUID());
        if (teamData == null) {
            return;
        }

        Task targetTask = findTaskByTagOrId(file, questId);
        if (targetTask == null) {
            return;
        }

        Quest quest = targetTask.getQuest();
//        if (!teamData.canStartTasks(quest)) {
//            player.sendSystemMessage(Component.literal(String.format("§c前置任务 %s 未完成，开启此箱子将无法完成任务！", quest.getTitle().getString())));
//            return;
//        }


        if (teamData.isCompleted(targetTask)) {
            return;
        }

        MinecraftServer server = level.getServer();

        CommandSourceStack source = server.createCommandSourceStack()
                .withPermission(4)
                .withSuppressedOutput();
        String hexId = Long.toHexString(targetTask.id).toUpperCase();
        String command = String.format("ftbquests change_progress %s complete %s",
                player.getGameProfile().getName(),
                hexId);
        server.getCommands().performPrefixedCommand(source, command);

        // Broadcast quest completion to all players on the server
        broadcastQuestCompletion(level, targetTask, player, questId);
    }

    private void broadcastQuestCompletion(ServerLevel level, Task task, ServerPlayer player, String questId) {
        MinecraftServer server = level.getServer();

        // Try to get a readable quest/task name; fall back to questId if necessary
        String questName = questId;
        try {
            Component titleComponent = task.getTitle();
            if (titleComponent != null) {
                String raw = titleComponent.getString();
                if (!raw.isEmpty()) {
                    questName = raw;
                }
            }
        } catch (Throwable ignored) {
        }

        // 彩色高亮：[RabbitCore] 前缀、玩家名、任务名
        Component message = Component.literal("[RabbitCore] ")
                .withStyle(ChatFormatting.GOLD)
                .append(player.getDisplayName().copy().withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" 找到了一处宝箱 ").withStyle(ChatFormatting.WHITE));

        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    private Task findTaskByTagOrId(BaseQuestFile file, String questId) {
        // 简单遍历所有任务，按照自定义约定匹配 questId
        for (Task task : file.getAllTasks()) { // 如果没有 getAllTasks，可以改为通过章节/任务线遍历
            if (task.hasTag(questId)) {
                return task;
            }
        }
        return null;
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean isMoving) {
        // 如果方块类型发生变化（例如被打掉/替换为其他方块），则尝试移除注册信息
        if (!level.isClientSide && state.getBlock() != newState.getBlock() && level instanceof ServerLevel serverLevel) {
            GlobalPos gPos = GlobalPos.of(serverLevel.dimension(), pos);
            ChestRegistryData data = ChestRegistryData.get(serverLevel);
            data.removeChest(gPos);
        }

        // 保持原有的箱子清理逻辑
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        // 只在服务端执行
        if (level.isClientSide) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        // 1. 生成时间戳
        long gameTime = serverLevel.getGameTime();
        long wallTime = System.currentTimeMillis();

        // 2. 把时间戳和基于时间的 questId 写回物品 NBT
        String questId = "qt_" + gameTime + "_" + wallTime;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putLong("RabbitPlacedGameTime", gameTime);
            tag.putLong("RabbitPlacedWallTime", wallTime);
            tag.putString("RabbitQuestId", questId);
        });

        // 3. 写入全局持久化数据
        GlobalPos gPos = GlobalPos.of(serverLevel.dimension(), pos);
        ChestRegistryData data = ChestRegistryData.get(serverLevel);
        data.addChest(gPos, questId, gameTime, wallTime);

        // 4. 提示信息：只显示坐标和 questId
        if (placer instanceof Player player) {
            String dimId = gPos.dimension().location().toString();
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();

            String msg = String.format("放置 RabbitChest 于 [%s] (%d, %d, %d) | questId=%s",
                    dimId, x, y, z, questId);
            player.sendSystemMessage(Component.literal(msg));
        }
    }
}
