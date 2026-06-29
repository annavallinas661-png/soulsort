package com.soulsort.mixin;

import com.soulsort.RestoreManager;
import com.soulsort.Settings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Catches clicks in any open menu - chest, anvil, or a grave block from another
 * mod. We don't care which moved an item into the player's inventory, just tidy
 * up after, gated on the player's opt-in container setting.
 */
@Mixin(AbstractContainerMenu.class)
public class AbstractContainerMenuMixin {
	@Inject(method = "clicked", at = @At("TAIL"))
	private void soulsort$afterClick(int slotId, int button, ContainerInput input, Player player, CallbackInfo ci) {
		if (player instanceof ServerPlayer sp && Settings.of(sp.getUUID()).container) {
			RestoreManager.reconcile(sp);
		}
	}
}
