/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.ui.desktop

import androidx.compose.ambientOf
import androidx.compose.Composable
import androidx.compose.emptyContent
import androidx.compose.Providers
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.WindowConstants

val AppWindowAmbient = ambientOf<AppWindow?>()

fun Window(
    title: String = "JetpackDesktopDialog",
    size: IntSize = IntSize(1024, 768),
    position: IntOffset = IntOffset(0, 0),
    isCentered: Boolean = true,
    onDismissEvent: (() -> Unit)? = null,
    content: @Composable () -> Unit = emptyContent()
) {
    AppWindow(
        title = title,
        size = size,
        position = position,
        onDismissEvent = onDismissEvent,
        centered = isCentered
    ).show {
        content()
    }
}

class AppWindow : AppFrame {

    constructor(
        title: String = "JetpackDesktopWindow",
        size: IntSize = IntSize(1024, 768),
        position: IntOffset = IntOffset(0, 0),
        onDismissEvent: (() -> Unit)? = null,
        centered: Boolean = true
    ) {
        this.title = title
        this.width = size.width
        this.height = size.height
        this.x = position.x
        this.y = position.y
        this.onDismissEvent = onDismissEvent
        isCentered = centered

        AppManager.addWindow(this)
    }

    override fun setSize(width: Int, height: Int) {
        // better check min/max values of current window size
        var w = width
        if (w <= 0) {
            w = this.width
        }

        var h = height
        if (h <= 0) {
            h = this.height
        }
        this.width = w
        this.height = h
        window?.setSize(w, h)
    }

    override fun setPosition(x: Int, y: Int) {
        this.x = x
        this.y = y
        window?.setLocation(x, y)
    }

    override fun setWindowCentered() {
        val dim: Dimension = Toolkit.getDefaultToolkit().getScreenSize()
        this.x = dim.width / 2 - width / 2
        this.y = dim.height / 2 - height / 2
        window?.setLocation(x, y)
    }

    var window: Window? = null
        private set

    private fun onCreate(content: @Composable () -> Unit) {

        window = Window(width = width, height = height, parent = this)

        window!!.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE

        window!!.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(windowevent: WindowEvent) {
                onDismissEvent?.invoke()
                window?.dispose()
            }
        })

        window!!.title = title

        window!!.setContent {
            Providers(
                AppWindowAmbient provides this,
                children = content
            )
        }

        if (isCentered)
            setWindowCentered()
        window!!.setVisible(true)
    }

    override fun show(content: @Composable () -> Unit) {
        onCreate {
            content()
        }
    }

    override fun close() {
        window?.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
        AppManager.removeWindow(this)
    }
}
