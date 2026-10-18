package app.listenandwrite.ui

import java.time.Instant
import java.time.ZoneId

fun formatWhen(epochMs: Long): String {
    val date = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDateTime()
    return "%d月%d日 %02d:%02d".format(date.monthValue, date.dayOfMonth, date.hour, date.minute)
}
