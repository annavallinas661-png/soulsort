package com.soulsort.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** All /soulsort subcommands. Any player may run them; the server config decides what actually sticks. */
public final class SoulSortCommand {
	private SoulSortCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("soulsort")
			.then(Commands.literal("on").executes(c -> toggle(c.getSource(), "mod", true)))
			.then(Commands.literal("off").executes(c -> toggle(c.getSource(), "mod", false)))
			.then(Commands.literal("armor")
				.then(Commands.literal("on").executes(c -> toggle(c.getSource(), "armor", true)))
				.then(Commands.literal("off").executes(c -> toggle(c.getSource(), "armor", false))))
			.then(Commands.literal("offhand")
				.then(Commands.literal("on").executes(c -> toggle(c.getSource(), "offhand", true)))
				.then(Commands.literal("off").executes(c -> toggle(c.getSource(), "offhand", false))))
			.then(Commands.literal("container")
				.then(Commands.literal("on").executes(c -> toggle(c.getSource(), "container", true)))
				.then(Commands.literal("off").executes(c -> toggle(c.getSource(), "container", false))))
			.then(Commands.literal("expire")
				.then(Commands.argument("seconds", IntegerArgumentType.integer(10))
					.executes(c -> setExpire(c.getSource(), IntegerArgumentType.getInteger(c, "seconds")))))
			.then(Commands.literal("list")
				.executes(c -> list(c.getSource()))
				.then(Commands.literal("on").then(Commands.literal("all").executes(c -> clear(c.getSource())))))
			.then(Commands.argument("items", StringArgumentType.string())
				.then(Commands.literal("on").executes(c -> items(c.getSource(), StringArgumentType.getString(c, "items"), false)))
				.then(Commands.literal("off").executes(c -> items(c.getSource(), StringArgumentType.getString(c, "items"), true)))));
	}

	private static int toggle(CommandSourceStack source, String feature, boolean value) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (Settings.locked(feature)) {
			return deny(source, player);
		}
		Settings s = Settings.of(player.getUUID());
		switch (feature) {
			case "mod" -> s.enabled = value;
			case "armor" -> s.armor = value;
			case "offhand" -> s.offhand = value;
			case "container" -> s.container = value;
		}
		Settings.savePlayers();
		String label = switch (feature) {
			case "mod" -> Settings.tr(player, "总开关", "SoulSort");
			case "armor" -> Settings.tr(player, "盔甲归位", "Armor restore");
			case "offhand" -> Settings.tr(player, "副手归位", "Off-hand restore");
			default -> Settings.tr(player, "容器取物归位", "Container restore");
		};
		source.sendSuccess(() -> Component.literal(label + ": " + onOff(value)), false);
		return 1;
	}

	private static int setExpire(CommandSourceStack source, int seconds) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Settings.of(player.getUUID()).expireSeconds = seconds;
		Settings.savePlayers();
		int cap = Settings.server().maxExpireSeconds;
		int eff = Math.min(seconds, cap);
		boolean capped = eff < seconds;
		source.sendSuccess(() -> Component.literal(Settings.tr(player,
			"记录保留 " + eff + " 秒" + (capped ? "（服务器上限 " + cap + "）" : ""),
			"Restore window: " + eff + "s" + (capped ? " (server cap " + cap + ")" : ""))), false);
		return 1;
	}

	private static int items(CommandSourceStack source, String raw, boolean exclude) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (Settings.locked("items")) {
			return deny(source, player);
		}
		Settings s = Settings.of(player.getUUID());
		List<String> bad = new ArrayList<>();
		int ok = 0;
		for (String token : raw.split(",")) {
			String t = token.trim();
			if (t.isEmpty()) {
				continue;
			}
			Identifier id = parseId(t);
			if (id == null || BuiltInRegistries.ITEM.getOptional(id).isEmpty()) {
				bad.add(t);
				continue;
			}
			if (exclude) {
				s.excluded.add(id.toString());
			} else {
				s.excluded.remove(id.toString());
			}
			ok++;
		}
		Settings.savePlayers();
		int count = ok;
		source.sendSuccess(() -> Component.literal(Settings.tr(player,
			"已更新 " + count + " 个物品: " + onOff(!exclude),
			"Updated " + count + " item(s): " + onOff(!exclude))), false);
		if (!bad.isEmpty()) {
			source.sendFailure(Component.literal(Settings.tr(player,
				"无效物品 id: " + String.join(", ", bad),
				"Invalid item id(s): " + String.join(", ", bad))));
		}
		return ok;
	}

	private static int clear(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (Settings.locked("items")) {
			return deny(source, player);
		}
		Settings.of(player.getUUID()).excluded.clear();
		Settings.savePlayers();
		source.sendSuccess(() -> Component.literal(Settings.tr(player,
			"已恢复所有物品的归位", "Re-enabled restore for all items")), false);
		return 1;
	}

	private static int list(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Settings s = Settings.of(player.getUUID());
		boolean zh = Settings.chinese(player);
		StringBuilder sb = new StringBuilder();
		sb.append("soulsort ").append(onOff(s.modEnabled())).append(lock(zh, "mod")).append('\n');
		sb.append("armor ").append(onOff(s.armorEnabled())).append(lock(zh, "armor")).append('\n');
		sb.append("offhand ").append(onOff(s.offhandEnabled())).append(lock(zh, "offhand")).append('\n');
		sb.append("container ").append(onOff(s.container)).append('\n');
		sb.append("expire ").append(s.expireTicks() / 20).append('s');
		Collection<String> items = Settings.locked("items") ? Settings.server().excludedItems : s.excluded;
		for (String id : items) {
			sb.append('\n').append(id).append(" off");
		}
		source.sendSuccess(() -> Component.literal(sb.toString()), false);
		return 1;
	}

	private static int deny(CommandSourceStack source, ServerPlayer player) {
		source.sendFailure(Component.literal(Settings.tr(player,
			Settings.server().deniedZh, Settings.server().deniedEn)));
		return 0;
	}

	private static String lock(boolean zh, String feature) {
		return Settings.locked(feature) ? (zh ? " (服务器强制)" : " (server-enforced)") : "";
	}

	// Identifier.withDefaultNamespace does NOT split on ':', so handle modded ids ourselves.
	private static Identifier parseId(String token) {
		try {
			int colon = token.indexOf(':');
			return colon < 0
				? Identifier.fromNamespaceAndPath("minecraft", token)
				: Identifier.fromNamespaceAndPath(token.substring(0, colon), token.substring(colon + 1));
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static String onOff(boolean value) {
		return value ? "on" : "off";
	}
}
