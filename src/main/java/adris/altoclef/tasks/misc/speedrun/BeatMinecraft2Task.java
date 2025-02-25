package adris.altoclef.tasks.misc.speedrun;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.DoToClosestBlockTask;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.misc.EquipArmorTask;
import adris.altoclef.tasks.misc.PlaceBedAndSetSpawnTask;
import adris.altoclef.tasks.movement.DefaultGoToDimensionTask;
import adris.altoclef.tasks.movement.GetCloseToBlockTask;
import adris.altoclef.tasks.movement.GetToBlockTask;
import adris.altoclef.tasks.movement.PickupDroppedItemTask;
import adris.altoclef.tasks.movement.TimeoutWanderTask;
import adris.altoclef.tasks.resources.CollectFoodTask;
import adris.altoclef.tasks.resources.GetBuildingMaterialsTask;
import adris.altoclef.tasks.slot.ThrowSlotTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.helpers.ItemHelper;
import adris.altoclef.util.slots.Slot;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.EndPortalFrameBlock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

public class BeatMinecraft2Task extends Task {

    private static final Block[] TRACK_BLOCKS = new Block[] {
            Blocks.END_PORTAL_FRAME,
            Blocks.END_PORTAL,
            Blocks.CRAFTING_TABLE // For pearl trading + gold crafting
    };

    private static final int FOOD_UNITS = 250;
    private static final int MIN_FOOD_UNITS = 50;
    private static final int MIN_BUILD_MATERIALS = 5;
    private static final int BUILD_MATERIALS = 40;

    private static final ItemTarget[] COLLECT_EYE_GEAR = combine(
            toItemTargets(ItemHelper.DIAMOND_ARMORS),
            toItemTargets(Items.GOLDEN_BOOTS),
            toItemTargets(Items.DIAMOND_SWORD),
            toItemTargets(Items.DIAMOND_PICKAXE, 3),
            toItemTargets(Items.CRAFTING_TABLE));
    private static final ItemTarget[] COLLECT_EYE_GEAR_MIN = combine(
            toItemTargets(ItemHelper.DIAMOND_ARMORS),
            toItemTargets(Items.GOLDEN_BOOTS),
            toItemTargets(Items.DIAMOND_SWORD),
            toItemTargets(Items.DIAMOND_PICKAXE, 1));
    private static final ItemTarget[] IRON_GEAR = combine(
            toItemTargets(Items.WATER_BUCKET),
            toItemTargets(Items.IRON_SWORD),
            toItemTargets(Items.IRON_PICKAXE, 3));
    private static final ItemTarget[] IRON_GEAR_MIN = combine(
            toItemTargets(Items.IRON_SWORD));

    private static final int END_PORTAL_FRAME_COUNT = 12;
    private static final double END_PORTAL_BED_SPAWN_RANGE = 8;

    private final boolean _shouldSetSpawnNearEndPortal;
    private final int _targetEyesMin;
    private final int _targetEyes;
    private final int _bedsToCollect;

    private BlockPos _endPortalCenterLocation;
    private boolean _ranStrongholdLocator;
    private boolean _endPortalOpened;
    private boolean collectFood = false;
    private BlockPos _bedSpawnLocation;

    private int _cachedFilledPortalFrames = 0;

    private final HashMap<Item, Integer> _cachedEndItemDrops = new HashMap<>();
    private Task _getBedTask = null;
    private Task _gearTask;
    private final Task _buildMaterialsTask = new GetBuildingMaterialsTask(BUILD_MATERIALS);
    private final PlaceBedAndSetSpawnTask _setBedSpawnTask = new PlaceBedAndSetSpawnTask();
    private final Task _locateStrongholdTask;
    private final Task _goToNetherTask = new DefaultGoToDimensionTask(Dimension.NETHER); // To keep the portal build
                                                                                         // cache.
    private boolean _collectingEyes;

    public BeatMinecraft2Task(boolean setSpawnNearEndPortal, int targetEnderEyesMin, int targetEnderEyes,
            int bedsToCollect) {
        _shouldSetSpawnNearEndPortal = setSpawnNearEndPortal;
        _targetEyesMin = targetEnderEyesMin;
        _targetEyes = targetEnderEyes;
        _bedsToCollect = bedsToCollect;
        _locateStrongholdTask = new LocateStrongholdTask(_targetEyes);
    }

    @Override
    protected void onStart(AltoClef mod) {

        // Add a warning to make sure the user at least knows to change the settings.
        String settingsWarningTail = "in \".minecraft/altoclef_settings.json\". @gamer may break if you don't add this! (sorry!)";
        if (!ArrayUtils.contains(mod.getModSettings().getThrowawayItems(mod), Items.END_STONE)) {
            Debug.logWarning("\"end_stone\" is not part of your \"throwawayItems\" list " + settingsWarningTail);
        }
        if (!mod.getModSettings().shouldThrowawayUnusedItems()) {
            Debug.logWarning("\"throwawayUnusedItems\" is not set to true " + settingsWarningTail);
        }
        // TODO: add throwaway items like this: mod.getBehaviour().addThrowawayItems();
        /*  Throwaway items to prevent bot from throwing away important stuff */

        ArrayList<Item> garbageItems = new ArrayList<>();

        // sand and sandstone
        garbageItems.add(Items.GRAVEL);
        garbageItems.add(Items.SAND);
        garbageItems.add(Items.SANDSTONE);
        garbageItems.add(Items.SANDSTONE_SLAB);
        garbageItems.add(Items.SANDSTONE_STAIRS);
        garbageItems.add(Items.SANDSTONE_WALL);
        garbageItems.add(Items.CUT_SANDSTONE);
        garbageItems.add(Items.CUT_SANDSTONE_SLAB);
        garbageItems.add(Items.RED_SAND);
        garbageItems.add(Items.RED_SAND);
        garbageItems.add(Items.RED_SANDSTONE);
        garbageItems.add(Items.RED_SANDSTONE_SLAB);
        garbageItems.add(Items.RED_SANDSTONE_STAIRS);
        garbageItems.add(Items.RED_SANDSTONE_WALL);

        //mob drops

        garbageItems.add(Items.ROTTEN_FLESH);
        garbageItems.add(Items.SPIDER_EYE);
        garbageItems.add(Items.FERMENTED_SPIDER_EYE);
        garbageItems.add(Items.STRING);
        garbageItems.add(Items.BONE);
        garbageItems.add(Items.BONE_MEAL);
        garbageItems.add(Items.ARROW);
        garbageItems.add(Items.TIPPED_ARROW);
        garbageItems.add(Items.SPECTRAL_ARROW);
        garbageItems.add(Items.GUNPOWDER);
        garbageItems.add(Items.GHAST_TEAR);

        // plants
        garbageItems.add(Items.WHEAT_SEEDS);
        garbageItems.add(Items.PUMPKIN_SEEDS);
        garbageItems.add(Items.MELON_SEEDS);
        garbageItems.add(Items.BEETROOT_SEEDS);
        garbageItems.add(Items.NETHER_WART);
        garbageItems.add(Items.SUGAR_CANE);
        garbageItems.add(Items.SUGAR);
        garbageItems.add(Items.OAK_SAPLING);
        garbageItems.add(Items.SPRUCE_SAPLING);
        garbageItems.add(Items.BIRCH_SAPLING);
        garbageItems.add(Items.JUNGLE_SAPLING);
        garbageItems.add(Items.ACACIA_SAPLING);
        garbageItems.add(Items.DARK_OAK_SAPLING);

        // add them to the throwaway items2
        for (Item item : garbageItems) {
            mod.getBehaviour().addThrowawayItems(item);
        }

        // mod.getBehaviour().addThrowawayItems(Items.GRAVEL);
        // mod.getBehaviour().addThrowawayItems(Items.SAND);
        // mod.getBehaviour().addThrowawayItems(Items.SANDSTONE);
        // mod.getBehaviour().addThrowawayItems(Items.CUT_SANDSTONE);
        // mod.getBehaviour().addThrowawayItems(Items.SANDSTO);

        mod.getBlockTracker().trackBlock(TRACK_BLOCKS);
        mod.getBlockTracker().trackBlock(ItemHelper.itemsToBlocks(ItemHelper.BED));
        mod.getBehaviour().push();
        mod.getBehaviour().addProtectedItems(Items.ENDER_EYE, Items.BLAZE_ROD, Items.ENDER_PEARL);
        mod.getBehaviour().addProtectedItems(ItemHelper.BED);
        mod.getBehaviour().addProtectedItems(Items.FLINT_AND_STEEL);
        // protect wool for beds
        mod.getBehaviour().addProtectedItems(ItemHelper.WOOL);
        // Allow walking on end portal
        mod.getBehaviour().allowWalkingOn(blockPos -> mod.getChunkTracker().isChunkLoaded(blockPos)
                && mod.getWorld().getBlockState(blockPos).getBlock() == Blocks.END_PORTAL);
    }

    @Override
    protected Task onTick(AltoClef mod) {
        /*
        if in the overworld:
          if end portal found:
            if end portal opened:
              @make sure we have iron gear and enough beds to kill the dragon first, considering whether that gear was dropped in the end
              @enter end portal
            else if we have enough eyes of ender:
              @fill in the end portal
          else if we have enough eyes of ender:
            @locate the end portal
          else:
            if we don't have diamond gear:
              if we have no food:
                @get a little bit of food
              @get diamond gear
            @go to the nether
        if in the nether:
          if we don't have enough blaze rods:
            @kill blazes till we do
          else if we don't have enough pearls:
            @barter with piglins till we do
          else:
            @leave the nether
        if in the end:
          if we have a bed:
            @do bed strats
          else:
            @just hit the dragon normally
         */

        // End stuff.
        if (mod.getCurrentDimension() == Dimension.END) {
            // If we have bed, do bed strats, otherwise punk normally.
            updateCachedEndItems(mod);
            if (mod.getInventoryTracker().hasItem(ItemHelper.BED)) {
                setDebugState("Bed strats");
                return new KillEnderDragonWithBedsTask(new WaitForDragonAndPearlTask());
            }
            setDebugState("No beds, regular strats.");
            return new KillEnderDragonTask();
        }

        // Check for end portals. Always.
        if (!endPortalOpened(mod, _endPortalCenterLocation) && mod.getCurrentDimension() == Dimension.OVERWORLD) {
            BlockPos endPortal = mod.getBlockTracker().getNearestTracking(Blocks.END_PORTAL);
            if (endPortal != null) {
                _endPortalCenterLocation = endPortal;
                _endPortalOpened = true;
            } else {
                // TODO: Test that this works, for some reason the bot gets stuck near the stronghold and it keeps "Searching" for the portal
                _endPortalCenterLocation = doSimpleSearchForEndPortal(mod);
            }
        }

        // Do we need more eyes?
        boolean noEyesPlease = (endPortalOpened(mod, _endPortalCenterLocation)
                || mod.getCurrentDimension() == Dimension.END);
        int filledPortalFrames = getFilledPortalFrames(mod, _endPortalCenterLocation);
        int eyesNeededMin = noEyesPlease ? 0 : _targetEyesMin - filledPortalFrames;
        int eyesNeeded = noEyesPlease ? 0 : _targetEyes - filledPortalFrames;
        int eyes = mod.getInventoryTracker().getItemCount(Items.ENDER_EYE);
        if (eyes < eyesNeededMin || (!_ranStrongholdLocator && _collectingEyes && eyes < eyesNeeded)) {
            _collectingEyes = true;
            return getEyesOfEnderTask(mod, eyesNeeded);
        } else {
            _collectingEyes = false;
        }

        // We have eyes. Locate our portal + enter.
        switch (mod.getCurrentDimension()) {
            case OVERWORLD -> {
                // TODO: if its dark sleep

                // If we found our end portal...
                if (endPortalFound(mod, _endPortalCenterLocation)) {
                    if (needsBeds(mod)) {
                        setDebugState("Getting beds before fight.");
                        return getBedTask(mod);
                    }
                    if (_shouldSetSpawnNearEndPortal) {
                        if (!spawnSetNearPortal(mod, _endPortalCenterLocation)) {
                            setDebugState("Setting spawn near end portal");
                            return setSpawnNearPortalTask(mod);
                        }
                    }
                    if (endPortalOpened(mod, _endPortalCenterLocation)) {
                        // Does our (current inventory) + (end dropped items inventory) satisfy (baserequirements)?
                        // If not, obtain (base requirements) - (end dropped items).
                        setDebugState("Getting equipment for End");
                        if (!hasItemOrDroppedInEnd(mod, Items.IRON_SWORD)
                                && !hasItemOrDroppedInEnd(mod, Items.DIAMOND_SWORD)) {
                            return TaskCatalogue.getItemTask(Items.IRON_SWORD, 1);
                        }
                        if (!hasItemOrDroppedInEnd(mod, Items.WATER_BUCKET)
                                && !hasItemOrDroppedInEnd(mod, Items.BUCKET)) {
                            return TaskCatalogue.getItemTask(Items.BUCKET, 1);
                        }
                        if (!hasItemOrDroppedInEnd(mod, Items.IRON_PICKAXE)
                                && !hasItemOrDroppedInEnd(mod, Items.DIAMOND_PICKAXE)) {
                            return TaskCatalogue.getItemTask(Items.DIAMOND_PICKAXE, 1);
                        }
                        if (needsBuildingMaterials(mod)) {
                            return _buildMaterialsTask;
                        }
                        // Get beds

                        // We're as ready as we'll ever be, hop into the portal!
                        setDebugState("Entering End");
                        return new DoToClosestBlockTask(
                                blockPos -> new GetToBlockTask(blockPos.up()),
                                Blocks.END_PORTAL);
                    } else {
                        // Open the portal! (we have enough eyes, do it)
                        setDebugState("Opening End Portal");
                        return new DoToClosestBlockTask(
                                blockPos -> new InteractWithBlockTask(Items.ENDER_EYE, blockPos),
                                blockPos -> !isEndPortalFrameFilled(mod, blockPos),
                                Blocks.END_PORTAL_FRAME);
                    }

                } else {

                    // Portal Location
                    setDebugState("Locating End Portal...");
                    _ranStrongholdLocator = true;
                    return _locateStrongholdTask;
                }
            }
            case NETHER -> {
                // Portal Location
                setDebugState("Locating End Portal...");
                if (needsBuildingMaterials(mod)) {
                    return _buildMaterialsTask;
                }
                return _locateStrongholdTask;
            }
        }

        return null;
    }

    private boolean needsBuildingMaterials(AltoClef mod) {
        return mod.getInventoryTracker().getBuildingMaterialCount() < MIN_BUILD_MATERIALS
                || shouldForce(mod, _buildMaterialsTask);
    }

    private void updateCachedEndItems(AltoClef mod) {
        _cachedEndItemDrops.clear();
        for (ItemEntity entity : mod.getEntityTracker().getDroppedItems()) {
            Item item = entity.getStack().getItem();
            int count = entity.getStack().getCount();
            _cachedEndItemDrops.put(item, _cachedEndItemDrops.getOrDefault(item, 0) + count);
        }
    }

    private int getEndCachedCount(Item item) {
        return _cachedEndItemDrops.getOrDefault(item, 0);
    }

    private boolean droppedInEnd(Item item) {
        return getEndCachedCount(item) > 0;
    }

    private boolean hasItemOrDroppedInEnd(AltoClef mod, Item item) {
        return mod.getInventoryTracker().hasItem(item) || droppedInEnd(item);
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.getBlockTracker().stopTracking(TRACK_BLOCKS);
        mod.getBlockTracker().stopTracking(ItemHelper.itemsToBlocks(ItemHelper.BED));
        mod.getBehaviour().pop();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof BeatMinecraft2Task;
    }

    @Override
    protected String toDebugString() {
        return "Beating the Game.";
    }

    private boolean endPortalFound(AltoClef mod, BlockPos endPortalCenter) {
        if (endPortalCenter == null) {
            return false;
        }
        if (endPortalOpened(mod, endPortalCenter)) {
            return true;
        }
        return getFrameBlocks(endPortalCenter).stream()
                .allMatch(frame -> mod.getBlockTracker().blockIsValid(frame, Blocks.END_PORTAL_FRAME));
    }

    private boolean endPortalOpened(AltoClef mod, BlockPos endPortalCenter) {
        return _endPortalOpened && endPortalCenter != null
                && mod.getBlockTracker().blockIsValid(endPortalCenter, Blocks.END_PORTAL);
    }

    private boolean spawnSetNearPortal(AltoClef mod, BlockPos endPortalCenter) {
        return _bedSpawnLocation != null
                && mod.getBlockTracker().blockIsValid(_bedSpawnLocation, ItemHelper.itemsToBlocks(ItemHelper.BED));
    }

    private int getFilledPortalFrames(AltoClef mod, BlockPos endPortalCenter) {
        // If we have our end portal, this doesn't matter.
        if (endPortalFound(mod, endPortalCenter)) {
            return END_PORTAL_FRAME_COUNT;
        }
        if (endPortalFound(mod, endPortalCenter)) {
            List<BlockPos> frameBlocks = getFrameBlocks(endPortalCenter);
            // If EVERY portal frame is loaded, consider updating our cached filled portal
            // count.
            if (frameBlocks.stream().allMatch(blockPos -> mod.getChunkTracker().isChunkLoaded(blockPos))) {
                _cachedFilledPortalFrames = frameBlocks.stream().reduce(0,
                        (count, blockPos) -> count + (isEndPortalFrameFilled(mod, blockPos) ? 1 : 0),
                        Integer::sum);
            }
            return _cachedFilledPortalFrames;
        }
        return 0;
    }

    private static List<BlockPos> getFrameBlocks(BlockPos endPortalCenter) {
        Vec3i[] frameOffsets = new Vec3i[] {
                new Vec3i(2, 0, 1),
                new Vec3i(2, 0, 0),
                new Vec3i(2, 0, -1),
                new Vec3i(-2, 0, 1),
                new Vec3i(-2, 0, 0),
                new Vec3i(-2, 0, -1),
                new Vec3i(1, 0, 2),
                new Vec3i(0, 0, 2),
                new Vec3i(-1, 0, 2),
                new Vec3i(1, 0, -2),
                new Vec3i(0, 0, -2),
                new Vec3i(-1, 0, -2)
        };
        return Arrays.stream(frameOffsets).map(endPortalCenter::add).toList();
    }

    private Task getEyesOfEnderTask(AltoClef mod, int targetEyes) {
        if (mod.getEntityTracker().itemDropped(Items.ENDER_EYE)) {
            setDebugState("Picking up Dropped Eyes");
            return new PickupDroppedItemTask(Items.ENDER_EYE, targetEyes);
        }

        int eyeCount = mod.getInventoryTracker().getItemCount(Items.ENDER_EYE);

        int blazePowderCount = mod.getInventoryTracker().getItemCount(Items.BLAZE_POWDER);
        int blazeRodCount = mod.getInventoryTracker().getItemCount(Items.BLAZE_ROD);
        int blazeRodTarget = (int) Math.ceil(((double) targetEyes - eyeCount - blazePowderCount) / 2.0);
        int enderPearlTarget = targetEyes - eyeCount;
        boolean needsBlazeRods = blazeRodCount < blazeRodTarget;
        boolean needsBlazePowder = eyeCount + blazePowderCount < targetEyes;
        boolean needsEnderPearls = mod.getInventoryTracker().getItemCount(Items.ENDER_PEARL) < enderPearlTarget;

        if (needsBlazePowder && !needsBlazeRods) {
            // We have enough blaze rods.
            setDebugState("Crafting blaze powder");
            return TaskCatalogue.getItemTask(Items.BLAZE_POWDER, targetEyes - eyeCount);
        }

        if (!needsBlazePowder && !needsEnderPearls) {
            // Craft ender eyes
            setDebugState("Crafting Ender Eyes");
            return TaskCatalogue.getItemTask(Items.ENDER_EYE, targetEyes);
        }

        // Get blaze rods + pearls...
        switch (mod.getCurrentDimension()) {
            case OVERWORLD -> {

                // Make sure we have gear, then food.
                for (Item diamond : ItemHelper.DIAMOND_ARMORS) {
                    if (mod.getInventoryTracker().hasItem(diamond)
                            && !mod.getInventoryTracker().isArmorEquipped(diamond)) {
                        return new EquipArmorTask(ItemHelper.DIAMOND_ARMORS);
                    }
                }
                if (shouldForce(mod, _gearTask)) {
                    setDebugState("Getting gear for Ender Eye journey");
                    return _gearTask;
                }
                // if (shouldForce(mod, _foodTask)) {
                // Make a bucket for mlg safety.
                // if (mod.getInventoryTracker().getItemCount(Items.WATER_BUCKET) < 1) {
                // setDebugState("Getting safety bucket");
                // return TaskCatalogue.getItemTask(Items.WATER_BUCKET, 1);
                // }
                // }

                // Start with iron
                if (!mod.getInventoryTracker().targetsMet(IRON_GEAR_MIN)
                        && !mod.getInventoryTracker().targetsMet(COLLECT_EYE_GEAR_MIN)) {
                    _gearTask = TaskCatalogue.getSquashedItemTask(IRON_GEAR);
                    return _gearTask;
                }

                if (needsFood(mod)) {
                    return new CollectFoodTask(FOOD_UNITS);
                }

                // Then get food

                // Get beds after food.
                // if (needsBeds(mod)) {
                // setDebugState("Collecting beds.");
                // return getBedTask(mod);
                // }

                // Then get diamond
                if (!mod.getInventoryTracker().targetsMet(COLLECT_EYE_GEAR_MIN)) {
                    _gearTask = TaskCatalogue.getSquashedItemTask(COLLECT_EYE_GEAR);
                    return _gearTask;
                }
                // Then go to the nether.
                setDebugState("Going to Nether");
                if (!mod.getInventoryTracker().hasItem(Items.CRAFTING_TABLE)) {
                    setDebugState("Grab a crafting table first tho");
                    return TaskCatalogue.getItemTask(Items.CRAFTING_TABLE, 1);
                }
                return _goToNetherTask;
            }
            case NETHER -> {
                // TODO: if no slots are free throw away junk items

                /*
                if (mod.getInventoryTracker().getEmptyInventorySlotCount() < 1) {
                    setDebugState("Throwing away junk items");
                    Slot toThrow = mod.getInventoryTracker().getGarbageSlot();
                
                    return new ThrowSlotTask(toThrow); //TODO: fix this
                }
                
                if (needsFood(mod)) {
                    // go to overworld to collect food
                    // make shure we are not blocked by portal cooldown
                    if (mod.getPlayer().hasNetherPortalCooldown()) {
                        setDebugState("Waiting for portal cooldown");
                        return new TimeoutWanderTask();
                    }
                    setDebugState("Going to Overworld to get food");
                    return new DefaultGoToDimensionTask(Dimension.OVERWORLD);
                }
                //*/

                if (needsBlazeRods) {
                    setDebugState("Getting Blaze Rods");
                    return getBlazeRodsTask(mod, blazeRodTarget);
                }
                setDebugState("Getting Ender Pearls");
                return getEnderPearlTask(mod, enderPearlTarget);
            }
            case END -> throw new UnsupportedOperationException("You're in the end. Don't collect eyes here.");
        }
        return null;
    }

    private Task setSpawnNearPortalTask(AltoClef mod) {
        _bedSpawnLocation = null;
        if (_setBedSpawnTask.isSpawnSet()) {
            _bedSpawnLocation = _setBedSpawnTask.getBedSleptPos();
        }
        if (shouldForce(mod, _setBedSpawnTask)) {
            // Set spawnpoint and set our bed spawn when it happens.
            setDebugState("Setting spawnpoint now.");
            return _setBedSpawnTask;
        }
        // Get close to portal. If we're close enough, set our bed spawn somewhere nearby.
        if (_endPortalCenterLocation.isWithinDistance(mod.getPlayer().getPos(), END_PORTAL_BED_SPAWN_RANGE)) {
            return _setBedSpawnTask;
        } else {
            setDebugState("Approaching portal");
            return new GetCloseToBlockTask(_endPortalCenterLocation);
        }
    }

    private Task getBlazeRodsTask(AltoClef mod, int count) {
        return new CollectBlazeRodsTask(count);
    }

    private boolean needsFood(AltoClef mod) {
        int foodScore = mod.getInventoryTracker().totalFoodScore();
        if (foodScore < MIN_FOOD_UNITS) {
            collectFood = true;
        }
        if (collectFood && foodScore >= FOOD_UNITS) {
            collectFood = false;
        }
        return collectFood;
    }

    private Task getEnderPearlTask(AltoClef mod, int count) {
        // Equip golden boots before trading...
        if (!mod.getInventoryTracker().isArmorEquipped(Items.GOLDEN_BOOTS)) {
            return new EquipArmorTask(Items.GOLDEN_BOOTS);
        }
        int goldBuffer = 32;
        if (!mod.getInventoryTracker().hasItem(Items.CRAFTING_TABLE)
                && mod.getInventoryTracker().getItemCount(Items.GOLD_INGOT) >= goldBuffer
                && mod.getBlockTracker().anyFound(Blocks.CRAFTING_TABLE)) {
            setDebugState("Getting crafting table ");
            return TaskCatalogue.getItemTask(Items.CRAFTING_TABLE, 1);
        }
        return new TradeWithPiglinsTask(32, Items.ENDER_PEARL, count);
    }

    private int getTargetBeds(AltoClef mod) {
        boolean needsToSetSpawn = _shouldSetSpawnNearEndPortal &&
                (!spawnSetNearPortal(mod, _endPortalCenterLocation) && !shouldForce(mod, _setBedSpawnTask));
        // boolean needsToSetSpawn = _shouldSetSpawnNearEndPortal && _bedSpawnLocation
        // == null;
        return _bedsToCollect + (needsToSetSpawn ? 1 : 0);
    }

    private boolean needsBeds(AltoClef mod) {
        return mod.getInventoryTracker().getItemCount(ItemHelper.BED) < getTargetBeds(mod);
    }

    private Task getBedTask(AltoClef mod) {
        int targetBeds = getTargetBeds(mod);

        // Entity sheep = mod.getEntityTracker().getClosestEntity(SheepEntity.class);
        // if (sheep == null) {
        // setDebugState("No sheep found. Wandering around to hopefully find one.");
        // return new TimeoutWanderTask();
        // }

        // Collect beds. If we want to set our spawn, collect 1 more.
        // TODO: when we found beds that are too far away, we should ignore that
        setDebugState("Collecting " + targetBeds + " beds");
        if (!mod.getInventoryTracker().hasItem(Items.SHEARS) && !anyBedsFound(mod)) {
            return TaskCatalogue.getItemTask(Items.SHEARS, 1);
        }
        if (!shouldForce(mod, _getBedTask)) {
            // TODO: when there are no sheeps in render distance, find one
            _getBedTask = TaskCatalogue.getItemTask("bed", targetBeds);
        }

        return _getBedTask;

    }

    private boolean anyBedsFound(AltoClef mod) {
        return mod.getBlockTracker().anyFound(ItemHelper.itemsToBlocks(ItemHelper.BED));
    }

    private BlockPos doSimpleSearchForEndPortal(AltoClef mod) {
        List<BlockPos> frames = mod.getBlockTracker().getKnownLocations(Blocks.END_PORTAL_FRAME);
        if (frames.size() >= END_PORTAL_FRAME_COUNT) {
            // Get the center of the frames.
            Vec3d average = frames.stream()
                    .reduce(Vec3d.ZERO,
                            (accum, bpos) -> accum.add(bpos.getX() + 0.5, bpos.getY() + 0.5, bpos.getZ() + 0.5),
                            Vec3d::add)
                    .multiply(1.0f / frames.size());
            return new BlockPos(average.x, average.y, average.z);
        }
        return null;
    }

    private static boolean isEndPortalFrameFilled(AltoClef mod, BlockPos pos) {
        if (!mod.getChunkTracker().isChunkLoaded(pos))
            return false;
        BlockState state = mod.getWorld().getBlockState(pos);
        if (state.getBlock() != Blocks.END_PORTAL_FRAME) {
            Debug.logWarning("BLOCK POS " + pos
                    + " DOES NOT CONTAIN END PORTAL FRAME! This is probably due to a bug/incorrect assumption.");
            return false;
        }
        return state.get(EndPortalFrameBlock.EYE);
    }

    // Just a helpful utility to reduce reuse recycle.
    private static boolean shouldForce(AltoClef mod, Task task) {
        return task != null && task.isActive() && !task.isFinished(mod);
    }

    private static ItemTarget[] toItemTargets(Item... items) {
        return Arrays.stream(items).map(item -> new ItemTarget(item, 1)).toArray(ItemTarget[]::new);
    }

    private static ItemTarget[] toItemTargets(Item item, int count) {
        return new ItemTarget[] { new ItemTarget(item, count) };
    }

    private static ItemTarget[] combine(ItemTarget[]... targets) {
        List<ItemTarget> result = new ArrayList<>();
        for (ItemTarget[] ts : targets) {
            result.addAll(Arrays.asList(ts));
        }
        return result.toArray(ItemTarget[]::new);
    }
}
