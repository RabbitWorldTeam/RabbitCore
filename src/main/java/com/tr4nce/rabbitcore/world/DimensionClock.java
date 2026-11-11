package com.tr4nce.rabbitcore.world;

import com.tr4nce.rabbitcore.world.storage.DimensionClockData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;

public interface DimensionClock {
    DimensionClockData rabbitcore$clock();

    void rabbitcore$setClock(DimensionClockData clock);

    static boolean cycles(ServerLevel level) {
        DimensionClockData clock = local(level);
        return clock == null ? level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT) : clock.cycles();
    }

    static void setCycles(ServerLevel level, boolean enabled) {
        DimensionClockData clock = local(level);
        if (clock == null) {
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(enabled, level.getServer());
        } else {
            clock.setCycles(enabled);
        }
    }

    private static DimensionClockData local(ServerLevel level) {
        return level.getLevelData() instanceof DimensionClock data ? data.rabbitcore$clock() : null;
    }
}
