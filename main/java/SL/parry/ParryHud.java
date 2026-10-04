package SL.parry;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Parry.MOD_ID, value = Dist.CLIENT)
public class ParryHud {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParryHud.class);

    public static int cooldownLeft = 0;
    public static int cooldownMax = 1;
    public static int windowLeft = 0;
    public static int flashSeq = 0;
    public static boolean enabled = true;

    private static int lastFlash = -1;
    private static int flashTicks = 0;
    private static final int FLASH_DURATION = 2;

    private static int hudX = -1;
    private static int hudY = -1;
    private static double hudScale = 1.0D;
    private static boolean positioned = false;
    private static boolean loaded = false;
    private static boolean editMode = false;
    private static int lastMx = -1;
    private static int lastMy = -1;

    private static final Gson GSON = new Gson();
    private static final ResourceLocation[] FRAMES = new ResourceLocation[10];
    private static final ResourceLocation BW_EFFECT = new ResourceLocation(Parry.MOD_ID, "shaders/post/parry_bw.json");

    static {
        for (int i = 0; i < 10; i++) {
            FRAMES[i] = new ResourceLocation(Parry.MOD_ID, "textures/gui/meter_" + i + ".png");
        }
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("parry-hud.json");
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path f = file();
        try {
            if (Files.exists(f)) {
                try (Reader r = Files.newBufferedReader(f)) {
                    JsonObject o = GSON.fromJson(r, JsonObject.class);
                    if (o != null) {
                        if (o.has("x")) hudX = o.get("x").getAsInt();
                        if (o.has("y")) hudY = o.get("y").getAsInt();
                        if (o.has("scale")) hudScale = Math.min(4.0D, Math.max(0.5D, o.get("scale").getAsDouble()));
                        positioned = o.has("x") && o.has("y");
                    }
                }
            }
        } catch (Exception ex) {
            LOGGER.error("Failed to load parry-hud.json", ex);
        }
    }

    private static void saveHud() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("x", hudX);
            o.addProperty("y", hudY);
            o.addProperty("scale", hudScale);
            Path f = file();
            Files.createDirectories(f.getParent());
            try (Writer w = Files.newBufferedWriter(f)) {
                GSON.toJson(o, w);
            }
        } catch (Exception ex) {
            LOGGER.error("Failed to save parry-hud.json", ex);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (cooldownLeft > 0) cooldownLeft--;
        if (windowLeft > 0) windowLeft--;

        boolean editDown = Parry.hudEditKey != null && Parry.hudEditKey.isDown();
        if (editDown && !editMode) {
            editMode = true;

            int size = (int) Math.round(16 * hudScale);
            hudX = mc.getWindow().getGuiScaledWidth() / 2 - size / 2;
            hudY = mc.getWindow().getGuiScaledHeight() / 2 - size / 2;
            double gs = Math.max(1.0D, mc.getWindow().getGuiScale());
            lastMx = (int) (mc.mouseHandler.xpos() / gs);
            lastMy = (int) (mc.mouseHandler.ypos() / gs);
        } else if (!editDown && editMode) {
            editMode = false;
            positioned = true;
            saveHud();
        } else if (editMode) {

            double gs = Math.max(1.0D, mc.getWindow().getGuiScale());
            int mx = (int) (mc.mouseHandler.xpos() / gs);
            int my = (int) (mc.mouseHandler.ypos() / gs);
            hudX += mx - lastMx;
            hudY += my - lastMy;
            lastMx = mx;
            lastMy = my;
        }

        if (flashSeq != lastFlash) {

            lastFlash = flashSeq;
            flashTicks = FLASH_DURATION;
            mc.gameRenderer.loadEffect(BW_EFFECT);
            if (mc.player != null) {
                mc.player.swing(InteractionHand.MAIN_HAND);
            }
        } else if (flashTicks > 0) {
            flashTicks--;
            if (flashTicks == 0) {
                mc.gameRenderer.shutdownEffect();
            }
        }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!editMode) return;
        double step = event.getScrollDelta() > 0 ? 0.25D : -0.25D;
        hudScale = Math.min(4.0D, Math.max(0.5D, hudScale + step));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ensureLoaded();

        int size = (int) Math.round(16 * hudScale);
        int x;
        int y;
        if (editMode || positioned) {
            x = hudX;
            y = hudY;
        } else {

            x = event.getWindow().getGuiScaledWidth() / 2 + 96;
            y = event.getWindow().getGuiScaledHeight() - 21;
        }

        if (enabled || editMode) {
            GuiGraphics graphics = event.getGuiGraphics();
            int frame = ParryData.meterFrame(cooldownLeft, cooldownMax, mc.player.tickCount);

            graphics.blit(FRAMES[frame], x, y, size, size, 0f, 0f, 16, 16, 16, 16);
            if (editMode) {
                graphics.drawCenteredString(mc.font,
                        Component.literal("§eHUD edit: move = mouse, size = wheel, release M = save §7(" + String.format("%.2f", hudScale) + "x)"),
                        event.getWindow().getGuiScaledWidth() / 2, 8, 0xFFFFFF);
            }
        }
    }
}
