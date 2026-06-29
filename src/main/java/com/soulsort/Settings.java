package com.soulsort;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything configurable, in one place. A player's live values come from
 * {@link #of}: normally their own saved preference, but a feature the server
 * has pinned (see {@link Server}) overrides it. Both the per-player prefs and
 * the server policy persist as JSON under the world's config/ folder.
 */
public final class Settings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<UUID, Settings> PLAYERS = new HashMap<>();
	private static Server server = new Server();
	private static Path playerFile;
	private static Path serverFile;

	// Fields are public so Gson maps them directly; values here are the per-player defaults.
	public boolean enabled = true;
	public boolean armor = true;
	public boolean offhand = true;
	public boolean container = true;
	public int expireSeconds = -1; // -1 = use the server default
	public Set<String> excluded = new LinkedHashSet<>();

	/** Server-wide policy file (config/soulsort.json). */
	public static final class Server {
		public boolean playerOverrideAllowed = true;
		public boolean lockMod = false;
		public boolean lockArmor = false;
		public boolean lockOffhand = false;
		public boolean lockItems = false;
		public boolean modEnabled = true;
		public boolean armorEnabled = true;
		public boolean offhandEnabled = true;
		public Set<String> excludedItems = new LinkedHashSet<>();
		public int defaultExpireSeconds = 300;
		public int maxExpireSeconds = 1800;
		public String deniedZh = "该服务器已禁止客户端调整此功能";
		public String deniedEn = "This server has disabled client-side control of this feature.";
	}

	private Settings() {
	}

	public static Settings of(UUID id) {
		return PLAYERS.computeIfAbsent(id, k -> new Settings());
	}

	public static Server server() {
		return server;
	}

	/** A locked feature follows the server; otherwise it's the player's own call. */
	public static boolean locked(String feature) {
		if (server.playerOverrideAllowed) {
			return false;
		}
		return switch (feature) {
			case "mod" -> server.lockMod;
			case "armor" -> server.lockArmor;
			case "offhand" -> server.lockOffhand;
			case "items" -> server.lockItems;
			default -> false;
		};
	}

	public boolean modEnabled() {
		return locked("mod") ? server.modEnabled : enabled;
	}

	public boolean armorEnabled() {
		return locked("armor") ? server.armorEnabled : armor;
	}

	public boolean offhandEnabled() {
		return locked("offhand") ? server.offhandEnabled : offhand;
	}

	public boolean isExcluded(ItemStack stack) {
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		return (locked("items") ? server.excludedItems : excluded).contains(id);
	}

	public long expireTicks() {
		int seconds = expireSeconds < 0 ? server.defaultExpireSeconds : Math.min(expireSeconds, server.maxExpireSeconds);
		return seconds * 20L;
	}

	public static boolean chinese(ServerPlayer player) {
		String lang = player.clientInformation().language();
		return lang != null && lang.toLowerCase(Locale.ROOT).startsWith("zh");
	}

	public static String tr(ServerPlayer player, String zh, String en) {
		return chinese(player) ? zh : en;
	}

	public static void load() {
		Path dir = FabricLoader.getInstance().getConfigDir();
		serverFile = dir.resolve("soulsort.json");
		playerFile = dir.resolve("soulsort_players.json");
		try {
			if (Files.exists(serverFile)) {
				Server s = GSON.fromJson(Files.readString(serverFile), Server.class);
				if (s != null) {
					server = s;
				}
			}
			if (Files.exists(playerFile)) {
				Map<UUID, Settings> loaded = GSON.fromJson(Files.readString(playerFile),
					new TypeToken<Map<UUID, Settings>>() {
					}.getType());
				if (loaded != null) {
					PLAYERS.putAll(loaded);
				}
			}
		} catch (IOException | RuntimeException ignored) {
			// fall back to defaults if a file is missing or corrupt
		}
		write(serverFile, server); // re-emit so a fresh server gets a documented file
	}

	public static void savePlayers() {
		write(playerFile, PLAYERS);
	}

	private static void write(Path file, Object value) {
		if (file == null) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(value));
		} catch (IOException ignored) {
			// best effort; in-memory state still works this session
		}
	}
}
