package SL.parry;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = Parry.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ParryEventHandler {
    private static int syncTimer = 0;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;
        if (player.level().isClientSide) return;

        UUID uuid = player.getUUID();
        if (ParryData.hasAbility(uuid) && ParryData.isParryActive(uuid)) {
            event.setCanceled(true);
            event.setAmount(0.0F);
            ParryData.onParrySuccess(uuid);
            ParryData.deactivateParry(uuid);

            if (event.getSource().getDirectEntity() instanceof Projectile projectile) {
                Vec3 incoming = projectile.getDeltaMovement();
                projectile.setOwner(player);
                if (projectile instanceof AbstractArrow arrow) {
                    arrow.setBaseDamage(arrow.getBaseDamage() * 2.0D);

                    var server = player.getServer();
                    if (server != null) {
                        server.execute(() -> {
                            if (projectile.isAlive()) {
                                projectile.setDeltaMovement(incoming.scale(-2.0D));
                                projectile.hasImpulse = true;
                            }
                        });
                    }
                } else if (projectile instanceof ThrowableItemProjectile tip) {

                    var level = player.level();
                    Entity copy = tip.getType().create(level);
                    if (copy instanceof ThrowableItemProjectile newTip) {
                        newTip.setPos(player.getX(), player.getEyeY() - 0.1D, player.getZ());
                        newTip.setItem(tip.getItem());
                        newTip.setDeltaMovement(incoming.scale(-2.0D));
                        newTip.setOwner(player);
                        newTip.hasImpulse = true;
                        level.addFreshEntity(newTip);
                    }
                }
            }

            player.swing(InteractionHand.MAIN_HAND);

            if (ParryData.getParryCount(uuid) % 10 == 0) {
                player.heal(8.0F);
            }

            if (Config.PARRY_FEEDBACK.get()) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.2F);
                if (player.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                            player.getX(), player.getY() + player.getEyeHeight() * 0.8D, player.getZ(),
                            8, 0.4D, 0.3D, 0.4D, 0.0D);
                }
            }

            if (player instanceof ServerPlayer sp) {
                ParrySyncPacket.sync(sp);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        ParryData.tickCooldowns();

        if (++syncTimer >= 20) {
            syncTimer = 0;
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                    UUID u = sp.getUUID();
                    if (ParryData.activeCooldowns.containsKey(u) || ParryData.parryWindow.containsKey(u)) {
                        ParrySyncPacket.sync(sp);
                    }
                }
            }
        }
    }
}
