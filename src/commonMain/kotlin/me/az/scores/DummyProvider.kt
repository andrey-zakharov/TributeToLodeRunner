package me.az.scores

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class HiScoreRecord(
    val playerName: String,
    val scores: Int,
    val date: Instant = Clock.System.now()
)
interface ScoresProvider {
    val scores: Sequence<HiScoreRecord>
}
class DummyProvider : ScoresProvider {
    override val scores = listOf(
        HiScoreRecord("Johnny Depp", 1000, Instant.parse("2022-06-01")),
        HiScoreRecord("Arnold Schwarzenegger", 500, Instant.parse("1991-12-25")),
        HiScoreRecord("Andrey Zakharov", 0, Instant.parse("1981-08-04"))
    ).asSequence()
}