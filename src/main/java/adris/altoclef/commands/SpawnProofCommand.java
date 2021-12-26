package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.commandsystem.Arg;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.CommandException;
import adris.altoclef.tasks.construction.SpawnProofAreaTask;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

public class SpawnProofCommand extends Command {
    public SpawnProofCommand() throws CommandException {
        super("spawnproof", "Spawns a proof of concept",
                new Arg(Integer.class, "radius", 5, 0, true)
        // new Arg(Block.class, "Block"),
        // new Arg(String.class, "Block", Blocks.TORCH, 1, true)

        );

    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        // mod.runUserTask(new PlaceBlockTask(mod.getPlayer().getBlockPos(),
        // Blocks.STONE, false, false), this::finish);

        int raduis = parser.get(Integer.class);
        // int raduis = 5;
        // String name = parser.get(String.class);
        Block block = Blocks.TORCH;
        // Block block = Blocks.OAK_BUTTON;

        mod.runUserTask(new SpawnProofAreaTask(raduis, block), this::finish);

    }

}
