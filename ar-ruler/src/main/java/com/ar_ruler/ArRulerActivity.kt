package com.ar_ruler

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class ArRulerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar_ruler)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            supportFragmentManager
                .beginTransaction()
                .add(R.id.fragment_container, ArRulerFragment())
                .commit()
        }
    }

    companion object {
        /**
         * @param stringValue defaultValue = StringValue(
         *             a = "A",
         *             b = "B",
         *             addPointA = "Add Point A",
         *             addPointB = "Add Point B",
         *             moveAround = "Move Around",
         *             tooDark = "It's too dark",
         *             tooDarkDescription = "Additional lighting is needed. Please add more lighting",
         *             popUpInch = "in",
         *             popUpCentimeters = "centimeters",
         *             popUpTitle = "Get big and boost your size!",
         *             popUpSubtitle = "Say hello to epic growth with exercise program",
         *             popUpButton = "EXERCISES",
         *         )
         */
        fun start(
            context: Context,
            stringValue: StringValue,
            onClickTutorial: (View) -> Unit,
        ) {
            ArRulerFragment.stringValue = stringValue
            ArRulerFragment.onClickTutorial = onClickTutorial

            context.startActivity(
                Intent(context, ArRulerActivity::class.java)
            )
        }
    }
}