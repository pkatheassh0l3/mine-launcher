package com.hearthbound.network;

import com.hearthbound.Hearthbound;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Two small payloads carry everything: the client sends named actions with arguments,
 * the server answers with named messages. Data travels as NBT for easy evolution.
 */
public final class Net {
    private Net() {}

    public record Action(String action, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(Hearthbound.id("action"));
        public static final StreamCodec<ByteBuf, Action> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Action::action,
                ByteBufCodecs.COMPOUND_TAG, Action::data,
                Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Message(String kind, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Message> TYPE = new Type<>(Hearthbound.id("message"));
        public static final StreamCodec<ByteBuf, Message> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Message::kind,
                ByteBufCodecs.COMPOUND_TAG, Message::data,
                Message::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(Action.TYPE, Action.CODEC, Net::onAction);
        r.playToClient(Message.TYPE, Message.CODEC, Net::onMessage);
    }

    private static void onAction(Action a, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) ServerActions.handle(sp, a.action(), a.data());
        });
    }

    private static void onMessage(Message m, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.hearthbound.client.ClientNet.handle(m.kind(), m.data()));
    }

    // ------------------------------------------------------------------ server → client helpers

    public static void send(ServerPlayer p, String kind, CompoundTag data) {
        PacketDistributor.sendToPlayer(p, new Message(kind, data));
    }

    public static void notify(ServerPlayer p, Component text, int color) {
        CompoundTag t = new CompoundTag();
        t.putString("text", Component.Serializer.toJson(text, p.registryAccess()));
        t.putInt("color", color);
        send(p, "notify", t);
    }

    public static void banner(ServerPlayer p, Component title, Component subtitle, int color) {
        CompoundTag t = new CompoundTag();
        t.putString("title", Component.Serializer.toJson(title, p.registryAccess()));
        t.putString("sub", Component.Serializer.toJson(subtitle, p.registryAccess()));
        t.putInt("color", color);
        send(p, "banner", t);
    }

    // ------------------------------------------------------------------ client → server

    public static void action(String action, CompoundTag data) {
        PacketDistributor.sendToServer(new Action(action, data));
    }
}
