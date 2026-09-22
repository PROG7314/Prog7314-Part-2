package com.example.aegis

import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.aegis.ui.main.VolunteerHomeViewModel

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vm: VolunteerHomeViewModel by viewModels { VolunteerHomeViewModel.Factory }
        Log.d("SmokeTest", "ViewModel created: $vm") // forces the lazy delegate to run

        setContentView(R.layout.activity_main)
    }

}