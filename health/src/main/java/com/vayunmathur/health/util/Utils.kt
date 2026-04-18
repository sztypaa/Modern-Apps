package com.vayunmathur.health.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding

fun LocalDate.displayString() = this.format(LocalDate.Format {
    monthName(MonthNames.ENGLISH_ABBREVIATED)
    chars(" ")
    day(Padding.NONE)
    chars(", ")
    year()
})