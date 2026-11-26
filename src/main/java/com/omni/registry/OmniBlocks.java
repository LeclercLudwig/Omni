package com.omni.registry;

import com.omni.Omni;
import com.omni.salvage.DiagnosticBenchBlock;
import com.omni.salvage.FuelLineBlock;
import com.omni.salvage.ReactorCoreBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Blocs des épaves. La coque se découpe au chalumeau plasma (tag omni:wreck_blocks) : sans lui,
 * le minage est extrêmement lent et ne donne rien.
 */
public final class OmniBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Omni.MODID);

    private static BlockBehaviour.Properties hull(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(15.0F, 12.0F)
                .requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK);
    }

    public static final DeferredBlock<Block> WRECK_HULL = BLOCKS.registerSimpleBlock("wreck_hull", hull(MapColor.COLOR_GRAY));
    public static final DeferredBlock<Block> SCORCHED_HULL = BLOCKS.registerSimpleBlock("scorched_hull", hull(MapColor.COLOR_BLACK));

    public static final DeferredBlock<FuelLineBlock> FUEL_LINE = BLOCKS.registerBlock("fuel_line", FuelLineBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(3.0F, 1.0F).sound(SoundType.COPPER));

    public static final DeferredBlock<ReactorCoreBlock> REACTOR_CORE = BLOCKS.registerBlock("reactor_core", ReactorCoreBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(5.0F, 1.0F)
                    .lightLevel(state -> 12).sound(SoundType.METAL));

    public static final DeferredBlock<Block> STABLE_REACTOR_CORE = BLOCKS.registerSimpleBlock("stable_reactor_core",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(5.0F, 6.0F)
                    .lightLevel(state -> 4).sound(SoundType.METAL));

    public static final DeferredBlock<Block> SALVAGE_CRATE = BLOCKS.registerSimpleBlock("salvage_crate",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(2.5F, 3.0F).sound(SoundType.METAL));

    public static final DeferredBlock<DiagnosticBenchBlock> DIAGNOSTIC_BENCH = BLOCKS.registerBlock("diagnostic_bench",
            DiagnosticBenchBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 6.0F).requiresCorrectToolForDrops()
                    .lightLevel(state -> 6).sound(SoundType.METAL));

    private OmniBlocks() {}
}
