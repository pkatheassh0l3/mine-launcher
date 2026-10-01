package io.github.marcsanzdev.chestseparators.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Server-to-Client report of a completed auto-deposit. Lists one {@link Flight} per (item type,
 * destination container) pair so the client can animate the deposited items flying from the player
 * toward each chest. Carries an {@link ItemStack} so it must travel over a {@link RegistryFriendlyByteBuf}.
 */
public record AutoDepositResultPayload(List<Flight> flights, boolean reverse) implements CustomPacketPayload {

    /** One animated transfer: a representative stack (with the moved count) and its destination. */
    public record Flight(ItemStack stack, BlockPos target) {}

    /** Deposit flights (player -> chest). The grab path uses the two-arg form with reverse=true. */
    public AutoDepositResultPayload(List<Flight> flights) {
        this(flights, false);
    }

    public static final CustomPacketPayload.Type<AutoDepositResultPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("chestseparators", "auto_deposit_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AutoDepositResultPayload> CODEC =
            StreamCodec.ofMember(AutoDepositResultPayload::write, AutoDepositResultPayload::new);

    private AutoDepositResultPayload(RegistryFriendlyByteBuf buf) {
        this(readFlights(buf), buf.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.flights.size());
        for (Flight flight : this.flights) {
            ItemStack.STREAM_CODEC.encode(buf, flight.stack());
            buf.writeBlockPos(flight.target());
        }
        buf.writeBoolean(this.reverse);
    }

    private static List<Flight> readFlights(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Flight> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
            BlockPos target = buf.readBlockPos();
            list.add(new Flight(stack, target));
        }
        return list;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
