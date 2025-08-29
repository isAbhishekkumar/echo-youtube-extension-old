package dev.brahmkshatriya.echo.common.models

import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.GregorianCalendar

/**
 * Represents a date with varying levels of precision (year, month, day)
 * Used for release dates, creation dates, etc.
 */
@Serializable
data class Date(val epochTimeMs: Long) : Comparable<Date> {
    
    /**
     * Creates a Date from year, optional month and day components
     * 
     * @param year The year component (required)
     * @param month The month component (1-12, optional)
     * @param day The day of month component (1-31, optional)
     */
    constructor(year: Int, month: Int? = null, day: Int? = null) : this(
        GregorianCalendar(
            year,
            month?.minus(1) ?: 0,
            day ?: 1
        ).timeInMillis
    )
    
    /**
     * The Calendar instance for this date
     */
    val calendar: Calendar
        get() = Calendar.getInstance().apply { timeInMillis = epochTimeMs }
    
    /**
     * The java.util.Date instance for this date
     */
    val date: java.util.Date
        get() = java.util.Date(epochTimeMs)
    
    /**
     * The year component of this date
     */
    val year: Int
        get() = calendar.get(Calendar.YEAR)
    
    /**
     * The month component of this date (1-12) or null if precision is year-only
     */
    val month: Int?
        get() {
            val cal = calendar
            return if (cal.get(Calendar.DAY_OF_MONTH) == 1 && cal.get(Calendar.MONTH) == 0) null
            else cal.get(Calendar.MONTH) + 1
        }
    
    /**
     * The day component of this date (1-31) or null if precision is year or month only
     */
    val day: Int?
        get() {
            val cal = calendar
            return if (cal.get(Calendar.DAY_OF_MONTH) == 1) null
            else cal.get(Calendar.DAY_OF_MONTH)
        }
    
    /**
     * Compares this date with another date
     */
    override fun compareTo(other: Date): Int = epochTimeMs.compareTo(other.epochTimeMs)
    
    /**
     * Returns a string representation of the date with the appropriate precision
     */
    override fun toString(): String {
        return when {
            day != null -> "$year-${month?.toString()?.padStart(2, '0')}-${day.toString().padStart(2, '0')}"
            month != null -> "$year-${month.toString().padStart(2, '0')}"
            else -> "$year"
        }
    }
    
    companion object {
        /**
         * Converts an integer year to a Date object
         */
        fun Int.toYearDate(): Date = Date(this)
    }
}