package com.example.jellymusic.util

data class TuningString(val label: String, val midi: Int)

data class InstrumentTuning(val name: String, val strings: List<TuningString>)

object Instruments {
    val GUITAR = InstrumentTuning(
        "吉他",
        listOf(
            TuningString("E2", 40), TuningString("A2", 45), TuningString("D3", 50),
            TuningString("G3", 55), TuningString("B3", 59), TuningString("E4", 64),
        ),
    )
    val DROP_D = InstrumentTuning(
        "吉他 Drop D",
        listOf(
            TuningString("D2", 38), TuningString("A2", 45), TuningString("D3", 50),
            TuningString("G3", 55), TuningString("B3", 59), TuningString("E4", 64),
        ),
    )
    val UKULELE = InstrumentTuning(
        "尤克里里",
        listOf(
            TuningString("G4", 67), TuningString("C4", 60),
            TuningString("E4", 64), TuningString("A4", 69),
        ),
    )
    val BASS = InstrumentTuning(
        "贝斯",
        listOf(
            TuningString("E1", 28), TuningString("A1", 33),
            TuningString("D2", 38), TuningString("G2", 43),
        ),
    )
    val VIOLIN = InstrumentTuning(
        "小提琴",
        listOf(
            TuningString("G3", 55), TuningString("D4", 62),
            TuningString("A4", 69), TuningString("E5", 76),
        ),
    )
    val ALL = listOf(GUITAR, DROP_D, UKULELE, BASS, VIOLIN)
}

data class ChordShape(val name: String, val frets: List<Int>)

object ChordData {
    val GUITAR = listOf(
        ChordShape("C", listOf(-1, 3, 2, 0, 1, 0)),
        ChordShape("G", listOf(3, 2, 0, 0, 0, 3)),
        ChordShape("D", listOf(-1, -1, 0, 2, 3, 2)),
        ChordShape("A", listOf(-1, 0, 2, 2, 2, 0)),
        ChordShape("E", listOf(0, 2, 2, 1, 0, 0)),
        ChordShape("F", listOf(1, 3, 3, 2, 1, 1)),
        ChordShape("B", listOf(-1, 2, 4, 4, 4, 2)),
        ChordShape("Am", listOf(-1, 0, 2, 2, 1, 0)),
        ChordShape("Dm", listOf(-1, -1, 0, 2, 3, 1)),
        ChordShape("Em", listOf(0, 2, 2, 0, 0, 0)),
        ChordShape("G7", listOf(3, 2, 0, 0, 0, 1)),
        ChordShape("C7", listOf(-1, 3, 2, 3, 1, 0)),
    )
    val UKULELE = listOf(
        ChordShape("C", listOf(0, 0, 0, 3)),
        ChordShape("G", listOf(0, 2, 3, 2)),
        ChordShape("D", listOf(2, 2, 2, 0)),
        ChordShape("A", listOf(2, 1, 0, 0)),
        ChordShape("E", listOf(1, 4, 0, 2)),
        ChordShape("F", listOf(2, 0, 1, 0)),
        ChordShape("Am", listOf(2, 0, 0, 0)),
        ChordShape("Dm", listOf(2, 2, 1, 0)),
        ChordShape("Em", listOf(0, 4, 3, 2)),
    )
}
