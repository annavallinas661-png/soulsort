package com.soulsort.mixin;

import com.soulsort.RestoreManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hooked at TAIL so vanilla pickup (sound, stats, stacking) runs untouched - we just tidy up after. */
@Mixin(ItemEntity.class)
public class ItemEntityMixin {
	@Inject(method = "playerTouch", at = @At("TAIL"))
	private void soulsort$afterPickup(Player player, CallbackInfo ci) {
		if (player instanceof ServerPlayer serverPlayer) {
			RestoreManager.reconcile(serverPlayer);
		}
	}
}
