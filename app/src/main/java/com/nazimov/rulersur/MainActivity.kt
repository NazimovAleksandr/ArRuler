package com.nazimov.rulersur

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ar_ruler.ARRulerActivity
import com.ar_ruler.StringValue

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.ruler_button).also {
            it.setOnClickListener {
                openArRulerActivity("Move Around")
            }
        }
    }

    private fun openArRulerActivity(screenName: String) {
        ARRulerActivity.start(
            context = this,
            stringValue = StringValue(
                a = "A",
                b = "B",
                addPointA = "Add Point A",
                addPointB = "Add Point B",
                moveAround = screenName,
                tooDark = "It's too dark",
                tooDarkDescription = "Additional lighting is needed. Please add more lighting",
                popUpInch = "in",
                popUpCentimeters = "centimeters",
                popUpTitle = "Get big and boost your size!",
                popUpSubtitle = "Say hello to epic growth with exercise program",
                popUpButton = "EXERCISES",
            ),
            onClickTutorial = {
                Toast.makeText(this, "Click Tutorial", Toast.LENGTH_SHORT).show()
                openArRulerActivity("Tutorial")
            },
            onClickExercises = {
                Toast.makeText(this, "Click Exercises", Toast.LENGTH_SHORT).show()
                openArRulerActivity("Exercises")
            }
        )
    }
}