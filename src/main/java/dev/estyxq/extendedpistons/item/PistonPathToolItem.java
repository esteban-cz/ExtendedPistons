package dev.estyxq.extendedpistons.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

public final class PistonPathToolItem extends Item {
    public PistonPathToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // Editing is intercepted only when the virtual endpoint itself is raycast. Returning
        // PASS here prevents clicking the physical base from bypassing that reach check.
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.extendedpistons.path_tool.aim")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.extendedpistons.path_tool.add")
                .withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("tooltip.extendedpistons.path_tool.remove")
                .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.extendedpistons.path_tool.requirements")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
