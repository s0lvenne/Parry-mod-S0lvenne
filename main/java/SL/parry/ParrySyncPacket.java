package SL.parry;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class ParrySyncPacket {
    private final int cooldownLeft;
    private final int cooldownMax;
    private final int windowLeft;
    private final int flashSeq;
    private final boolean enabled;

    public ParrySyncPacket(int cooldownLeft, int cooldownMax, int windowLeft, int flashSeq, boolean enabled) {
        this.cooldownLeft = cooldownLeft;
        this.cooldownMax = cooldownMax;
        this.windowLeft = windowLeft;
        this.flashSeq = flashSeq;
        this.enabled = enabled;
    }

    public ParrySyncPacket(FriendlyByteBuf buf) {
        this.cooldownLeft = buf.readInt();
        this.cooldownMax = buf.readInt();
        this.windowLeft = buf.readInt();
        this.flashSeq = buf.readInt();
        this.enabled = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(cooldownLeft);
        buf.writeInt(cooldownMax);
        buf.writeInt(windowLeft);
        buf.writeInt(flashSeq);
        buf.writeBoolean(enabled);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ParryHud.cooldownLeft = cooldownLeft;
            ParryHud.cooldownMax = Math.max(1, cooldownMax);
            ParryHud.windowLeft = windowLeft;
            ParryHud.flashSeq = flashSeq;
            ParryHud.enabled = enabled;
        });
        context.setPacketHandled(true);
    }

    public static void sync(ServerPlayer player) {
        UUID uuid = player.getUUID();
        PACKET_SEND(player, new ParrySyncPacket(
                ParryData.getRemainingCooldown(uuid),
                ParryData.getActiveMax(uuid),
                ParryData.getParryWindow(uuid),
                ParryData.flashSeq.getOrDefault(uuid, 0),
                ParryData.hasAbility(uuid)));
    }

    private static void PACKET_SEND(ServerPlayer player, ParrySyncPacket packet) {
        Parry.PACKET_HANDLER.sendTo(packet, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
}
