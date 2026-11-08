package com.tr4nce.rabbitcore.bridge;

import java.util.*;
import java.util.concurrent.*;

/** Optional public API boundary: RabbitCore still loads without RabbitAuth. */
public final class RabbitAuthIntegration {
    private RabbitAuthIntegration() {}
    @SuppressWarnings("unchecked")
    public static CompletionStage<Map<String, Object>> execute(BridgeAdmin.Request request, long deadline) {
        try {
            Class<?> api = Class.forName("com.tr4nce.rabbitauth.api.RabbitAuthAPI", true, RabbitAuthIntegration.class.getClassLoader());
            Map<String, String> arguments = Map.of("action", request.action(), "name", request.name(),
                    "enabled", Boolean.toString(request.enabled()), "page", Integer.toString(request.page()),
                    "actor", "bridge:" + request.sourceId() + ":" + request.senderId(), "deadline", Long.toString(deadline));
            return (CompletionStage<Map<String, Object>>) api.getMethod("manageWhitelist", Map.class).invoke(null, arguments);
        } catch (ReflectiveOperationException | LinkageError exception) {
            return CompletableFuture.completedFuture(Map.of("ok", false, "code", "AUTH_UNAVAILABLE",
                    "message", "需要安装支持白名单管理 API 的 RabbitAuth"));
        }
    }
}
