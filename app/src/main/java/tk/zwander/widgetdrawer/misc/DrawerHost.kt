package tk.zwander.widgetdrawer.misc

import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.WindowConfiguration
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import tk.zwander.widgetdrawer.views.Drawer
import tk.zwander.widgetdrawer.views.DrawerHostView
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * The super constructor here is technically a hidden API, but for whatever reason,
 * it's the one part of the OnClickHandler framework that's both accessible and
 * not blacklisted.
 *
 * On Pie, the RemoteViews$OnClickHandler class is actually blacklisted, but
 * it seems like the API blacklist doesn't actually work on classes themselves.
 * So it's possible to extend the class without much issue, and even override the methods.
 *
 * On Q, RemoteViews$OnClickHandler has been changed to an interface. While it's still possible
 * to implement it with a proxy, it has one method that's passed a View, PendingIntent, and
 * RemoteViews$RemoteResponse.
 *
 * Unfortunately, the RemoteResponse is now what contains the Intent that's needed to carry
 * out the click event, and that field is blacklisted. Currently, there doesn't seem to be
 * a way to intercept a widget click on Q, without setting Settings$Global.hidden_api_policy
 * to 1 or 0
 *
 * Proxy.newProxyInstance(
 * RemoteViews.OnClickHandler::class.java.classLoader,
 * arrayOf(RemoteViews.OnClickHandler::class.java),
 * InnerOnClickHandlerQ(drawer)
 * ) as RemoteViews.OnClickHandler
 */
class DrawerHost(val context: Context, id: Int, drawer: Drawer) : AppWidgetHost(
    context,
    id,
    if (RemoteViews.OnClickHandler::class.java.isInterface)
    Proxy.newProxyInstance(
        RemoteViews.OnClickHandler::class.java.classLoader,
        arrayOf(RemoteViews.OnClickHandler::class.java),
        InnerOnClickHandlerQ(drawer)
    ) as RemoteViews.OnClickHandler
    else InnerOnClickHandlerPie(drawer),
    Looper.getMainLooper()
) {
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        return DrawerHostView(context)
    }

    class InnerOnClickHandlerPie(private val drawer: Drawer) : RemoteViews.OnClickHandler() {
        private var enterAnimationId: Int = 0

        override fun onClickHandler(
            view: View,
            pendingIntent: PendingIntent,
            fillInIntent: Intent
        ): Boolean {
            return if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
                onClickHandler(view, pendingIntent, fillInIntent,
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) WindowConfiguration.WINDOWING_MODE_UNDEFINED
                    else ActivityManager.StackId.INVALID_STACK_ID)
            } else {
                if (pendingIntent.isActivity) {
                    drawer.hideDrawer()
                }

                super.onClickHandler(view, pendingIntent, fillInIntent)
            }
        }

        override fun onClickHandler(
            view: View,
            pendingIntent: PendingIntent,
            fillInIntent: Intent,
            windowingMode: Int
        ): Boolean {
            if (pendingIntent.isActivity) {
                drawer.hideDrawer()
            }

            return super.onClickHandler(view, pendingIntent, fillInIntent, windowingMode)
        }

        override fun setEnterAnimationId(enterAnimationId: Int) {
            this.enterAnimationId = enterAnimationId
        }
    }

    class InnerOnClickHandlerQ(private val drawer: Drawer) : InvocationHandler {
        override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>): Any {
            val view = args[0] as View
            val pi = args[1] as PendingIntent
            val response = args[2]

            val responseClass = Class.forName("android.widget.RemoteViews\$RemoteResponse")

            val getLaunchOptions = responseClass.getDeclaredMethod("getLaunchOptions", View::class.java)
            val startPendingIntent = RemoteViews::class.java.getDeclaredMethod(
                "startPendingIntent", View::class.java, PendingIntent::class.java, android.util.Pair::class.java)

            val launchOptions = getLaunchOptions.invoke(response, view) as android.util.Pair<Intent, ActivityOptions>

            if (pi.isActivity) drawer.hideDrawer()

            return startPendingIntent.invoke(null, view, pi, launchOptions) as Boolean
        }
    }
}