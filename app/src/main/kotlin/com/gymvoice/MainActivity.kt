package com.gymvoice

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.gymvoice.databinding.ActivityMainBinding
import com.gymvoice.ui.CalendarFragment
import com.gymvoice.ui.ProgressFragment
import com.gymvoice.ui.RecordFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            showFragment(RecordFragment(), TAG_RECORD)
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_record -> {
                    showFragment(
                        supportFragmentManager.findFragmentByTag(TAG_RECORD) ?: RecordFragment(),
                        TAG_RECORD,
                    )
                    true
                }
                R.id.nav_calendar -> {
                    showFragment(
                        supportFragmentManager.findFragmentByTag(TAG_CALENDAR) ?: CalendarFragment(),
                        TAG_CALENDAR,
                    )
                    true
                }
                R.id.nav_progress -> {
                    showFragment(
                        supportFragmentManager.findFragmentByTag(TAG_PROGRESS) ?: ProgressFragment(),
                        TAG_PROGRESS,
                    )
                    true
                }
                else -> false
            }
        }
    }

    private fun showFragment(
        fragment: Fragment,
        tag: String,
    ) {
        val tx = supportFragmentManager.beginTransaction()
        supportFragmentManager.fragments.forEach { tx.hide(it) }
        val existing = supportFragmentManager.findFragmentByTag(tag)
        if (existing != null) tx.show(existing) else tx.add(R.id.fragmentContainer, fragment, tag)
        tx.commit()
    }

    companion object {
        private const val TAG_RECORD = "record"
        private const val TAG_CALENDAR = "calendar"
        private const val TAG_PROGRESS = "progress"
    }
}
