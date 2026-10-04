package SL.parry;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Parry.MOD_ID)
public class Parry {
    public static final String MOD_ID = "parry";
    public static final Logger LOGGER = LoggerFactory.getLogger(Parry.class);
    public static KeyMapping parryKey;
    public static KeyMapping hudEditKey;
    private static boolean parryWasDown;
    private static long lastPressMs = 0;
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel PACKET_HANDLER = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public Parry() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC, MOD_ID + "-common.toml");

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerKeyMappings);
        modEventBus.addListener(this::onConfigLoading);
        modEventBus.addListener(this::onConfigReloading);

        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("Parry Mod Initialized!");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            PACKET_HANDLER.messageBuilder(ParryActivatePacket.class, 0)
                    .encoder(ParryActivatePacket::encode)
                    .decoder(ParryActivatePacket::new)
                    .consumerMainThread(ParryActivatePacket::handle)
                    .add();
            PACKET_HANDLER.messageBuilder(ParrySyncPacket.class, 1)
                    .encoder(ParrySyncPacket::encode)
                    .decoder(ParrySyncPacket::new)
                    .consumerMainThread(ParrySyncPacket::handle)
                    .add();
        });
        LOGGER.info("Network Packets Registered!");
    }

    private void registerKeyMappings(final RegisterKeyMappingsEvent event) {
        parryKey = new KeyMapping("key.parry.activate", GLFW.GLFW_KEY_R, "key.categories.parry");
        hudEditKey = new KeyMapping("key.parry.hudedit", GLFW.GLFW_KEY_M, "key.categories.parry");
        event.register(parryKey);
        event.register(hudEditKey);
        LOGGER.info("Keybinds registered: R (parry), M (HUD edit)");
    }

    private void onConfigLoading(final ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == Config.SPEC) ParryData.refreshConfigCache();
    }

    private void onConfigReloading(final ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == Config.SPEC) ParryData.refreshConfigCache();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ParryCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ParryData.load();
        LOGGER.info("Parry data loaded: {} disabled players", ParryData.disabled.size());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ParryData.save();
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
            ParrySyncPacket.sync(sp);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;

        boolean down = parryKey != null && parryKey.isDown();
        if (down && !parryWasDown) {
            long now = System.currentTimeMillis();
            if (now - lastPressMs >= 250) {
                lastPressMs = now;
                PACKET_HANDLER.sendToServer(new ParryActivatePacket());
            }
        }
        parryWasDown = down;
    }
}
