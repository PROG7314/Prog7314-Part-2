package com.example.aegis.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.aegis.R
import com.google.firebase.auth.FirebaseAuth

class OrgProfileFragment : Fragment() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_org_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Populate organization data
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val txtOrgEmail = view.findViewById<TextView>(R.id.txtOrgEmail)
            txtOrgEmail?.text = currentUser.email ?: "ChristineRevell@gmail.com"
        }

        setupClickListeners(view)
    }

    private fun setupClickListeners(view: View) {
        view.findViewById<View>(R.id.btnEditProfile)?.setOnClickListener {
            Toast.makeText(requireContext(), "Edit Profile clicked", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.rowLanguageSettings)?.setOnClickListener {
            Toast.makeText(requireContext(), "Language Settings clicked", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.rowBiometricAuth)?.setOnClickListener {
            Toast.makeText(requireContext(), "Biometric Settings clicked", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.rowNotifications)?.setOnClickListener {
            Toast.makeText(requireContext(), "Notifications clicked", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.rowLogout)?.setOnClickListener {
            auth.signOut()
            Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show()
        }
    }
}