/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2022 Crypto Morin
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.cryptomorin.xseries;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * This class is currently unused until I find a solution.
 */
@SuppressWarnings("unused")
final class SkullCacheListener {
    protected static final Map<UUID, String> CACHE = new HashMap<>();
    private static final String SESSION = "https://sessionserver.mojang.com/session/minecraft/profile/";

    @Nonnull
    public static SkullMeta applyCachedSkin(@Nonnull ItemMeta head, @Nonnull UUID identifier) {
        String base64 = SkullCacheListener.CACHE.get(identifier);
        SkullMeta meta = (SkullMeta) head;
        return SkullUtils.getSkullByValue(meta, base64);
    }

    /**
     * https://api.mojang.com/users/profiles/minecraft/Username gives the ID
     * https://api.mojang.com/user/profiles/ID without dashes/names gives the names used for the unique ID.
     * https://sessionserver.mojang.com/session/minecraft/profile/ID example data:
     * <p>
     * <pre>
     * {
     *      "id": "Without dashes -",
     *      "name": "",
     *      "properties": [
     *      {
     *          "name": "textures",
     *          "value": ""
     *      }
     *      ]
     * }
     * </pre>
     */
    @Nullable
    public static String getSkinValue(@Nonnull String id) {
        Objects.requireNonNull(id, "Player UUID cannot be null");

        try {
            JsonParser parser = new JsonParser();
            URL properties = new URL(SESSION + id); // + "?unsigned=false"
            try (InputStreamReader readProperties = new InputStreamReader(properties.openStream())) {
                JsonObject jObjectP = parser.parse(readProperties).getAsJsonObject();

                if (mojangError(jObjectP)) return null;
                JsonObject textureProperty = jObjectP.get("properties").getAsJsonArray().get(0).getAsJsonObject();
                //String signature = textureProperty.get("signature").getAsString();
                return textureProperty.get("value").getAsString();
            }
        } catch (IOException | IllegalStateException e) {
            System.err.println("Could not get skin data from session servers! " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @Nullable
    public static String getIdFromUsername(@Nonnull String username) {
        if (username == null || username.isEmpty()) throw new IllegalArgumentException("Cannot get UUID of a null or empty username");
        int len = username.length();
        if (len < 3 || len > 16) throw new IllegalArgumentException("Username cannot be less than 3 and longer than 16 characters: " + username);

        try {
            URL convertName = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
            JsonParser parser = new JsonParser();

            try (InputStreamReader idReader = new InputStreamReader(convertName.openStream())) {
                JsonElement jElement = parser.parse(idReader);
                if (!jElement.isJsonObject()) return null;

                JsonObject jObject = jElement.getAsJsonObject();
                if (mojangError(jObject)) return null;
                return jObject.get("id").getAsString();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static boolean mojangError(@Nonnull JsonObject jsonObject) {
        if (!jsonObject.has("error")) return false;

        String err = jsonObject.get("error").getAsString();
        String msg = jsonObject.get("errorMessage").getAsString();
        System.err.println("Mojang Error " + err + ": " + msg);
        return true;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        GameProfile profile = new GameProfile(player.getUniqueId(), player.getName());
        ItemStack head = XMaterial.PLAYER_HEAD.parseItem();
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        try {
            SkullUtils.PROFILE_SETTER.invoke(meta, profile);
        } catch (Throwable ex) {
            ex.printStackTrace();
        }
        head.setItemMeta(meta);

        // If you don't add it to the players inventory, it won't be cached. That's the problem.
        // Or is the inventory cached? I tested this with multiple inventories and other inventories load immediately after an inventory with
        // the skull in it is opened once.
        player.getInventory().addItem(head);
    }
}
