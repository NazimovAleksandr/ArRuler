package com.nazimov.rulersur

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ar_ruler.ArRulerActivity
import com.ar_ruler.StringValue

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.ruler_button).also {
            it.setOnClickListener {
                ArRulerActivity.start(
                    context = this,
                    stringValue = StringValue(
                        a = "A",
                        b = "B",
                        addPointA = "Add Point A",
                        addPointB = "Add Point B",
                        moveAround = "Move Around",
                        tooDark = "It's too dark",
                        tooDarkDescription = "Additional lighting is needed. Please add more lighting",
                        popUpInch = "in",
                        popUpCentimeters = "centimeters",
                        popUpTitle = "Get big and boost your size!",
                        popUpSubtitle = "Say hello to epic growth with exercise program",
                        popUpButton = "EXERCISES",
                    ),
                    onClickTutorial = {
                        Toast.makeText(this, "onClickTutorial", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }
}