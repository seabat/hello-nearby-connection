package dev.seabat.android.hellonearbyconnections.model.game

/**
 * Enum class for defining the winning rules for Rock-Paper-Scissors. Each player will make a
 * choice, then the beats function in this class will be used to determine whom to reward the
 * point to.
 */
enum class GameChoiceEnum {
    ROCK, PAPER, SCISSORS;

    fun beats(other: GameChoiceEnum): Boolean =
        (this == ROCK && other == SCISSORS)
                || (this == SCISSORS && other == PAPER)
                || (this == PAPER && other == ROCK)
}