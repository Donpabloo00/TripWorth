package com.ridego.app.data

import com.ridego.app.parser.Platform

object DemoOffers {

    /**
     * @param synthetic true when the text was written to the spec rather than
     * captured from a real screen. Surfaced in the UI so a passing demo is
     * never mistaken for proof the parser handles the real app.
     */
    data class Sample(
        val label: String,
        val platform: Platform,
        val ocrText: String,
        val synthetic: Boolean = false
    )

    /** Real Uber captures, kept as raw OCR text so Demo exercises the parser too. */
    private val uber = listOf(
        Sample(
            label = "17,20 RON • Pallady → Rebreanu",
            platform = Platform.UBER,
            ocrText = """
                Uber
                17,20 RON

                La 5 min. (2.9 km) distanță
                Bulevardul Theodor Pallady 51G, București

                Cursă: 12 min. (5.2 km)
                Str. Liviu Rebreanu 5, București
            """.trimIndent()
        ),
        Sample(
            label = "30,29 RON • Popești-Leordeni → Rebreanu",
            platform = Platform.UBER,
            ocrText = """
                Uber
                30,29 RON

                La 17 min. (14.8 km) distanță
                Str. Biruinței 85, Popești-Leordeni

                Cursă: 19 min. (8.2 km)
                Str. Liviu Rebreanu 5, București
            """.trimIndent()
        ),
        Sample(
            label = "19,76 RON • HILS Pallady → Pantelimon",
            platform = Platform.UBER,
            ocrText = """
                Uber
                19,76 RON

                La 1 min. (0.6 km) distanță
                HILS Pallady 66A, București

                Cursă: 20 min. (9.7 km)
                Strada Sfântul Călinic 16, Pantelimon
            """.trimIndent()
        ),
        Sample(
            // Reproduces a real mis-read from the field: the ride's minutes
            // came back as 1, which made an 8.5 km trip read as 510 km/h and
            // turned a 20 RON fare into a confident 1223 RON/oră. Kept as a
            // demo so the guard against it stays exercisable by hand.
            label = "20,38 RON • citire greșită a minutelor",
            platform = Platform.UBER,
            synthetic = true,
            ocrText = """
                Uber
                20,38 RON

                La 4 min. (2.2 km) distanță
                Strada Exemplu 1, București

                Cursă: 1 min. (8.5 km)
                Strada Exemplu 2, București
            """.trimIndent()
        )
    )

    /**
     * Synthetic Bolt cards. No real Bolt Driver capture was available when
     * this parser was written, so these follow the generic layout and the
     * fragmented variant OCR tends to produce.
     */
    private val bolt = listOf(
        Sample(
            label = "25,50 RON • layout standard",
            platform = Platform.BOLT,
            synthetic = true,
            ocrText = """
                Bolt
                25,50 RON

                3,2 km
                6 min

                Pickup:
                Strada Exemplu 10, București

                Destinație:
                Bulevardul Exemplu 20, București

                8,4 km
                18 min
            """.trimIndent()
        ),
        Sample(
            label = "25,50 RON • text fragmentat OCR",
            platform = Platform.BOLT,
            synthetic = true,
            ocrText = """
                Bolt
                25,50
                RON
                3.2 km
                6 min
                Strada Exemplu 10
                București
                8.4 km
                18 min
                Bulevardul Exemplu 20
                București
            """.trimIndent()
        ),
        Sample(
            label = "48,00 RON • cursă lungă, Bolt Comfort",
            platform = Platform.BOLT,
            synthetic = true,
            ocrText = """
                Bolt Comfort
                ★ 4,87
                48,00 RON • Card
                2,1 km • 4 min
                Pickup: Piața Victoriei 1, București
                Destinație: Aeroport Otopeni
                18,6 km • 26 min
            """.trimIndent()
        )
    )

    val samples = uber + bolt

    fun forPlatform(platform: Platform?): List<Sample> =
        if (platform == null) samples else samples.filter { it.platform == platform }
}
