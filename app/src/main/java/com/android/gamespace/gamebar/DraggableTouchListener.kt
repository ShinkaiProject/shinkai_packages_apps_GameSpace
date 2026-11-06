/*
 * Copyright (C) 2021 Chaldeaprjkt
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
package com.android.gamespace.gamebar

import android.content.Context
import android.graphics.Point
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

import kotlin.math.hypot

class DraggableTouchListener(
    context: Context,
    private val view: View,
    private val initialPosition: () -> Point,
    private val positionListener: (x: Int, y: Int) -> Unit,
    private val onDragComplete: () -> Unit
) : View.OnTouchListener {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longClickInterval = ViewConfiguration.getLongPressTimeout()
    private var pointerStartX = 0
    private var pointerStartY = 0
    private var initialX = 0
    private var initialY = 0
    private var moving = false
    private var longClickPerformed = false

    // Runs on the main thread, and there is nothing to start for it: a Timer would be a new
    // thread for every touch on the bar.
    private val longClickRunnable = Runnable {
        if (!moving && !longClickPerformed) {
            longClickPerformed = true
            view.performLongClick()
        }
    }

    init {
        view.setOnTouchListener(this)
    }

    private fun scheduleLongClickTimer() {
        view.removeCallbacks(longClickRunnable)
        view.postDelayed(longClickRunnable, longClickInterval.toLong())
    }

    private fun cancelLongClickTimer() {
        view.removeCallbacks(longClickRunnable)
    }

    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> {
                pointerStartX = motionEvent.rawX.toInt()
                pointerStartY = motionEvent.rawY.toInt()
                with(initialPosition()) {
                    initialX = x
                    initialY = y
                }
                moving = false
                longClickPerformed = false
                scheduleLongClickTimer()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!longClickPerformed) {
                    val deltaX = motionEvent.rawX - pointerStartX
                    val deltaY = motionEvent.rawY - pointerStartY
                    if (moving || hypot(deltaX, deltaY) > touchSlop) {
                        cancelLongClickTimer()
                        positionListener(initialX + deltaX.toInt(), initialY + deltaY.toInt())
                        moving = true
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> cancelLongClickTimer()
            MotionEvent.ACTION_UP -> {
                cancelLongClickTimer()
                if (!moving && !longClickPerformed) {
                    view.performClick()
                }
                if (moving && !longClickPerformed) {
                    onDragComplete()
                }
            }
        }
        return true
    }
}
