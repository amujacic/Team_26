package com.tugraz.chronos

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.tugraz.chronos.model.service.ChronosService

class TaskDetailsActivity : AppCompatActivity() {

    lateinit var chronosService: ChronosService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_details)

        val actionBar = supportActionBar
        actionBar!!.title = resources.getString(R.string.app_name)

        chronosService = ChronosService(this)

        // read id of button which pressed details
        val b = intent.extras
        var value = 0
        if (b != null) {
            value = b.getInt("id")
        }

        val task = chronosService.getTaskById(value.toLong())

        if (task != null){
            val title: TextView = findViewById(R.id.tv_td_title)
            title.text = task.title

            val date: TextView = findViewById(R.id.tv_td_date)
            date.text = task.date

            val group: TextView = findViewById(R.id.tv_td_group)
            group.text = task.groupId.toString()

            val description: TextView = findViewById(R.id.tv_td_description)
            description.text = task.description

            chronosService = ChronosService(this)
        }
    }
}