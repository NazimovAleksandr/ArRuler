package com.never_sleep

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class NeverSleepActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_never_sleep)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<TextView>(R.id.tutorial_text).also { tutorial ->
            tutorialText?.let { tutorial.text = it }
        }

        findViewById<TextView>(R.id.never_sleep_title).also { title ->
            neverSleepTitle?.let { title.text = it }
        }

        findViewById<TextView>(R.id.button_ok_text).also { buttonOk ->
            buttonOkText?.let { buttonOk.text = it }
        }

        findViewById<CardView>(R.id.button_ok).also { buttonOk ->
            buttonOk.setOnClickListener { finish() }

            try {
                buttonOkColor?.let {
                    val color = Color.parseColor(it)
                    buttonOk.setCardBackgroundColor(color)
                }
            } catch (ignore: Exception) {
            }
        }

        findViewById<ImageView>(R.id.ic_plus).also { buttonPlus ->
            buttonPlus.setOnClickListener { finish() }
        }
    }

    companion object {
        private var tutorialText: String? = null
        private var neverSleepTitle: String? = null
        private var buttonOkText: String? = null
        private var buttonOkColor: String? = null

        private val intent = Intent().also { intent ->
            intent.action = "com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY"
            intent.`package` = "com.samsung.android.lool"
            intent.putExtra("activity_type", 2)
        }

        fun isAvailable(context: Context): Boolean {
            return context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL).isNotEmpty()
        }

        /**
         * @param buttonOkColor -> #00B84C
         */
        fun start(
            context: Context,
            tutorialText: String? = null,
            neverSleepTitle: String? = null,
            buttonOkText: String? = null,
            buttonOkColor: String? = null,
        ) {
            this.tutorialText = tutorialText
            this.neverSleepTitle = neverSleepTitle
            this.buttonOkText = buttonOkText
            this.buttonOkColor = buttonOkColor

            if (isAvailable(context)) {
                context.startActivity(intent)
                context.startActivity(Intent(context, NeverSleepActivity::class.java))
            }
        }
    }
}