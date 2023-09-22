package com.ar_ruler.halpers

import android.content.Context
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

class TapHelper(
    context: Context,
) : View.OnTouchListener {

    private var gestureDetector: GestureDetector = GestureDetector(
        context,
        object : SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                queuedSingleTaps.offer(e)
                return true
            }

            override fun onDown(e: MotionEvent): Boolean {
                return true
            }
        }
    )

    // TODO: remove ?
    private val queuedSingleTaps: BlockingQueue<MotionEvent> = ArrayBlockingQueue(16)

    override fun onTouch(v: View?, event: MotionEvent): Boolean {
        v?.performClick()
        return gestureDetector.onTouchEvent(event)
    }
}