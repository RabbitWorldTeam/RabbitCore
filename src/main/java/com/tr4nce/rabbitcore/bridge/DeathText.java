package com.tr4nce.rabbitcore.bridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Objects;

/** Renders vanilla death components without changing the server's global language. */
final class DeathText {
    private static final JsonObject TRANSLATIONS = load();

    private DeathText() {}

    private static JsonObject load() {
        try (var stream = Objects.requireNonNull(DeathText.class.getResourceAsStream(
                "/assets/rabbitcore/lang/death_zh_cn.json"));
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    static String render(Component component) {
        var result = new StringBuilder();
        if (component.getContents() instanceof TranslatableContents translated
                && TRANSLATIONS.has(translated.getKey())) {
            Object[] args = translated.getArgs().clone();
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Component child) args[i] = render(child);
            }
            try {
                result.append(String.format(Locale.ROOT, TRANSLATIONS.get(translated.getKey()).getAsString(), args));
            } catch (IllegalFormatException e) {
                result.append(Component.translatable(translated.getKey(), translated.getArgs()).getString());
            }
        } else {
            result.append(component.plainCopy().getString());
        }
        for (Component sibling : component.getSiblings()) result.append(render(sibling));
        return result.toString();
    }
}
