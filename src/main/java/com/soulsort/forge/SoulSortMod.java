package com.soulsort.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Forge entrypoint. All restore logic is server-side and rides the vanilla
 * classes, so it's identical to the Fabric build's RestoreManager - only the
 * way we hook events differs. Forge 65's EventBus exposes a static BUS per
 * event type; we add listeners to the game (Forge) bus for the three things we
 * care about: death (snapshot), ground pickup (restore), and container clicks
 * (restore, polled while a non-inventory menu is open).
 */
@Mod(SoulSortMod.MODID)
public final class SoulSortMod {
	public static final String MODID = "soulsort";

	public SoulSortMod(FMLJavaModLoadingContext context) {
		// Game-bus events use the static BUS field; no per-mod bus needed for these.
		LivingDeathEvent.BUS.addListener(this::onDeath);
		PlayerEvent.ItemPickupEvent.BUS.addListener(this::onGroundPickup);
		TickEvent.PlayerTickEvent.Post.BUS.addListener(this::onPlayerTick);
		RegisterCommandsEvent.BUS.addListener(this::onRegisterCommands);
		ServerStartingEvent.BUS.addListener(this::onServerStarting);
	}

	private void onServerStarting(ServerStartingEvent event) {
		Settings.load();
	}

	private void onDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			RestoreManager.capture(player);
		}
	}

	private void onGroundPickup(PlayerEvent.ItemPickupEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			RestoreManager.reconcile(player);
		}
	}

	/**
	 * Forge has no per-click container event, so for grave/chest mods we poll:
	 * while the player has a non-inventory menu open and opted in, try to tidy.
	 * reconcile() is a cheap no-op when nothing is pending.
	 */
	private void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
		if (event.player() instanceof ServerPlayer player
			&& player.containerMenu != player.inventoryMenu
			&& Settings.of(player.getUUID()).container) {
			RestoreManager.reconcile(player);
		}
	}

	private void onRegisterCommands(RegisterCommandsEvent event) {
		SoulSortCommand.register(event.getDispatcher());
	}
}
