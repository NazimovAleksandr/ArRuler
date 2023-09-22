package com.ar_ruler

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.ar_ruler.halpers.ifNull

class RulerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ruler)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )

        supportFragmentManager.findFragmentById(R.id.fragment_container) ifNull {
            supportFragmentManager
                .beginTransaction()
                .add(R.id.fragment_container, RulerFragment())
                .commit()
        }
    }

    companion object {
        fun start(
            context: Context,
            stringValue: StringValue,
            onClickTutorial: (View) -> Unit,
        ) {
            RulerFragment.stringValue = stringValue
            RulerFragment.onClickTutorial = onClickTutorial

            context.startActivity(
                Intent(context, RulerActivity::class.java)
            )
        }
    }
}