package com.alip.admin

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.alip.admin.databinding.ActivityPanelBinding

class PanelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPanelBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPanelBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}