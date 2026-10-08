package app.forgetit.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll

class ForgetItWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ForgetItWidget()
}

/** Redraw every placed widget; failures are ignored because the widget is optional. */
suspend fun refreshWidgets(context: Context) {
    runCatching { ForgetItWidget().updateAll(context) }
}
