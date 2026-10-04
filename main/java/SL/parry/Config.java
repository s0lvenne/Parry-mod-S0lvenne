package SL.parry;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "parry", bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue DEFAULT_COOLDOWN = BUILDER
            .comment("Кулдаун парри по умолчанию (в тиках, 20 тиков = 1 сек).")
            .comment("Персонально переопределяется командой /parry cooldown.")
            .defineInRange("defaultCooldownTicks", 600, 1, 72000);

    public static final ForgeConfigSpec.IntValue DEFAULT_WINDOW = BUILDER
            .comment("Окно активного парри по умолчанию (в тиках).")
            .comment("Персонально переопределяется командой /parry window.")
            .defineInRange("defaultWindowTicks", 20, 1, 72000);

    public static final ForgeConfigSpec.IntValue DEFAULT_ATTEMPTS = BUILDER
            .comment("Число попыток парри до полного кулдауна (режим джагернаута).")
            .comment("1 = полный кулдаун после каждого использования. 0 = бесконечно.")
            .defineInRange("defaultAttempts", 1, 0, 10000);

    public static final ForgeConfigSpec.IntValue RECHARGE_TICKS = BUILDER
            .comment("Перезарядка после каждого использования парри, пока есть попытки (в тиках).")
            .comment("После исчерпания попыток - полный кулдаун (DEFAULT_COOLDOWN).")
            .defineInRange("rechargeTicks", 40, 1, 72000);

    public static final ForgeConfigSpec.BooleanValue PARRY_FEEDBACK = BUILDER
            .comment("Проигрывать ли звук и частицы при успешном парри.")
            .define("parryFeedback", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();
}
