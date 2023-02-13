package com.ilmusu.ilmusuenchantments.enchantments;

import com.ilmusu.ilmusuenchantments.Resources;
import com.ilmusu.ilmusuenchantments.callbacks.PlayerAttackCallback;
import com.ilmusu.ilmusuenchantments.callbacks.PlayerTickCallback;
import com.ilmusu.ilmusuenchantments.callbacks.ProjectileLoadCallback;
import com.ilmusu.ilmusuenchantments.callbacks.ProjectileShotCallback;
import com.ilmusu.ilmusuenchantments.mixins.interfaces._IEnchantmentExtensions;
import com.ilmusu.ilmusuenchantments.registries.ModDamageSources;
import com.ilmusu.ilmusuenchantments.registries.ModEnchantments;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TridentItem;
import net.minecraft.text.Text;

public class OverchargeEnchantment extends Enchantment implements _IDemonicEnchantment, _IEnchantmentExtensions
{
    private static final String OVERCHARGE_DAMAGE_TAG = Resources.MOD_ID+".overcharge_damage";

    private static final int OVERCHARGE_START_TICK = 40;
    private static final int DELTA_TIME_DAMAGE = 10;

    public OverchargeEnchantment(Rarity weight)
    {
        super(weight, EnchantmentTarget.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    public Text getName(int level)
    {
        return _IDemonicEnchantment.super.getName(this.getTranslationKey(), level, this.getMaxLevel());
    }

    @Override
    public int getMaxLevel()
    {
        return 5;
    }

    @Override
    public boolean isAcceptableItem(ItemStack stack)
    {
        return  EnchantmentTarget.BOW.isAcceptableItem(stack.getItem()) ||
                EnchantmentTarget.CROSSBOW.isAcceptableItem(stack.getItem()) ||
                EnchantmentTarget.TRIDENT.isAcceptableItem(stack.getItem());
    }

    private static float getPullProgress(PlayerEntity player, ItemStack stack)
    {
        if(!player.isUsingItem())
            return -1;

        if(stack.getItem() instanceof BowItem)
            return BowItem.getPullProgress(player.getItemUseTime());
        if(stack.getItem() instanceof CrossbowItem)
            return CrossbowItem.getPullProgress(player.getItemUseTime(), stack);
        if(stack.getItem() instanceof TridentItem)
            return 1.0F;

        return -1;
    }

    private static int getPullTime(ItemStack stack)
    {
        if(stack.getItem() instanceof BowItem)
            return 20;
        if(stack.getItem() instanceof CrossbowItem)
            return CrossbowItem.getPullTime(stack);
        if(stack.getItem() instanceof TridentItem)
            return 0;
        return -1;
    }

    protected static float getOverchargeAdditionalDamage(LivingEntity player, ItemStack stack)
    {
        int timeSinceFullCharge = player.getItemUseTime() - OverchargeEnchantment.getPullTime(stack);
        int damagesApplied = timeSinceFullCharge/DELTA_TIME_DAMAGE;
        int level = EnchantmentHelper.getLevel(ModEnchantments.OVERCHARGED, stack);
        return damagesApplied*(0.3F + level*0.2F);
    }

    static
    {
        PlayerTickCallback.AFTER.register((player ->
        {
            if(player.world.isClient || !player.isUsingItem())
                return;

            // The item must be chargeable
            // Check if the enchantment is available
            ItemStack stack = player.getMainHandStack();
            int level = EnchantmentHelper.getLevel(ModEnchantments.OVERCHARGED, stack);
            if(level == 0)
                return;

            // If the bow is not fully charged, remove the tag just to be sure
            float progress = OverchargeEnchantment.getPullProgress(player, stack);
            if(progress < 1.0F)
                return;

            // Gets the time since the stack was fully charged
            // And checks if the damage needs to be applied
            int timeSinceFullCharge = player.getItemUseTime() - OverchargeEnchantment.getPullTime(stack);
            if(timeSinceFullCharge < OVERCHARGE_START_TICK || timeSinceFullCharge % DELTA_TIME_DAMAGE != 0)
                return;

            // Applying damage to the player
            player.damage(ModDamageSources.DEMONIC_DAMAGE, 1.0F);
        }));

        ProjectileLoadCallback.BEFORE.register((shooter, stack) ->
        {
            // The stack must have the enchantment
            int level = EnchantmentHelper.getLevel(ModEnchantments.OVERCHARGED, stack);
            if(level == 0)
                return;
            // Adding additional damage to the trident to the nbt
            float additionalDamage = OverchargeEnchantment.getOverchargeAdditionalDamage(shooter, stack);
            stack.getNbt().putFloat(OVERCHARGE_DAMAGE_TAG, additionalDamage);
        });

        ProjectileShotCallback.AFTER.register((shooter, stack, projectile) ->
        {
            if(!(projectile instanceof PersistentProjectileEntity projectile1))
                return;

            // The stack must have the enchantment
            int level = EnchantmentHelper.getLevel(ModEnchantments.OVERCHARGED, stack);
            if(level == 0)
                return;

            // If trident, the damage cannot be done here, storing it
            if(stack.getItem() instanceof TridentItem)
            {
                // Adding additional damage to the trident to the nbt
                float additionalDamage = OverchargeEnchantment.getOverchargeAdditionalDamage(shooter, stack);
                stack.getNbt().putFloat(OVERCHARGE_DAMAGE_TAG, additionalDamage);
                return;
            }

            // Computing the additional damage
            float additionalDamage = 0;
            if(stack.getItem() instanceof BowItem)
                additionalDamage = OverchargeEnchantment.getOverchargeAdditionalDamage(shooter, stack);
            else if(stack.getItem() instanceof CrossbowItem)
                additionalDamage = stack.getNbt().getFloat(OVERCHARGE_DAMAGE_TAG);

            // Adding damage to the arrow
            projectile1.setDamage(projectile1.getDamage() + additionalDamage);
        });
    }

    @Override
    public float getAdditionalAttackDamage(ItemStack stack, int level, EntityGroup group)
    {
        // The trident entity gets the damage from the enchantments
        if(stack.hasNbt())
            return stack.getNbt().getFloat(OVERCHARGE_DAMAGE_TAG);
        return 0.0F;
    }

    static
    {
        ProjectileShotCallback.AFTER_MULTIPLE.register((shooter, stack, projectile) ->
            // Remove the eventual tag of the crossbow after shooting all projectiles
            stack.removeSubNbt(OVERCHARGE_DAMAGE_TAG)
        );

        PlayerAttackCallback.AFTER_ENCHANTMENT_DAMAGE.register(((player, stack, entity, hand) ->
            // Removing the eventual tag of the trident after damage
            stack.removeSubNbt(OVERCHARGE_DAMAGE_TAG))
        );
    }
}
