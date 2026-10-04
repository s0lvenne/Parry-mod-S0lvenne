package SL.parry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ParryData {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParryData.class);

    public static final Set<UUID> disabled = ConcurrentHashMap.newKeySet();

    public static final Map<UUID, Integer> playerCooldowns = new ConcurrentHashMap<>();

    public static final Map<UUID, Integer> playerWindows = new ConcurrentHashMap<>();

    public static final Map<UUID, Integer> attemptsMax = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> attemptsLeft = new ConcurrentHashMap<>();

    public static final Map<UUID, Integer> activeCooldowns = new ConcurrentHashMap<>();

    public static final Map<UUID, Integer> activeMax = new ConcurrentHashMap<>();

    public static final Set<UUID> fullCooldown = ConcurrentHashMap.newKeySet();

    public static final Map<UUID, Integer> parryWindow = new ConcurrentHashMap<>();

    public static final Map<UUID, Integer> flashSeq = new ConcurrentHashMap<>();

    public static final Map<UUID, Integer> parryCount = new ConcurrentHashMap<>();

    public static int defaultCooldown = 600;
    public static int defaultWindow = 20;
    public static int defaultAttempts = 1;

    public static int rechargeTicks = 40;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("parry-data.json");
    }

    public static void refreshConfigCache() {
        defaultCooldown = Config.DEFAULT_COOLDOWN.get();
        defaultWindow = Config.DEFAULT_WINDOW.get();
        defaultAttempts = Config.DEFAULT_ATTEMPTS.get();
        rechargeTicks = Config.RECHARGE_TICKS.get();
    }

    public static boolean hasAbility(UUID uuid) {
        return uuid != null && !disabled.contains(uuid);
    }

    public static int getCooldown(UUID uuid) {
        Integer o = playerCooldowns.get(uuid);
        if (o != null) return o;
        return defaultCooldown;
    }

    public static int getWindow(UUID uuid) {
        Integer o = playerWindows.get(uuid);
        if (o != null && o > 0) return o;
        return defaultWindow;
    }

    public static int getMaxAttempts(UUID uuid) {
        Integer a = attemptsMax.get(uuid);
        return a != null ? a : defaultAttempts;
    }

    public static int getAttemptsLeft(UUID uuid) {
        int max = getMaxAttempts(uuid);
        if (max <= 0) return -1;
        return attemptsLeft.getOrDefault(uuid, max);
    }

    public static void setAttemptsLeft(UUID uuid, int value) {
        if (uuid == null) return;
        attemptsLeft.put(uuid, Math.max(0, value));
    }

    public static void refillAttempts(UUID uuid) {
        int max = getMaxAttempts(uuid);
        if (max > 0) attemptsLeft.put(uuid, max);
    }

    public static void onParryUse(UUID uuid) {
        if (uuid == null) return;
        int max = getMaxAttempts(uuid);
        if (max > 0) {
            int left = attemptsLeft.getOrDefault(uuid, max) - 1;
            if (left <= 0) {
                attemptsLeft.put(uuid, 0);
                startCooldown(uuid);
            } else {
                attemptsLeft.put(uuid, left);
                startRecharge(uuid);
            }
        } else {
            startRecharge(uuid);
        }
    }

    private static void startRecharge(UUID uuid) {
        fullCooldown.remove(uuid);
        int v = Math.max(1, rechargeTicks);
        activeMax.put(uuid, v);
        activeCooldowns.put(uuid, v);
    }

    public static void startCooldown(UUID uuid) {
        if (uuid == null) return;
        int cooldown = getCooldown(uuid);
        if (cooldown <= 0) {
            activeCooldowns.put(uuid, -1);
        } else {
            fullCooldown.add(uuid);
            activeMax.put(uuid, cooldown);
            activeCooldowns.put(uuid, cooldown);
        }
    }

    public static int getActiveMax(UUID uuid) {
        Integer m = activeMax.get(uuid);
        if (m != null && m > 0) return m;
        return Math.max(1, getCooldown(uuid));
    }

    public static boolean isReady(UUID uuid) {
        if (uuid == null) return false;
        Integer cd = activeCooldowns.get(uuid);
        if (cd == null) return true;
        if (getCooldown(uuid) <= 0) return true;
        return cd <= 0;
    }

    public static void activateParry(UUID uuid, int windowTicks) {
        if (uuid == null) return;
        parryWindow.put(uuid, windowTicks);
    }

    public static boolean isParryActive(UUID uuid) {
        if (uuid == null) return false;
        Integer window = parryWindow.get(uuid);
        return window != null && window > 0;
    }

    public static void deactivateParry(UUID uuid) {
        if (uuid == null) return;
        parryWindow.remove(uuid);
    }

    public static int onParrySuccess(UUID uuid) {
        if (uuid == null) return -1;
        flashSeq.merge(uuid, 1, Integer::sum);
        parryCount.merge(uuid, 1, Integer::sum);
        return getAttemptsLeft(uuid);
    }

    public static int getParryCount(UUID uuid) {
        return uuid == null ? 0 : parryCount.getOrDefault(uuid, 0);
    }

    public static void tickCooldowns() {
        for (Map.Entry<UUID, Integer> entry : activeCooldowns.entrySet()) {
            UUID uuid = entry.getKey();
            Integer value = entry.getValue();
            if (value == null) {
                activeCooldowns.remove(uuid);
                continue;
            }
            if (value < 0) continue;
            int newVal = value - 1;
            if (newVal <= 0) {
                activeCooldowns.remove(uuid);
                if (fullCooldown.remove(uuid)) {
                    refillAttempts(uuid);
                }
            } else {
                activeCooldowns.put(uuid, newVal);
            }
        }

        for (Map.Entry<UUID, Integer> entry : parryWindow.entrySet()) {
            UUID uuid = entry.getKey();
            Integer value = entry.getValue();
            if (value == null) {
                parryWindow.remove(uuid);
                continue;
            }
            int newVal = value - 1;
            if (newVal <= 0) {
                parryWindow.remove(uuid);
            } else {
                parryWindow.put(uuid, newVal);
            }
        }
    }

    public static int getRemainingCooldown(UUID uuid) {
        if (uuid == null) return 0;
        Integer cd = activeCooldowns.get(uuid);
        if (cd == null) return 0;
        if (cd < 0) return -1;
        return cd;
    }

    public static int getParryWindow(UUID uuid) {
        if (uuid == null) return 0;
        Integer window = parryWindow.get(uuid);
        return window != null ? window : 0;
    }

    public static int meterFrame(int cooldownLeft, int cooldownMax, long tick) {
        if (cooldownLeft > 0 && cooldownMax > 0) {
            double progress = 1.0D - (double) cooldownLeft / (double) cooldownMax;
            return Math.max(0, Math.min(9, (int) (progress * 10)));
        }
        return 9;
    }

    public static synchronized void load() {
        Path file = file();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root != null) {
                    if (root.has("defaultCooldown")) defaultCooldown = root.get("defaultCooldown").getAsInt();
                    if (root.has("defaultWindow")) defaultWindow = root.get("defaultWindow").getAsInt();
                    if (root.has("defaultAttempts")) defaultAttempts = root.get("defaultAttempts").getAsInt();
                    disabled.clear();
                    playerCooldowns.clear();
                    playerWindows.clear();
                    attemptsMax.clear();
                    attemptsLeft.clear();
                    if (root.has("players") && root.get("players").isJsonObject()) {
                        for (Map.Entry<String, JsonElement> e : root.get("players").getAsJsonObject().entrySet()) {
                            UUID uuid;
                            try {
                                uuid = UUID.fromString(e.getKey());
                            } catch (IllegalArgumentException ignored) {
                                continue;
                            }
                            JsonElement v = e.getValue();
                            if (!v.isJsonObject()) continue;
                            JsonObject p = v.getAsJsonObject();
                            if (p.has("disabled") && p.get("disabled").getAsBoolean()) disabled.add(uuid);
                            if (p.has("cooldown") && p.get("cooldown").getAsInt() > 0) playerCooldowns.put(uuid, p.get("cooldown").getAsInt());
                            if (p.has("window") && p.get("window").getAsInt() > 0) playerWindows.put(uuid, p.get("window").getAsInt());
                            if (p.has("attempts")) attemptsMax.put(uuid, p.get("attempts").getAsInt());
                            if (p.has("left")) attemptsLeft.put(uuid, p.get("left").getAsInt());
                        }
                    }
                }
                LOGGER.info("Parry data loaded from {}", file);
            } catch (Exception ex) {
                LOGGER.error("Failed to load parry data from " + file, ex);
            }
        } else {
            refreshConfigCache();
            save();
        }
    }

    public static synchronized void save() {
        Path file = file();
        JsonObject root = new JsonObject();
        root.addProperty("defaultCooldown", defaultCooldown);
        root.addProperty("defaultWindow", defaultWindow);
        root.addProperty("defaultAttempts", defaultAttempts);
        JsonObject players = new JsonObject();
        Set<UUID> all = ConcurrentHashMap.newKeySet();
        all.addAll(disabled);
        all.addAll(playerCooldowns.keySet());
        all.addAll(playerWindows.keySet());
        all.addAll(attemptsMax.keySet());
        for (UUID uuid : all) {
            JsonObject p = new JsonObject();
            p.addProperty("disabled", disabled.contains(uuid));
            Integer c = playerCooldowns.get(uuid);
            p.addProperty("cooldown", c != null ? c : 0);
            Integer w = playerWindows.get(uuid);
            p.addProperty("window", w != null ? w : 0);
            Integer a = attemptsMax.get(uuid);
            p.addProperty("attempts", a != null ? a : defaultAttempts);
            p.addProperty("left", attemptsLeft.getOrDefault(uuid, a != null && a > 0 ? a : defaultAttempts));
            players.add(uuid.toString(), p);
        }
        root.add("players", players);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception ex) {
            LOGGER.error("Failed to save parry data to " + file, ex);
        }
    }
}
