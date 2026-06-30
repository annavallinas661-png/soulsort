package com.soulsort.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * NeoForge entrypoint. All restore logic is server-side and rides vanilla
 * classes, so RestoreManager is shared byte-for-byte with the Fabric/Forge
 * builds - only the event hooks differ. NeoForge uses the classic
 * NeoForge.EVENT_BUS.register(this) + @SubscribeEvent style for game events.
 */
@Mod(SoulSortMod.MODID)
public final class SoulSortMod {
	public static final String MODID = "soulsort";

	// FML injects these; the mod-bus param is required by the signature even though
	// we only use the game bus (NeoForge.EVENT_BUS) for our gameplay events.
	public SoulSortMod(IEventBus modEventBus, ModContainer modContainer) {
		NeoForge.EVENT_BUS.register(this);
	}

	@SubscribeEvent
	void onServerStarting(ServerStartingEvent event) {
		Settings.load();
	}

	// Fires before drops are generated, so the snapshot sees the full inventory.
	@SubscribeEvent
	void onDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			RestoreManager.capture(player);
		}
	}

	// Post = after the item was added to the inventory; that's when we re-slot it.
	@SubscribeEvent
	void onGroundPickup(ItemEntityPickupEvent.Post event) {
		if (event.getPlayer() instanceof ServerPlayer player) {
			RestoreManager.reconcile(player);
		}
	}

	// No per-click container event exists, so for grave/chest mods we poll while a
	// non-inventory menu is open and the player opted in. reconcile() no-ops when idle.
	@SubscribeEvent
	void onPlayerTick(PlayerTickEvent.Post event) {
		if (event.getEntity() instanceof ServerPlayer player
			&& player.containerMenu != player.inventoryMenu
			&& Settings.of(player.getUUID()).container) {
			RestoreManager.reconcile(player);
		}
	}

	@SubscribeEvent
	void onRegisterCommands(RegisterCommandsEvent event) {
		SoulSortCommand.register(event.getDispatcher());
	}
}
