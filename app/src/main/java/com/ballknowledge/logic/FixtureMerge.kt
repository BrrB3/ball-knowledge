package com.ballknowledge.logic

import com.ballknowledge.model.Fixture
import java.time.YearMonth

object FixtureMerge {
    /**
     * New results replace old ones by id. An old fixture missing from a month that was
     * just fetched is dropped (it moved or was cancelled), except near month edges where
     * the source's idea of the date may differ from ours.
     */
    fun merge(
        base: List<Fixture>,
        fresh: List<Fixture>,
        covered: Set<Pair<String, YearMonth>>,
    ): List<Fixture> {
        val freshIds = fresh.map { it.id }.toSet()
        val kept = base.filter { old ->
            if (old.id in freshIds) return@filter false
            val ym = TimeFormat.yearMonthUk(old.kickoffMillis)
            val day = TimeFormat.dayOfMonthUk(old.kickoffMillis)
            val interior = day in 2 until ym.lengthOfMonth()
            !((old.league to ym) in covered && interior)
        }
        return (kept + fresh).sortedBy { it.kickoffMillis }
    }
}
