package fr.tropimon.stocksmanager;

import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.util.math.BlockPos;

/** This mod's canonical identity for either half of a double chest. */
final class ChestPositions {
    private ChestPositions() { }

    static BlockPos canonical(BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ChestBlock) || state.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE) {
            return pos;
        }
        BlockPos other = pos.offset(ChestBlock.getFacing(state));
        if (other.getX() < pos.getX()
                || other.getX() == pos.getX() && other.getY() < pos.getY()
                || other.getX() == pos.getX() && other.getY() == pos.getY() && other.getZ() < pos.getZ()) {
            return other;
        }
        return pos;
    }
}
