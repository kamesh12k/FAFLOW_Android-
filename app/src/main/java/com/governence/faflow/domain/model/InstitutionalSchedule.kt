package com.governence.faflow.domain.model

/**
 * Institutional Schedule & Academic Timetable Configuration for FAFLOW.
 * Defines the standard institutional milestones, teaching period time ranges, and break slots.
 */
object InstitutionalSchedule {

    // Daily Institutional Milestones
    const val WORKING_DAY_START = "09:10"
    const val STUDENT_REPORTING = "09:15"
    const val FIRST_PERIOD_START = "09:20"
    const val COLLEGE_DAY_END = "15:50"
    const val STAFF_EXTENDED_END = "17:45"

    // Break & Interval Milestones
    const val MORNING_BREAK_START = "11:15"
    const val MORNING_BREAK_END = "11:40"
    const val LUNCH_START = "12:35"
    const val LUNCH_END = "13:35"
    const val AFTERNOON_BREAK_START = "14:30"
    const val AFTERNOON_BREAK_END = "14:55"

    /**
     * Map of period numbers (1 to 5) to formatted time range strings.
     */
    val PERIOD_TIMES: Map<Int, String> = mapOf(
        1 to "09:20–10:20",
        2 to "10:20–11:15",
        3 to "11:40–12:35",
        4 to "13:35–14:30",
        5 to "14:55–15:50"
    )

    /**
     * Returns formatted time range string for a given period number (e.g., "09:20–10:20").
     */
    fun getPeriodTime(periodNumber: Int): String {
        return PERIOD_TIMES[periodNumber] ?: "Period $periodNumber"
    }

    /**
     * Period timing definitions with start & end minutes from midnight for schedule calculations.
     */
    val PERIOD_MINUTE_RANGES: Map<Int, IntRange> = mapOf(
        1 to (9 * 60 + 20)..(10 * 60 + 19),   // 09:20 – 10:19
        2 to (10 * 60 + 20)..(11 * 60 + 14),  // 10:20 – 11:14
        3 to (11 * 60 + 40)..(12 * 60 + 34),  // 11:40 – 12:34
        4 to (13 * 60 + 35)..(14 * 60 + 29),  // 13:35 – 14:29
        5 to (14 * 60 + 55)..(15 * 60 + 49)   // 14:55 – 15:49
    )

    /**
     * Resolves the active period number for a given minute since midnight, or null during breaks/off-hours.
     */
    fun resolvePeriodNumber(minutesSinceMidnight: Int): Int? {
        return PERIOD_MINUTE_RANGES.entries.firstOrNull { minutesSinceMidnight in it.value }?.key
    }

    /**
     * Resolves the active or most recent academic period. During breaks/lunch/afternoon tea,
     * it maps to the period that just occurred or is occurring (e.g. 14:30 -> Period 4).
     */
    fun resolveActiveOrRecentPeriod(minutesSinceMidnight: Int): Int {
        resolvePeriodNumber(minutesSinceMidnight)?.let { return it }
        return when {
            minutesSinceMidnight < (10 * 60 + 20) -> 1
            minutesSinceMidnight < (11 * 60 + 40) -> 2
            minutesSinceMidnight < (13 * 60 + 35) -> 3
            minutesSinceMidnight < (14 * 60 + 55) -> 4
            else -> 5
        }
    }

    fun getPeriodStartMinute(periodNumber: Int): Int {
        return PERIOD_MINUTE_RANGES[periodNumber]?.first ?: (9 * 60 + 20)
    }

    /**
     * Checks if current minutesSinceMidnight is within the official 15-minute submission window
     * from the period start time.
     */
    fun isWithin15MinuteWindow(periodNumber: Int, minutesSinceMidnight: Int): Boolean {
        val start = getPeriodStartMinute(periodNumber)
        return minutesSinceMidnight in start..(start + 15)
    }
}
