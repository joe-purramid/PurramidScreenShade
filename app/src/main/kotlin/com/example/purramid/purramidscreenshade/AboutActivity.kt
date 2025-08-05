package com.example.purramid.thepurramid

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val balsamiqLicenseText = getString(R.string.balsamiq_sans_license_text_part1) +
                getString(R.string.balsamiq_sans_license_text_part2) +
                getString(R.string.balsamiq_sans_license_text_part3)

        findViewById<TextView>(R.id.balsamiq_sans_license).text = balsamiqLicenseText

        val dataPrivacyText = getString(R.string.data_privacy_statement_part1) +
                getString(R.string.data_privacy_statement_part2) +
                getString(R.string.data_privacy_statement_part3)

        findViewById<TextView>(R.id.data_privacy_text).text = dataPrivacyText

        val bestPracticesText = getString(R.string.best_practices_content_part1) +
                getString(R.string.best_practices_content_part2) +
                getString(R.string.best_practices_content_part3) +
                getString(R.string.best_practices_content_part4) +
                getString(R.string.best_practices_content_part5) +
                getString(R.string.best_practices_content_part6) +
                getString(R.string.best_practices_content_part7) +
                getString(R.string.best_practices_content_part8) +
                getString(R.string.best_practices_content_part9)

        findViewById<TextView>(R.id.best_practices_text).text = bestPracticesText

        val acknowledgementsText = getString(R.string.acknowledgements_content_part1) +
                getString(R.string.acknowledgements_content_part2) +
                getString(R.string.acknowledgements_content_part3) +
                getString(R.string.acknowledgements_content_part4)

        findViewById<TextView>(R.id.acknowledgements_text).text = acknowledgementsText

        val eulaText = getString(R.string.eula_content_part1) +
                getString(R.string.eula_content_part2) +
                getString(R.string.eula_content_part3) +
                getString(R.string.eula_content_part4) +
                getString(R.string.eula_content_part5) +
                getString(R.string.eula_content_part6) +
                getString(R.string.eula_content_part7) +
                getString(R.string.eula_content_part8) +
                getString(R.string.eula_content_part9) +
                getString(R.string.eula_content_part10) +
                getString(R.string.eula_content_part11) +
                getString(R.string.eula_content_part12) +
                getString(R.string.eula_content_part13) +
                getString(R.string.eula_content_part14) +
                getString(R.string.eula_content_part15) +
                getString(R.string.eula_content_part16) +
                getString(R.string.eula_content_part17) +
                getString(R.string.eula_content_part18) +
                getString(R.string.eula_content_part19) +
                getString(R.string.eula_content_part20)

        findViewById<TextView>(R.id.eula_text).text = eulaText
    }
}