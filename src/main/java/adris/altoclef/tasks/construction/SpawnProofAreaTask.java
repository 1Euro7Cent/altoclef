package adris.altoclef.tasks.construction;

import java.util.ArrayList;

import adris.altoclef.AltoClef;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Material;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

public class SpawnProofAreaTask extends Task {

    private final int maxBlocks = 500;
    private final int puffer = 15;
    private final Item requiredPickaxe = Items.DIAMOND_PICKAXE;

    private int radius;
    private Block block;
    private Item item;
    private ArrayList<BlockPos> blocks = new ArrayList<BlockPos>();
    private BlockPos playerPos;

    private boolean done = false;

    private int x;
    private int y;
    private int z;
    private int ticks = 19;

    public SpawnProofAreaTask(int radius, Block block) {

        this.radius = radius;
        this.block = block;
        this.item = block.asItem();

    }

    @Override
    protected void onStart(AltoClef mod) {
        mod.getBehaviour().push();

        mod.getBehaviour().addProtectedItems(item);
        mod.getBehaviour().avoidBlockBreaking(blockPos -> {
            BlockState s = mod.getWorld().getBlockState(blockPos);
            return s.getBlock() == block;
        });

        x = mod.getPlayer().getBlockX();
        y = mod.getPlayer().getBlockY();
        z = mod.getPlayer().getBlockZ();
        playerPos = mod.getPlayer().getBlockPos();
        ticks = 19;

    }

    @Override
    protected Task onTick(AltoClef mod) {

        /*
         * this is still in development. but it works.
         * dont use that. i just dont want to remove that
         * 
         */
        ticks++;

        // check if the bot has required pickaxe
        if (!mod.getInventoryTracker().hasItem(requiredPickaxe)) {
            return TaskCatalogue.getItemTask(requiredPickaxe, 1);
        }

        int amountToPlace = (int) Math.floor(blocks.size() / 4);

        // get all blocks in radius
        ClientWorld world = mod.getWorld();
        if (ticks % 20 == 0) {
            playerPos = mod.getPlayer().getBlockPos();

            scanWorld(world);
            System.out.println("amount to place: " + amountToPlace);
        }

        // check if client has enough blocks
        // get all blocks of type block in inventory

        int invAmount = mod.getInventoryTracker().getItemCount(item);

        // System.out.println("invAmount: " + invAmount);

        if (blocks.size() == 0) {
            System.out.println("no blocks to place");
            done = true;
            return null;
        }
        int missing = amountToPlace - invAmount;

        if (invAmount < missing && missing < maxBlocks) {
            ItemTarget target = TaskCatalogue.getItemTarget(block.getName().getString().toLowerCase(),
                    missing > maxBlocks ? maxBlocks : missing + puffer);
            return TaskCatalogue.getItemTask(target);
        }

        // get nearest block
        BlockPos nearest = null;
        for (BlockPos position : blocks) {
            int distanceX = 0;
            int distanceY = 0;
            int distanceZ = 0;

            if (nearest == null) {
                nearest = position;
            } else {
                distanceX = Math.abs(position.getX() - playerPos.getX());
                distanceY = Math.abs(position.getY() - playerPos.getY());
                distanceZ = Math.abs(position.getZ() - playerPos.getZ());

                if (distanceX + distanceY + distanceZ < Math.abs(nearest.getX() - playerPos.getX())
                        + Math.abs(nearest.getZ() - playerPos.getZ())) {
                    nearest = position;
                }
            }
        }

        if (world.getBlockState(nearest).getBlock() == block) {
            scanWorld(world);
        }

        return new PlaceBlockTask(nearest, block);

    }

    private void scanWorld(ClientWorld world) {
        blocks.clear();
        // System.out.println("radius: " + radius);
        System.out.println("fetsching world data");

        // go through all blocks in radius
        for (int i = -radius; i <= radius; i++) {
            for (int j = -radius; j <= radius; j++) {
                for (int k = -radius; k <= radius; k++) {
                    // check if block is in radius
                    // if (i * i + j * j + k * k <= radius * radius) {
                    BlockPos pos = new BlockPos(x + i, y + j, z + k);
                    // check if block is air and block below in NOT air
                    Block cBlock = world.getBlockState(pos).getBlock();
                    // Block cBlockDown = world.getBlockState(pos.down()).getBlock();
                    // if (cBlock == Blocks.AIR && cBlockDown != Blocks.AIR && cBlockDown !=
                    // Blocks.WATER
                    // && cBlockDown != Blocks.LAVA) {
                    Material material = world.getBlockState(pos.down()).getMaterial();

                    // if (cBlock == Blocks.AIR && block.canPlaceAt(world.getBlockState(pos.down()),
                    // world, pos)) {
                    if (cBlock == Blocks.AIR && material.isSolid()) {
                        // check if block is too dark

                        if (world.getLightLevel(pos) <= 7) {
                            // put all positions in a list
                            blocks.add(pos);

                        }

                    }
                    // }
                }
            }
        }

    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.getBehaviour().pop();

    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return done;
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof SpawnProofAreaTask;
    }

    @Override
    protected String toDebugString() {
        // TODO Auto-generated method stub
        return null;
    }
}
