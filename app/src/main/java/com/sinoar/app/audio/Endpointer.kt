package com.sinoar.app.audio

/**
 * Energy + adaptive noise-floor endpointer.
 *
 * This is our own small algorithm: it tracks the ambient noise floor with a fast
 * attack / slow release, and declares speech only when the frame is clearly above
 * that floor. A hangover keeps the speech state alive through short pauses.
 */
class Endpointer(
    private val marginDb: Float = 9f,
    private val hangoverMs: Int = 500,
    private val frameMs: Int = 20,
) {
    var noiseFloorDb: Float = -60f
        private set

    private var hangoverLeftMs = 0

    val isSpeaking: Boolean get() = hangoverLeftMs > 0

    fun update(db: Float): Boolean {
        noiseFloorDb = if (db < noiseFloorDb) {
            0.80f * noiseFloorDb + 0.20f * db
        } else {
            0.995f * noiseFloorDb + 0.005f * db
        }

        val aboveFloor = db > noiseFloorDb + marginDb
        hangoverLeftMs = if (aboveFloor) {
            hangoverMs
        } else {
            (hangoverLeftMs - frameMs).coerceAtLeast(0)
        }
        return aboveFloor
    }
}
