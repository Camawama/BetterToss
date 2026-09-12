package net.camacraft.bettertoss.mixin;

import net.camacraft.bettertoss.client.ClientCatchState;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A caught projectile shows in its holder's main hand for everyone: the third-person held-item
 * layer is told the main hand holds the projectile's item instead of whatever is really there
 * (possibly nothing). Everything else about the layer - transforms, the off hand, other mods'
 * overrides - is untouched.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin
{
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack bettertoss$caughtProjectileInHand(LivingEntity entity)
    {
        ItemStack caught = ClientCatchState.display(entity);
        return caught.isEmpty() ? entity.getMainHandItem() : caught;
    }
}
