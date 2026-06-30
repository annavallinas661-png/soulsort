package com.soulsort.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Remembers where a player's items were at death and slots them back as they're
 * picked up. Only ONE death is ever being restored at a time: when a pickup
 * first matches a waiting death, that death is "claimed" as active and any other
 * un-recovered deaths are dropped (their items still come back, just wherever
 * vanilla puts them). The next death in line only gets a chance once the active
 * one is fully restored. This keeps the logic simple and avoids deaths fighting
 * over the same slot - which is what made earlier versions degrade after a few
 * deaths.
 */
public final class RestoreManager {
	private static final int MAIN_SIZE = 36;
	private static final int OFFHAND = 40;
	private static final int SLOT_COUNT = 41;
	private static final int MAX_WAITING = 20;

	private static final class Death {
		final Map<Integer, ItemStack> pending = new LinkedHashMap<>();
		final Set<Integer> resolved = new HashSet<>();
		final long expireAt;

		Death(long expireAt) {
			this.expireAt = expireAt;
		}
	}

	private static final class State {
		final Deque<Death> waiting = new ArrayDeque<>();
		Death active;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private RestoreManager() {
	}

	public static void capture(ServerPlayer player) {
		Settings s = Settings.of(player.getUUID());
		if (!s.enabled) {
			return;
		}
		Death death = new Death(player.level().getGameTime() + s.expireTicks());
		Inventory inv = player.getInventory();
		for (int i = 0; i < MAIN_SIZE; i++) {
			record(death, s, i, inv.getItem(i));
		}
		if (s.armor) {
			for (int slot = MAIN_SIZE; slot < OFFHAND; slot++) {
				record(death, s, slot, player.getItemBySlot(equip(slot)));
			}
		}
		if (s.offhand) {
			record(death, s, OFFHAND, player.getItemBySlot(equip(OFFHAND)));
		}
		if (death.pending.isEmpty()) {
			return;
		}
		State state = STATES.computeIfAbsent(player.getUUID(), k -> new State());
		state.waiting.addLast(death);
		while (state.waiting.size() > MAX_WAITING) {
			state.waiting.removeFirst();
		}
	}

	public static void reconcile(ServerPlayer player) {
		State state = STATES.get(player.getUUID());
		if (state == null) {
			return;
		}
		long now = player.level().getGameTime();
		state.waiting.removeIf(d -> now > d.expireAt);
		if (state.active != null && now > state.active.expireAt) {
			state.active = null;
		}
		if (state.active == null) {
			state.active = claim(player, state);
		}
		if (state.active == null) {
			if (state.waiting.isEmpty()) {
				STATES.remove(player.getUUID());
			}
			return;
		}
		boolean changed = true;
		while (changed) {
			changed = resolvePlaced(player, state.active);
			changed |= resolveSearched(player, state.active);
		}
		if (state.active.pending.isEmpty()) {
			state.active = null;
			if (state.waiting.isEmpty()) {
				STATES.remove(player.getUUID());
			}
		}
	}

	private static void record(Death death, Settings s, int slot, ItemStack stack) {
		if (!stack.isEmpty() && !s.isExcluded(stack)) {
			death.pending.put(slot, stack.copy());
		}
	}

	/** Pick the oldest waiting death that has at least one item present now, and drop the rest. */
	private static Death claim(ServerPlayer player, State state) {
		for (Death candidate : state.waiting) {
			for (ItemStack target : candidate.pending.values()) {
				if (findSource(player, target, -1, candidate.resolved) >= 0) {
					state.waiting.clear();
					return candidate;
				}
			}
		}
		return null;
	}

	private static boolean resolvePlaced(ServerPlayer player, Death death) {
		boolean changed = false;
		for (int slot : new ArrayList<>(death.pending.keySet())) {
			if (matches(read(player, slot), death.pending.get(slot))) {
				death.pending.remove(slot);
				death.resolved.add(slot);
				changed = true;
			}
		}
		return changed;
	}

	private static boolean resolveSearched(ServerPlayer player, Death death) {
		boolean changed = false;
		for (int slot : new ArrayList<>(death.pending.keySet())) {
			int source = findSource(player, death.pending.get(slot), slot, death.resolved);
			if (source < 0) {
				continue;
			}
			ItemStack blocking = read(player, slot);
			if (!blocking.isEmpty()) {
				int free = freeSlot(player, death.pending.keySet());
				if (free < 0) {
					continue;
				}
				write(player, free, blocking.copy());
			}
			write(player, slot, read(player, source).copy());
			write(player, source, ItemStack.EMPTY);
			death.resolved.add(slot);
			death.pending.remove(slot);
			changed = true;
		}
		return changed;
	}

	private static int findSource(ServerPlayer player, ItemStack target, int exclude, Set<Integer> resolved) {
		for (int slot = 0; slot < SLOT_COUNT; slot++) {
			if (slot != exclude && !resolved.contains(slot) && matches(read(player, slot), target)) {
				return slot;
			}
		}
		return -1;
	}

	private static int freeSlot(ServerPlayer player, Set<Integer> reserved) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < MAIN_SIZE; i++) {
			if (!reserved.contains(i) && inv.getItem(i).isEmpty()) {
				return i;
			}
		}
		return -1;
	}

	private static boolean matches(ItemStack a, ItemStack b) {
		return !a.isEmpty() && !b.isEmpty() && ItemStack.isSameItemSameComponents(a, b);
	}

	private static ItemStack read(ServerPlayer player, int slot) {
		return slot < MAIN_SIZE ? player.getInventory().getItem(slot) : player.getItemBySlot(equip(slot));
	}

	private static void write(ServerPlayer player, int slot, ItemStack stack) {
		if (slot < MAIN_SIZE) {
			player.getInventory().setItem(slot, stack);
		} else {
			player.setItemSlot(equip(slot), stack);
		}
	}

	private static EquipmentSlot equip(int slot) {
		return switch (slot) {
			case 36 -> EquipmentSlot.HEAD;
			case 37 -> EquipmentSlot.CHEST;
			case 38 -> EquipmentSlot.LEGS;
			case 39 -> EquipmentSlot.FEET;
			default -> EquipmentSlot.OFFHAND;
		};
	}
}
