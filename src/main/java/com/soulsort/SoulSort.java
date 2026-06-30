package com.soulsort;

import io.netty.buffer.ByteBuf;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public class SoulSort implements ModInitializer {
	public static final Identifier HELLO_ID = Identifier.fromNamespaceAndPath("soulsort", "hello");

	/** Empty payload; just registering its channel lets a client detect the mod via canSend(). */
	public record Hello() implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<Hello> TYPE = new CustomPacketPayload.Type<>(HELLO_ID);
		public static final StreamCodec<ByteBuf, Hello> CODEC = StreamCodec.unit(new Hello());

		@Override
		public CustomPacketPayload.Type<Hello> type() {
			return TYPE;
		}
	}

	@Override
	public void onInitialize() {
		Settings.load();
		// Runs in every environment, so register the detection channel exactly once here.
		PayloadTypeRegistry.playC2S().register(Hello.TYPE, Hello.CODEC);

		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player) {
				RestoreManager.capture(player);
			}
			return true;
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, ctx, sel) -> SoulSortCommand.register(dispatcher));
	}
}
