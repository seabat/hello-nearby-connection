package dev.seabat.android.hellonearbyconnections

import java.util.Random

/**
 * Instead of having each player enter a name, in this sample we will conveniently generate
 * random human readable names for players.
 */
object CodenameGenerator {
    private val COLORS = arrayOf(
        "Red", "Orange", "Yellow", "Green", "Blue", "Indigo", "Violet", "Purple", "Lavender"
    )
    private val TREATS = arrayOf(
        "Cupcake", "Donut", "Eclair", "Froyo", "Gingerbread", "Honeycomb",
        "Ice Cream Sandwich", "Jellybean", "Kit Kat", "Lollipop", "Marshmallow", "Nougat",
        "Oreo", "Pie"
    )
    private val generator = Random()

    /** Generate a random Android agent codename  */
    fun generate(): String {
        val color = COLORS[generator.nextInt(COLORS.size)]
        val treat = TREATS[generator.nextInt(TREATS.size)]
        return "$color $treat"
    }
}