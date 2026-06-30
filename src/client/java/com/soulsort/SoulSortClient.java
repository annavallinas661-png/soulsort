package com.soulsort;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Restore logic is server-side; this only warns the player when a multiplayer
 * server they joined seems to lack the mod, so it silently doing nothing isn't
 * a mystery. Singleplayer/LAN always have both sides, so the check is skipped.
 * The detection channel is registered by SoulSort (which runs here too), so
 * there's nothing to register here - doing so would double-register and crash.
 */
public class SoulSortClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (client.isLocalServer() || ClientPlayNetworking.canSend(SoulSort.HELLO_ID)) {
				return;
			}
			client.execute(() -> {
				if (client.player != null) {
					client.player.displayClientMessage(Component.literal(warning(client)), false);
				}
			});
		});
	}

	/** Pick the message in the client's own UI language (zh -> Chinese, otherwise English). */
	private static String warning(Minecraft client) {
		String lang = client.options.languageCode;
		boolean zh = lang != null && lang.toLowerCase(Locale.ROOT).startsWith("zh");
		return zh
			? "[SoulSort] 此服务器可能未安装 SoulSort，死亡归位不会生效。"
			: "[SoulSort] This server may not have SoulSort installed; death-restore won't work here.";
	}
}
