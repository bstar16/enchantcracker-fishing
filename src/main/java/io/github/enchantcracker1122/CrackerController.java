package io.github.enchantcracker1122;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ContainerEnchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class CrackerController {
    private static final int UNKNOWN_MASK = Integer.MIN_VALUE;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Enchant Cracker 1.12.2");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService planner = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Enchant Cracker target search");
        thread.setDaemon(true);
        return thread;
    });
    private World sessionWorld;

    private int seedMask = UNKNOWN_MASK;
    private int[] candidateSeeds = new int[0];
    private int knownXpSeed;
    private boolean xpSeedKnown;
    private String lastObservationKey = "";
    private boolean filtering;
    private volatile FilterResult completedFilter;
    private String prompt = "Insert an enchantable item.";
    private int detectedPower;

    private CalibrationStage calibrationStage = CalibrationStage.IDLE;
    private int firstFreshXpSeed;
    private JavaRandom48 playerRng;

    private SearchJob searchJob;
    private ManipulationPlan activePlan;
    private DropQueue dropQueue;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (sessionWorld != mc.world) {
            sessionWorld = mc.world;
            resetAll();
        }
        if (mc.player == null || mc.world == null) {
            return;
        }

        applyWorkerResults();
        tickDropQueue();

        if (!(mc.player.openContainer instanceof ContainerEnchantment)) {
            return;
        }

        ContainerEnchantment container = (ContainerEnchantment) mc.player.openContainer;
        int currentMask = container.xpSeed & 0xfff0;
        if (seedMask == UNKNOWN_MASK) {
            beginSeed(currentMask);
        } else if (seedMask != currentMask) {
            onSeedChanged(currentMask);
        }

        if (xpSeedKnown || filtering) {
            return;
        }

        ItemStack stack = container.getSlot(0).getStack();
        if (stack.isEmpty() || !stack.isItemEnchantable()) {
            prompt = "Insert a different enchantable item.";
            return;
        }

        if (container.enchantLevels[0] == 0 && container.enchantLevels[1] == 0
            && container.enchantLevels[2] == 0) {
            return;
        }

        detectedPower = findEnchantPower(mc.world, mc.player.getPosition());
        Observation observation = new Observation(
            stack.copy(),
            detectedPower,
            container.enchantLevels.clone(),
            container.enchantClue.clone(),
            container.worldClue.clone());
        String key = observation.key();
        if (!key.equals(lastObservationKey)) {
            lastObservationKey = key;
            scheduleFilter(observation);
        } else {
            prompt = "Need another observation: swap item or bookshelf power.";
        }
    }

    private void applyWorkerResults() {
        FilterResult filter = completedFilter;
        if (filter != null) {
            completedFilter = null;
            filtering = false;
            if (filter.mask == seedMask) {
                candidateSeeds = filter.seeds;
                if (candidateSeeds.length == 1) {
                    knownXpSeed = candidateSeeds[0];
                    xpSeedKnown = true;
                    prompt = "XP seed cracked. Predictions are exact for 1.12.2.";
                    onXpSeedCracked(knownXpSeed);
                } else if (candidateSeeds.length == 0) {
                    prompt = "No candidates: verify bookshelf count and server version.";
                } else {
                    prompt = "Need another observation: swap item or bookshelf power.";
                }
            }
        }

        SearchJob job = searchJob;
        if (job != null && job.done) {
            searchJob = null;
            if (job.cancelled) {
                return;
            }
            if (job.error != null) {
                tell(TextFormatting.RED + "Search failed: " + job.error + ". Try again or reset.");
                return;
            }
            ManipulationPlan plan = job.result;
            if (playerRng == null || playerRng.getInternalSeed() != job.baseState) {
                tell(TextFormatting.RED + "Search discarded because the player RNG changed.");
                return;
            }
            activePlan = plan.found ? plan : null;
            if (!plan.found) {
                tell(TextFormatting.RED + "Full drop RNG cycle searched: no match for this drop-and-dummy path.");
            } else if (!plan.requiresDummy) {
                activePlan.readyForFinal = true;
                tell(TextFormatting.GREEN + "Current seed already matches. Use " + plan.bookshelves
                    + " shelves and slot " + (plan.slot + 1) + ".");
            } else {
                tell(TextFormatting.YELLOW + "Plan ready: " + plan.drops + " drops, then one cheap dummy enchant."
                    + " Select a disposable item; matching hotbar stacks will be used by /enchcracker execute.");
            }
        }
    }

    private void beginSeed(int mask) {
        seedMask = mask;
        candidateSeeds = createInitialCandidates(mask);
        xpSeedKnown = false;
        lastObservationKey = "";
        filtering = false;
        prompt = "Insert an enchantable item to observe offers.";
    }

    private void onSeedChanged(int newMask) {
        cancelSearch();
        if (dropQueue != null) {
            failManipulation("An enchant occurred before the queued drops completed.");
        }
        boolean finishingFinal = activePlan != null && activePlan.readyForFinal;
        seedMask = newMask;
        candidateSeeds = createInitialCandidates(newMask);
        xpSeedKnown = false;
        lastObservationKey = "";
        filtering = false;

        if (calibrationStage == CalibrationStage.WAIT_FIRST_ENCHANT) {
            calibrationStage = CalibrationStage.CAPTURE_FIRST;
            prompt = "Crack this fresh seed; then another cheap enchant is required.";
            return;
        }
        if (calibrationStage == CalibrationStage.WAIT_SECOND_ENCHANT) {
            calibrationStage = CalibrationStage.CAPTURE_SECOND;
            prompt = "Crack this second consecutive seed.";
            return;
        }

        if (playerRng != null) {
            int predicted = playerRng.nextInt();
            if ((predicted & 0xfff0) == newMask) {
                knownXpSeed = predicted;
                xpSeedKnown = true;
                candidateSeeds = new int[] {predicted};
                if (activePlan != null && activePlan.awaitingDummy) {
                    if (predicted == activePlan.xpSeed) {
                        activePlan.awaitingDummy = false;
                        activePlan.readyForFinal = true;
                        prompt = "Target ready: set shelves and take the shown slot.";
                        tell(TextFormatting.GREEN + "Target seed reached. Use " + activePlan.bookshelves
                            + " shelves and slot " + (activePlan.slot + 1) + ".");
                    } else {
                        failManipulation("Dummy enchant produced an unexpected XP seed.");
                    }
                } else {
                    prompt = "XP seed tracked from the cracked player RNG.";
                }
                if (finishingFinal) {
                    tell(TextFormatting.GREEN + "Final enchant completed; RNG tracking maintained.");
                    activePlan = null;
                }
                return;
            }
            playerRng = null;
            activePlan = null;
            prompt = "Player RNG desynced. Run /enchcracker rng again.";
            tell(TextFormatting.RED + "Player RNG desynced: an untracked random action occurred.");
            return;
        }

        prompt = "New enchantment seed. Insert an item to crack it.";
    }

    private void onXpSeedCracked(int seed) {
        if (calibrationStage == CalibrationStage.CAPTURE_FIRST) {
            firstFreshXpSeed = seed;
            calibrationStage = CalibrationStage.WAIT_SECOND_ENCHANT;
            prompt = "First fresh seed captured. Perform one more cheap enchant.";
            tell(TextFormatting.YELLOW + "First consecutive XP seed captured. Perform another cheap enchant without dropping items.");
        } else if (calibrationStage == CalibrationStage.CAPTURE_SECOND) {
            JavaRandom48 recovered = JavaRandom48.recoverFromTwoNextInts(firstFreshXpSeed, seed);
            calibrationStage = CalibrationStage.IDLE;
            if (recovered == null) {
                playerRng = null;
                prompt = "Player RNG recovery failed; an extra RNG call occurred.";
                tell(TextFormatting.RED + "Player RNG recovery failed. Reset and avoid unrelated actions between enchants.");
            } else {
                playerRng = recovered;
                prompt = "Player RNG cracked. /enchcracker target is available.";
                tell(TextFormatting.GREEN + "Player RNG cracked successfully.");
            }
        }
    }

    private void scheduleFilter(final Observation observation) {
        final int mask = seedMask;
        final int[] input = candidateSeeds;
        filtering = true;
        prompt = "Analyzing observation...";
        worker.submit(() -> {
            int[] kept = new int[input.length];
            int count = 0;
            for (int seed : input) {
                if (matches(seed, observation)) {
                    kept[count++] = seed;
                }
            }
            completedFilter = new FilterResult(mask, Arrays.copyOf(kept, count));
        });
    }

    private static boolean matches(int seed, Observation observation) {
        int[] costs = EnchantmentSimulator.costs(seed, observation.power, observation.stack);
        if (!Arrays.equals(costs, observation.costs)) {
            return false;
        }
        for (int slot = 0; slot < 3; slot++) {
            if (costs[slot] <= 0) {
                continue;
            }
            EnchantmentData clue = EnchantmentSimulator.clue(seed, observation.stack, slot, costs[slot]);
            int id = clue == null ? -1 : Enchantment.getEnchantmentID(clue.enchantment);
            int level = clue == null ? -1 : clue.enchantmentLevel;
            if (id != observation.clueIds[slot] || level != observation.clueLevels[slot]) {
                return false;
            }
        }
        return true;
    }

    public void startCalibration() {
        resetManipulationOnly();
        calibrationStage = CalibrationStage.WAIT_FIRST_ENCHANT;
        tell(TextFormatting.YELLOW + "Player RNG calibration started. Perform a cheap enchant, then crack its new seed.");
    }

    public void planTarget(String itemName, Map<String, Integer> requested) {
        if (searchJob != null) {
            tell(TextFormatting.RED + "A target search is already running. Use /enchcracker cancel to stop it.");
            return;
        }
        if (dropQueue != null || (activePlan != null && activePlan.awaitingDummy)) {
            tell(TextFormatting.RED + "Finish the pending drops and dummy enchant before searching again.");
            return;
        }
        if (playerRng == null) {
            tell(TextFormatting.RED + "Player RNG is not cracked. Run /enchcracker rng first.");
            return;
        }

        ResourceLocation itemId = normalizedId(itemName);
        Item item = Item.REGISTRY.getObject(itemId);
        if (item == null || item == Item.REGISTRY.getObject(new ResourceLocation("minecraft:air"))) {
            tell(TextFormatting.RED + "Unknown item: " + itemName);
            return;
        }

        final ItemStack targetStack = new ItemStack(item);
        if (!targetStack.isItemEnchantable()) {
            tell(TextFormatting.RED + "That item is not enchantable at a table.");
            return;
        }
        final EnchantmentTargets targets = new EnchantmentTargets(targetStack, requested);
        final JavaRandom48 baseRng = playerRng.copy();
        final int currentSeed = knownXpSeed;
        final boolean hasCurrentSeed = xpSeedKnown;
        final SearchJob job = new SearchJob(baseRng.getInternalSeed());
        searchJob = job;
        activePlan = null;
        tell(TextFormatting.GRAY + "Requiring ALL: " + targets.describe() + ". Extra enchantments are allowed.");
        tell(TextFormatting.GRAY + "Searching beyond 64 drops until a match or cancellation. /enchcracker status shows progress; /enchcracker cancel stops.");
        planner.submit(() -> {
            try {
                job.result = searchPlan(targetStack, targets, baseRng, currentSeed, hasCurrentSeed, job);
            } catch (RuntimeException e) {
                job.error = e.toString();
            } finally {
                job.done = true;
            }
        });
    }

    private static ManipulationPlan searchPlan(ItemStack stack, EnchantmentTargets targets,
                                                JavaRandom48 baseRng, int currentSeed, boolean hasCurrentSeed, SearchJob job) {
        if (hasCurrentSeed) {
            ManipulationPlan current = findForSeed(stack, targets, currentSeed, -1, false,
                baseRng.getInternalSeed());
            if (current != null) {
                return current;
            }
        }
        ManipulationPlan plan = DropSearch.find(baseRng, () -> job.cancelled,
            checked -> job.checked = checked,
            (drops, xpSeed) -> findForSeed(stack, targets, xpSeed, drops, true, baseRng.getInternalSeed()));
        return plan == null ? ManipulationPlan.notFound() : plan;
    }

    private static ManipulationPlan findForSeed(ItemStack stack, EnchantmentTargets targets,
                                                 int xpSeed, long drops, boolean dummy, long baseState) {
        for (int power = 0; power <= 15; power++) {
            int[] costs = EnchantmentSimulator.costs(xpSeed, power, stack);
            for (int slot = 0; slot < 3; slot++) {
                List<EnchantmentData> list = EnchantmentSimulator.enchantments(xpSeed, stack, slot, costs[slot]);
                if (targets.matches(list)) {
                    return new ManipulationPlan(true, dummy, drops, power, slot, xpSeed,
                        stack.copy(), new ArrayList<EnchantmentData>(list), baseState);
                }
            }
        }
        return null;
    }

    public void executePlan() {
        if (dropQueue != null) {
            if (dropQueue.paused) {
                dropQueue.paused = false;
                tell(TextFormatting.YELLOW + "Resuming matching hotbar items. Close the GUI to continue dropping.");
            } else {
                tell(TextFormatting.YELLOW + "Drops already running. /enchcracker pause to pause.");
            }
            return;
        }
        if (activePlan == null || !activePlan.found) {
            tell(TextFormatting.RED + "No plan is ready. Use /enchcracker target first.");
            return;
        }
        if (!activePlan.requiresDummy) {
            tell(TextFormatting.GREEN + "No execution is required; the current seed already matches.");
            return;
        }
        if (dropQueue != null || activePlan.awaitingDummy) {
            tell(TextFormatting.RED + "The current plan is already executing.");
            return;
        }
        if (playerRng == null || playerRng.getInternalSeed() != activePlan.basePlayerState) {
            failManipulation("Player RNG changed after the plan was calculated.");
            return;
        }
        if (activePlan.drops == 0) {
            activePlan.awaitingDummy = true;
            tell(TextFormatting.YELLOW + "Perform one cheap dummy enchant now.");
            return;
        }
        ItemStack held = mc.player.getHeldItemMainhand();
        if (held.isEmpty()) {
            tell(TextFormatting.RED + "Select a disposable item. Only matching hotbar stacks will be dropped.");
            return;
        }
        dropQueue = new DropQueue(activePlan.drops, held.copy(), mc.player.inventory.currentItem);
        tell(TextFormatting.YELLOW + "Dropping " + activePlan.drops + " x " + held.getDisplayName()
            + " from matching hotbar stacks. Close the GUI. /enchcracker pause to pause; execute to resume.");
    }

    private void tickDropQueue() {
        if (dropQueue == null || mc.player == null || mc.isGamePaused()) {
            return;
        }
        // Finish an in-flight request even if the user opens chat or pauses.
        // Never re-send a pending drop: it may already have consumed server RNG.
        if (dropQueue.pending != null) {
            DropConfirmation.Result result = dropQueue.pending.observe(
                mc.player.inventory.mainInventory.get(dropQueue.slot), System.nanoTime());
            if (result == DropConfirmation.Result.WAITING) {
                return;
            }
            if (result != DropConfirmation.Result.CONFIRMED) {
                failManipulation(result == DropConfirmation.Result.TIMED_OUT
                    ? "No drop inventory update within 15 seconds. Stopped without retrying; run /enchcracker rng again."
                    : "Disposable slot changed unexpectedly while awaiting a drop. Run /enchcracker rng again.");
                return;
            }
            playerRng.advance(4L);
            dropQueue.sent++;
            dropQueue.pending = null;
            dropQueue.cooldown = 2;
            if (dropQueue.sent == dropQueue.total) {
                activePlan.awaitingDummy = true;
                dropQueue = null;
                tell(TextFormatting.YELLOW + "Drops complete. Perform one cheap dummy enchant now.");
            }
            return;
        }
        if (dropQueue.paused || mc.currentScreen != null) {
            return;
        }
        if (dropQueue.cooldown > 0) {
            dropQueue.cooldown--;
            return;
        }
        if (dropQueue.sent < dropQueue.total) {
            int slot = DisposableStacks.findHotbarSlot(mc.player.inventory.mainInventory,
                dropQueue.template, dropQueue.slot);
            if (slot < 0) {
                dropQueue.paused = true;
                tell(TextFormatting.YELLOW + "Paused at " + dropQueue.sent + "/" + dropQueue.total
                    + " drops. Refill the hotbar with " + dropQueue.template.getDisplayName()
                    + " from your inventory, then /enchcracker execute. Do not drop items manually.");
                return;
            }
            dropQueue.slot = slot;
            mc.player.inventory.currentItem = slot;
            mc.playerController.updateController();
            dropQueue.pending = new DropConfirmation(mc.player.getHeldItemMainhand(), System.nanoTime());
            mc.player.dropItem(false);
            return;
        }
    }

    public void pauseDrops() {
        if (dropQueue == null) {
            tell("No drops are running. Use /enchcracker cancel to cancel a search.");
            return;
        }
        dropQueue.paused = true;
        tell("Drops paused at " + dropQueue.sent + "/" + dropQueue.total + ". /enchcracker execute resumes.");
    }

    public void cancelPlan() {
        if (dropQueue != null || (activePlan != null && activePlan.awaitingDummy)) {
            failManipulation("Execution cancelled. Recalibrate before another target.");
            return;
        }
        cancelSearch();
        activePlan = null;
        tell("Search/plan cancelled. No items were dropped by this command.");
    }

    private void cancelSearch() {
        if (searchJob != null) {
            searchJob.cancelled = true;
            searchJob = null;
        }
    }

    public void resetAll() {
        seedMask = UNKNOWN_MASK;
        candidateSeeds = new int[0];
        xpSeedKnown = false;
        lastObservationKey = "";
        filtering = false;
        completedFilter = null;
        resetManipulationOnly();
        prompt = "Reset. Open an enchanting table.";
        tell(TextFormatting.GRAY + "Enchant Cracker reset.");
    }

    private void resetManipulationOnly() {
        calibrationStage = CalibrationStage.IDLE;
        playerRng = null;
        cancelSearch();
        activePlan = null;
        dropQueue = null;
    }

    private void failManipulation(String reason) {
        resetManipulationOnly();
        prompt = reason + " Run /enchcracker rng again.";
        tell(TextFormatting.RED + reason);
    }

    public List<String> getOverlayLines() {
        List<String> lines = new ArrayList<String>();
        lines.add(TextFormatting.AQUA + "Enchant Cracker 1.12.2");
        if (xpSeedKnown) {
            lines.add(TextFormatting.GREEN + String.format(Locale.ROOT, "XP seed: %08X", knownXpSeed));
        } else if (filtering) {
            lines.add(TextFormatting.YELLOW + "XP seed: analyzing " + candidateSeeds.length);
        } else {
            lines.add(TextFormatting.YELLOW + "XP candidates: " + candidateSeeds.length);
        }
        lines.add("Player RNG: " + (playerRng != null
            ? TextFormatting.GREEN + "cracked"
            : calibrationStage == CalibrationStage.IDLE
                ? TextFormatting.RED + "unknown"
                : TextFormatting.YELLOW + calibrationStage.label));
        lines.add("Bookshelves: " + detectedPower);
        lines.add(TextFormatting.GRAY + prompt);
        if (searchJob != null) {
            lines.add("Search: " + searchJob.checked + " drop counts checked; /enchcracker cancel");
        }
        if (dropQueue != null) {
            lines.add("Drops: " + dropQueue.sent + "/" + dropQueue.total
                + (dropQueue.paused ? " paused; /enchcracker execute" : " (close GUI to run)"));
            if (dropQueue.pending != null) {
                lines.add("  Waiting for server inventory update (one drop pending)");
            }
        }

        if (xpSeedKnown && mc.player != null && mc.player.openContainer instanceof ContainerEnchantment) {
            ContainerEnchantment container = (ContainerEnchantment) mc.player.openContainer;
            ItemStack stack = container.getSlot(0).getStack();
            if (!stack.isEmpty()) {
                for (int slot = 0; slot < 3; slot++) {
                    List<EnchantmentData> predictions = EnchantmentSimulator.enchantments(
                        knownXpSeed, stack, slot, container.enchantLevels[slot]);
                    lines.add(TextFormatting.GOLD + "Slot " + (slot + 1) + " [" + container.enchantLevels[slot] + "]:"
                        + TextFormatting.RESET);
                    if (predictions.isEmpty()) {
                        lines.add("  -");
                    } else {
                        for (EnchantmentData data : predictions) {
                            lines.add("  " + data.enchantment.getTranslatedName(data.enchantmentLevel));
                        }
                    }
                }
            }
        }

        if (activePlan != null && activePlan.found) {
            lines.add(TextFormatting.LIGHT_PURPLE + "Target plan:");
            if (activePlan.requiresDummy && !activePlan.readyForFinal) {
                lines.add("  drops " + activePlan.drops + " + dummy enchant");
            } else {
                lines.add("  shelves " + activePlan.bookshelves + ", slot " + (activePlan.slot + 1));
                for (EnchantmentData data : activePlan.enchantments) {
                    lines.add("  " + data.enchantment.getTranslatedName(data.enchantmentLevel));
                }
            }
        }
        return lines;
    }

    public String getStatus() {
        return "XP=" + (xpSeedKnown ? String.format(Locale.ROOT, "%08X", knownXpSeed)
            : candidateSeeds.length + " candidates")
            + ", player RNG=" + (playerRng == null ? calibrationStage.label : "cracked")
            + ", " + prompt
            + (searchJob == null ? "" : ", search: " + searchJob.checked + " drop counts checked")
            + (dropQueue == null ? "" : ", drops: " + dropQueue.sent + "/" + dropQueue.total
                + (dropQueue.paused ? " paused; /enchcracker execute to resume" : "; close GUI to run")
                + (dropQueue.pending == null ? "" : "; awaiting server inventory update"));
    }

    private void tell(String message) {
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString(TextFormatting.AQUA + "[EnchantCracker] "
                + TextFormatting.RESET + message));
        }
    }

    private static ResourceLocation normalizedId(String value) {
        return value.indexOf(':') >= 0 ? new ResourceLocation(value) : new ResourceLocation("minecraft", value);
    }

    private static int[] createInitialCandidates(int lowMask) {
        int[] seeds = new int[1 << 20];
        int index = 0;
        for (int high = 0; high < 1 << 16; high++) {
            for (int low = 0; low < 16; low++) {
                seeds[index++] = (high << 16) | lowMask | low;
            }
        }
        return seeds;
    }

    private static int findEnchantPower(World world, BlockPos playerPos) {
        BlockPos table = null;
        double best = Double.MAX_VALUE;
        for (int x = -8; x <= 8; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -8; z <= 8; z++) {
                    BlockPos candidate = playerPos.add(x, y, z);
                    if (world.getBlockState(candidate).getBlock() == Blocks.ENCHANTING_TABLE) {
                        double distance = candidate.distanceSq(playerPos);
                        if (distance < best) {
                            best = distance;
                            table = candidate;
                        }
                    }
                }
            }
        }
        if (table == null) {
            return 0;
        }
        int power = 0;
        for (int j = -1; j <= 1; j++) {
            for (int k = -1; k <= 1; k++) {
                if ((j != 0 || k != 0) && world.isAirBlock(table.add(k, 0, j))
                    && world.isAirBlock(table.add(k, 1, j))) {
                    power += bookshelf(world, table.add(k * 2, 0, j * 2));
                    power += bookshelf(world, table.add(k * 2, 1, j * 2));
                    if (k != 0 && j != 0) {
                        power += bookshelf(world, table.add(k * 2, 0, j));
                        power += bookshelf(world, table.add(k * 2, 1, j));
                        power += bookshelf(world, table.add(k, 0, j * 2));
                        power += bookshelf(world, table.add(k, 1, j * 2));
                    }
                }
            }
        }
        return Math.min(power, 15);
    }

    private static int bookshelf(World world, BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        return block == Blocks.BOOKSHELF ? 1 : 0;
    }

    private enum CalibrationStage {
        IDLE("unknown"),
        WAIT_FIRST_ENCHANT("awaiting first enchant"),
        CAPTURE_FIRST("cracking first seed"),
        WAIT_SECOND_ENCHANT("awaiting second enchant"),
        CAPTURE_SECOND("cracking second seed");

        private final String label;

        CalibrationStage(String label) {
            this.label = label;
        }
    }

    private static final class Observation {
        private final ItemStack stack;
        private final int power;
        private final int[] costs;
        private final int[] clueIds;
        private final int[] clueLevels;

        private Observation(ItemStack stack, int power, int[] costs, int[] clueIds, int[] clueLevels) {
            this.stack = stack;
            this.power = power;
            this.costs = costs;
            this.clueIds = clueIds;
            this.clueLevels = clueLevels;
        }

        private String key() {
            ResourceLocation item = Item.REGISTRY.getNameForObject(stack.getItem());
            return item + ":" + stack.getMetadata() + ":" + power + ":"
                + Arrays.toString(costs) + ":" + Arrays.toString(clueIds) + ":" + Arrays.toString(clueLevels);
        }
    }

    private static final class FilterResult {
        private final int mask;
        private final int[] seeds;

        private FilterResult(int mask, int[] seeds) {
            this.mask = mask;
            this.seeds = seeds;
        }
    }

    private static final class DropQueue {
        private final long total;
        private final ItemStack template;
        private long sent;
        private int slot;
        private int cooldown;
        private boolean paused;
        private DropConfirmation pending;

        private DropQueue(long total, ItemStack template, int slot) {
            this.total = total;
            this.template = template;
            this.slot = slot;
        }
    }

    private static final class SearchJob {
        private final long baseState;
        private volatile boolean cancelled;
        private volatile boolean done;
        private volatile long checked;
        private ManipulationPlan result;
        private String error;

        private SearchJob(long baseState) {
            this.baseState = baseState;
        }
    }

    private static final class ManipulationPlan {
        private final boolean found;
        private final boolean requiresDummy;
        private final long drops;
        private final int bookshelves;
        private final int slot;
        private final int xpSeed;
        private final ItemStack item;
        private final List<EnchantmentData> enchantments;
        private final long basePlayerState;
        private boolean awaitingDummy;
        private boolean readyForFinal;

        private ManipulationPlan(boolean found, boolean requiresDummy, long drops, int bookshelves,
                                 int slot, int xpSeed, ItemStack item,
                                 List<EnchantmentData> enchantments, long basePlayerState) {
            this.found = found;
            this.requiresDummy = requiresDummy;
            this.drops = drops;
            this.bookshelves = bookshelves;
            this.slot = slot;
            this.xpSeed = xpSeed;
            this.item = item;
            this.enchantments = enchantments;
            this.basePlayerState = basePlayerState;
        }

        private static ManipulationPlan notFound() {
            return new ManipulationPlan(false, false, 0, 0, 0, 0, ItemStack.EMPTY,
                Collections.<EnchantmentData>emptyList(), 0L);
        }
    }
}
