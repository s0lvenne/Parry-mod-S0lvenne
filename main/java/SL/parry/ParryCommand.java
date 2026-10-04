package SL.parry;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

public class ParryCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("parry")
                .requires(source -> source.hasPermission(2));
        root.then(disableNode(true));
        root.then(disableNode(false));
        root.then(cooldownNode());
        root.then(windowNode());
        root.then(attemptsNode());
        root.then(resetNode());
        root.then(statusNode());
        root.then(listNode());
        root.then(helpNode());
        dispatcher.register(root);
    }

    private static SuggestionProvider<CommandSourceStack> suggest(String... values) {
        return (ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.asList(values), builder);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> disableNode(boolean disable) {
        return Commands.literal(disable ? "disable" : "enable")
                .then(Commands.argument("target", EntityArgument.players())
                        .executes(ctx -> setDisabled(ctx, disable)));
    }

    private static int setDisabled(CommandContext<CommandSourceStack> ctx, boolean disable) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "target");
        for (ServerPlayer player : targets) {
            UUID uuid = player.getUUID();
            if (disable) {
                ParryData.disabled.add(uuid);
                ParryData.parryWindow.remove(uuid);
            } else {
                ParryData.disabled.remove(uuid);
            }
            ParrySyncPacket.sync(player);
        }
        ParryData.save();
        ctx.getSource().sendSuccess(() -> Component.literal("§a Updated " + targets.size() + " players!"), true);
        return targets.size();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> cooldownNode() {
        return Commands.literal("cooldown")
                .then(Commands.argument("target", EntityArgument.players())
                        .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0)).suggests(suggest("0", "2", "5", "15", "30"))
                                .executes(ctx -> {
                                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "target");
                                    double seconds = DoubleArgumentType.getDouble(ctx, "seconds");
                                    for (ServerPlayer player : targets) {
                                        UUID uuid = player.getUUID();
                                        if (seconds <= 0) {
                                            ParryData.playerCooldowns.remove(uuid);
                                            player.sendSystemMessage(Component.literal("§a Cooldown: GLOBAL (" + ParryData.getCooldown(uuid) + "t)"));
                                        } else {
                                            ParryData.playerCooldowns.put(uuid, (int) (seconds * 20));
                                            player.sendSystemMessage(Component.literal("§a Cooldown set: " + seconds + " sec"));
                                        }
                                        ParryData.activeCooldowns.remove(uuid);
                                        ParrySyncPacket.sync(player);
                                    }
                                    ParryData.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal("§a Done!"), true);
                                    return targets.size();
                                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> windowNode() {
        return Commands.literal("window")

                .then(Commands.argument("target", EntityArgument.players())
                        .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0)).suggests(suggest("0", "1", "2", "3"))
                                .executes(ctx -> {
                                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "target");
                                    double seconds = DoubleArgumentType.getDouble(ctx, "seconds");
                                    for (ServerPlayer player : targets) {
                                        UUID uuid = player.getUUID();
                                        if (seconds <= 0) {
                                            ParryData.playerWindows.remove(uuid);
                                            player.sendSystemMessage(Component.literal("§a Window: GLOBAL (" + ParryData.getWindow(uuid) + "t)"));
                                        } else {
                                            ParryData.playerWindows.put(uuid, (int) (seconds * 20));
                                            player.sendSystemMessage(Component.literal("§a Window set: " + seconds + " sec"));
                                        }
                                        ParrySyncPacket.sync(player);
                                    }
                                    ParryData.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal("§a Done!"), true);
                                    return targets.size();
                                })))

                .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0.1)).suggests(suggest("1", "2", "3"))
                        .executes(ctx -> {
                            double seconds = DoubleArgumentType.getDouble(ctx, "seconds");
                            ParryData.defaultWindow = (int) (seconds * 20);
                            ParryData.save();
                            ctx.getSource().sendSuccess(() -> Component.literal("§a Global window set: " + seconds + " sec"), true);
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> attemptsNode() {
        return Commands.literal("attempts")
                .then(Commands.argument("target", EntityArgument.players())
                        .then(Commands.argument("count", IntegerArgumentType.integer(0)).suggests(suggest("0", "1", "3", "5", "10"))
                                .executes(ctx -> {
                                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "target");
                                    int count = IntegerArgumentType.getInteger(ctx, "count");
                                    for (ServerPlayer player : targets) {
                                        UUID uuid = player.getUUID();
                                        ParryData.attemptsMax.put(uuid, count);
                                        ParryData.setAttemptsLeft(uuid, count);
                                        player.sendSystemMessage(Component.literal(count <= 0 ? "§a Attempts: INFINITE" : "§a Attempts: " + count));
                                        ParrySyncPacket.sync(player);
                                    }
                                    ParryData.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal("§a Done!"), true);
                                    return targets.size();
                                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> resetNode() {
        return Commands.literal("reset")
                .then(Commands.argument("target", EntityArgument.players())
                        .executes(ctx -> {
                            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "target");
                            for (ServerPlayer player : targets) {
                                UUID uuid = player.getUUID();
                                ParryData.activeCooldowns.remove(uuid);
                                ParryData.parryWindow.remove(uuid);
                                ParryData.refillAttempts(uuid);
                                player.sendSystemMessage(Component.literal("§a Cooldown RESET!"));
                                ParrySyncPacket.sync(player);
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("§a Reset " + targets.size() + " players!"), true);
                            return targets.size();
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> statusNode() {
        return Commands.literal("status")
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                            UUID uuid = target.getUUID();
                            String state = ParryData.hasAbility(uuid) ? "§a ENABLED" : "§c DISABLED";
                            int cd = ParryData.getRemainingCooldown(uuid);
                            String cdText = cd < 0 ? "§4 INFINITE" : (cd > 0 ? "§e " + cd + "t" : "§a READY");
                            int left = ParryData.getAttemptsLeft(uuid);
                            String attemptsText = left < 0 ? "§b INF" : "§b " + left + "/" + ParryData.getMaxAttempts(uuid);
                            ctx.getSource().sendSuccess(() -> Component.literal(target.getName().getString()
                                    + " | " + state
                                    + " | cd " + cdText + " (" + ParryData.getCooldown(uuid) + "t)"
                                    + " | attempts " + attemptsText
                                    + " | window §d" + ParryData.getWindow(uuid) + "t"
                                    + " | parried §6" + ParryData.getParryCount(uuid)), true);
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> listNode() {
        return Commands.literal("list")
                .executes(ctx -> {
                    int count = ParryData.disabled.size();
                    ctx.getSource().sendSuccess(() -> Component.literal("§c Disabled players: " + count), true);
                    for (UUID uuid : ParryData.disabled) {
                        ctx.getSource().sendSuccess(() -> Component.literal("  - " + uuid), false);
                    }
                    return count;
                });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> helpNode() {
        return Commands.literal("help")
                .executes(ctx -> {
                    CommandSourceStack s = ctx.getSource();
                    s.sendSuccess(() -> Component.literal("§b/parry disable|enable <игроки> §7- выключить/включить парри"), false);
                    s.sendSuccess(() -> Component.literal("§b/parry cooldown <игроки> <сек> §7- личный кулдаун (0 = глобальный)"), false);
                    s.sendSuccess(() -> Component.literal("§b/parry window <игроки> <сек> §7- личное окно (0 = глобальное)"), false);
                    s.sendSuccess(() -> Component.literal("§b/parry window <сек> §7- глобальное окно"), false);
                    s.sendSuccess(() -> Component.literal("§b/parry attempts <игроки> <n> §7- попытки до полного кулдауна (0 = бесконечно)"), false);
                    s.sendSuccess(() -> Component.literal("§b/parry reset <игроки> §7- сброс кулдауна и попыток"), false);
                    s.sendSuccess(() -> Component.literal("§b/parry status <игрок> §7- состояние | §b/parry list §7- отключённые"), false);
                    return 1;
                });
    }
}
