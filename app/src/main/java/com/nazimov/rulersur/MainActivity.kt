package com.nazimov.rulersur

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.ar_ruler.RulerActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.ruler_button).also {
            it.setOnClickListener {
                startActivity(
                    Intent(this, RulerActivity::class.java)
                )
            }
        }
    }
}