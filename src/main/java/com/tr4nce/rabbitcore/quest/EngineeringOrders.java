package com.tr4nce.rabbitcore.quest;

import com.mojang.logging.LogUtils;
import com.tr4nce.rabbitcore.world.storage.WeeklyQuestData;
import dev.architectury.networking.NetworkManager;
import dev.ftb.mods.ftbquests.net.EditObjectResponseMessage;
import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.ftb.mods.ftbquests.quest.translation.TranslationKey;
import dev.ftb.mods.ftbquests.quest.translation.TranslationManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

final class EngineeringOrders {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ORDER_TAG = "rabbit_engineering_order";
    private static final long WEEK_MILLIS = Duration.ofDays(7).toMillis();
    private static final List<Order> ORDERS = List.of(
            new Order("站台翻新", "Platform Renovation",
                    List.of(new Delivery(Items.CYAN_STAINED_GLASS, 64),
                            new Delivery(Items.LIGHT_GRAY_CONCRETE, 128), new Delivery(Items.COPPER_BULB, 16))),
            new Order("隧道加固", "Tunnel Reinforcement",
                    List.of(new Delivery(Items.DEEPSLATE_BRICKS, 128),
                            new Delivery(Items.IRON_BARS, 64), new Delivery(Items.STONE_BRICK_STAIRS, 16))),
            new Order("港口扩建", "Harbor Expansion",
                    List.of(new Delivery(Items.SPRUCE_PLANKS, 128),
                            new Delivery(Items.GLASS_PANE, 64), new Delivery(Items.SEA_LANTERN, 16))));

    private EngineeringOrders() {}

    static void apply(BaseQuestFile file, List<Quest> weeklies, WeeklyQuestData data, long weekStart) {
        if (!(file instanceof ServerQuestFile serverFile)) {
            return;
        }
        List<QuestObjectBase> changed = new ArrayList<>();
        for (Quest quest : weeklies) {
            if (!quest.hasTag(ORDER_TAG)) {
                continue;
            }
            List<ItemTask> tasks = quest.getTasksAsList().stream()
                    .filter(ItemTask.class::isInstance).map(ItemTask.class::cast)
                    .filter(ItemTask::consumesResources).toList();
            if (tasks.size() != 3 || quest.getTasksAsList().size() != 3) {
                LOGGER.warn("Engineering order {} must have exactly three consuming item tasks", quest.getCodeString());
                continue;
            }

            long firstWeek = data.getOrStartEngineeringRotation(weekStart);
            long weeksElapsed = Math.floorDiv(weekStart - firstWeek, WEEK_MILLIS);
            Order order = ORDERS.get(Math.floorMod(weeksElapsed, ORDERS.size()));
            for (int index = 0; index < tasks.size(); index++) {
                ItemTask task = tasks.get(index);
                Delivery delivery = order.deliveries().get(index);
                ItemStack stack = new ItemStack(delivery.item());
                if (task.getMaxProgress() != delivery.count()
                        || !ItemStack.isSameItemSameComponents(task.getItemStack(), stack)) {
                    task.setStackAndCount(stack, delivery.count());
                    changed.add(task);
                }
            }
            TranslationManager translations = file.getTranslationManager();
            boolean chineseChanged = applyText(translations, quest, "zh_cn", order.chinese(),
                    "本周工程为「" + order.chinese() + "」。按任务栏清单交付材料，领取建材兑换券。");
            boolean englishChanged = applyText(translations, quest, "en_us", order.english(),
                    "This week's project is " + order.english() + ". Deliver the listed materials to earn a building materials voucher.");
            if (chineseChanged || englishChanged) {
                changed.add(quest);
            }
        }
        if (changed.isEmpty()) {
            return;
        }
        file.clearCachedData();
        file.markDirty();
        serverFile.saveNow();

        var players = serverFile.server.getPlayerList().getPlayers();
        if (!players.isEmpty()) {
            players.forEach(file.getTranslationManager()::sendTranslationsToPlayer);
            for (QuestObjectBase object : changed) {
                NetworkManager.sendToPlayers(players, new EditObjectResponseMessage(object));
            }
        }
    }

    private static boolean applyText(TranslationManager translations, Quest quest, String locale,
                                     String subtitle, String description) {
        if (translations.getStringTranslation(quest, locale, TranslationKey.QUEST_SUBTITLE).orElse("").equals(subtitle)
                && translations.getStringListTranslation(quest, locale, TranslationKey.QUEST_DESC)
                        .orElse(List.of()).equals(List.of(description))) {
            return false;
        }
        translations.addTranslation(quest, locale, TranslationKey.QUEST_SUBTITLE, subtitle);
        translations.addTranslation(quest, locale, TranslationKey.QUEST_DESC, List.of(description));
        return true;
    }

    private record Delivery(Item item, int count) {}

    private record Order(String chinese, String english, List<Delivery> deliveries) {}
}
