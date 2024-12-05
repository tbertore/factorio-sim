import kotlin.math.floor
import kotlin.math.pow

// Assumptions:
// Loop is input items -> assembler 3 (With config'd modules) -> output items -> recycler (With config'd modules)
// Input items are the direct input to the produced item
// Output items are recycled back to input items if they are less than legendary
data class Config(
    val totalCycles: Int = 1,
    val recyclerQuality: Double = 0.0, // Total quality % of recycler (Rare Quality Module 3)
    val productionProductivity: Double = 0.0, // Total productivity % of assembler (Rare Productivity Module 2)
    val productionQuality: Double = 0.0, // Total quality % of assembler
    val maxItemTier: Int = 4 // 0 = Normal, 1 = Uncommon, etc.
)
enum class ItemTier(val tier: Int, val multiplier: Double) {
    NORMAL(0, 1.0),
    UNCOMMON(1, 1.3),
    RARE(2, 1.6),
    EPIC(3, 1.9),
    LEGENDARY(4, 2.5)
}

fun qualityModule(moduleLevel: Int, itemTier: ItemTier): Double {
    return when (moduleLevel) {
        1 -> 0.01 * itemTier.multiplier
        2 -> 0.02 * itemTier.multiplier
        3 -> 0.025 * itemTier.multiplier
        else -> error("Invalid module level")
    }
}

fun productivityModule(moduleLevel: Int, itemTier: ItemTier): Double {
    return when (moduleLevel) {
        1 -> floor(4 * itemTier.multiplier) / 100.0
        2 -> floor(6 * itemTier.multiplier) / 100.0
        3 -> floor(10 * itemTier.multiplier) / 100.0
        else -> error("Invalid module level")
    }
}

data class Result(
    val inputItemsByTier: ArrayList<Double> = arrayListOf(0.0, 0.0, 0.0, 0.0, 0.0),
    val outputItemsByTier: ArrayList<Double> = arrayListOf(0.0, 0.0, 0.0, 0.0, 0.0)
) {
    override fun toString(): String {
        return "Input: ${inputItemsByTier}, Output: ${outputItemsByTier}"
    }
}

fun main() {
    val qualityModule = qualityModule(3, ItemTier.LEGENDARY)
    val productivityModule = productivityModule(3, ItemTier.LEGENDARY)
    val qualityConfig =      Config(recyclerQuality = qualityModule * 4, productionQuality = qualityModule * 5, productionProductivity = productivityModule * 0 + 0.5)
    val hybridConfig41 =     Config(recyclerQuality = qualityModule * 4, productionQuality = qualityModule * 4, productionProductivity = productivityModule * 1 + 0.5)
    val hybridConfig32 =     Config(recyclerQuality = qualityModule * 4, productionQuality = qualityModule * 3, productionProductivity = productivityModule * 2 + 0.5)
    val hybridConfig23 =     Config(recyclerQuality = qualityModule * 4, productionQuality = qualityModule * 2, productionProductivity = productivityModule * 3 + 0.5)
    val hybridConfig14 =     Config(recyclerQuality = qualityModule * 4, productionQuality = qualityModule * 1, productionProductivity = productivityModule * 4 + 0.5)
    val productivityConfig = Config(recyclerQuality = qualityModule * 4, productionQuality = qualityModule * 0, productionProductivity = productivityModule * 5 + 0.5)
    val inputItems = Result(arrayListOf(10000.0, 0.0, 0.0, 0.0, 0.0))
    val cycles = 10

    val qualityResult = simulate(qualityConfig, inputItems.inputItemsByTier, cycles)
    val hybridResult41 = simulate(hybridConfig41, inputItems.inputItemsByTier, cycles)
    val hybridResult32 = simulate(hybridConfig32, inputItems.inputItemsByTier, cycles)
    val hybridResult23 = simulate(hybridConfig23, inputItems.inputItemsByTier, cycles)
    val hybridResult14 = simulate(hybridConfig14, inputItems.inputItemsByTier, cycles)
    val productivityResult = simulate(productivityConfig, inputItems.inputItemsByTier, cycles)


    println("Quality result after ${cycles} recycler loops ${format(qualityResult) }")
    println("Hybrid41 result after ${cycles} recycler loops ${format(hybridResult41)}")
    println("Hybrid32 result after ${cycles} recycler loops ${format(hybridResult32)}")
    println("Hybrid23 result after ${cycles} recycler loops ${format(hybridResult23)}")
    println("Hybrid14 result after ${cycles} recycler loops ${format(hybridResult14)}")
    println("Productivity result after ${cycles} recycler loops ${format(productivityResult)}")

}

fun format(result: List<Double>): String {
    return ItemTier.entries.foldIndexed("") {index, acc, itemTier ->
        acc + "${itemTier.name}: ${String.format("%.2f", result[index])}, "
    }
}

fun simulate(config: Config, initialItems: List<Double>, cycles: Int): List<Double> {
    var totalMaxItems = 0.0;
    val simulationResult = (0..cycles).fold(ArrayList(initialItems)) { acc, it ->
        val result = cycle(config, acc)

        totalMaxItems += result.second
        result.first as java.util.ArrayList<Double?>
    }

    simulationResult[config.maxItemTier] = totalMaxItems
    return simulationResult
}

fun cycle(config: Config, inputItems: List<Double>): Pair<List<Double>, Double> {
    val productionItems = (0..config.maxItemTier).map { tier ->
        upgradeChance(config, tier, config.productionQuality).map {
            // If we're crafting legendary input items, always use productivity modules
            val productivity = if (tier == 4) productivityModule(3, ItemTier.LEGENDARY) * 5 + 0.5 else config.productionProductivity

            inputItems[tier] * (1.0 + productivity) * it
        }
    }.reduce { acc, result ->
        acc.zip(result).map { it.first + it.second }
    }

    val recycleItems = (0..config.maxItemTier).map { tier ->
        upgradeChance(config, tier, config.recyclerQuality).map {
            // Don't recycle the desired items
            if (tier == config.maxItemTier)
                0.0
            else
                productionItems[tier] * 0.25 * it
        }
    }.reduce { acc, result ->
        acc.zip(result).map { it.first + it.second }
    }

    return recycleItems to productionItems[config.maxItemTier]
}

fun upgradeChance(config: Config, inputTier: Int, quality: Double): Array<Double> {
    val result = Array(5) { 0.0 }
    var totalUpgradeChance = 0.0

    for (tier in inputTier + 1 .. config.maxItemTier) {
        result[tier] = upgradeStep(inputTier, tier, quality)
        totalUpgradeChance += result[tier]
    }
    result[inputTier] = 1.0 - totalUpgradeChance
    return result
}

fun upgradeStep(inputTier: Int, outputTier: Int, quality: Double): Double {
    if (inputTier >= outputTier) error("Can't calculate upgrade between $inputTier and $outputTier")
    val tierDiff = outputTier - inputTier

    return quality * 0.10.pow(tierDiff.toDouble() - 1)
}