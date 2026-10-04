package SL.parry;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ParryActivatePacket {
    public ParryActivatePacket() {}

    public ParryActivatePacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            if (!ParryData.hasAbility(player.getUUID())) return;

            if (!ParryData.isReady(player.getUUID())) {
                ParrySyncPacket.sync(player);
                return;
            }

            ParryData.activateParry(player.getUUID(), ParryData.getWindow(player.getUUID()));
            ParryData.onParryUse(player.getUUID());
            ParrySyncPacket.sync(player);
        });
        context.setPacketHandled(true);
    }
}
