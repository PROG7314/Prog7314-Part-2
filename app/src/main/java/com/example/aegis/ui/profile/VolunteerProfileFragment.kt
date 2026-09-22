package com.example.aegis.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.aegis.R
import com.google.firebase.auth.FirebaseAuth

class VolunteerProfileFragment : Fragment() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_volunteer_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentUser = auth.currentUser
        if (currentUser != null) {
            val txtEmail = view.findViewById<TextView>(R.id.txtVolunteerEmail)
            txtEmail?.text = currentUser.email ?: "JulieBeckett2@gmail.com"
        }

        setupClickListeners(view)
    }

    private fun setupClickListeners(view: View) {
        view.findViewById<View>(R.id.btnEditProfile)?.setOnClickListener {
            Toast.makeText(requireContext(), "Edit Profile clicked", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.rowMyInterests)?.setOnClickListener {
            try {
                findNavController().navigate(R.id.interestMatchingFragment)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "My Interests clicked", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<View>(R.id.rowMySkills)?.setOnClickListener {
            Toast.makeText(requireContext(), "My Skills clicked", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.rowLanguageSettings)?.setOnClickListener {
            Toast.makeText(requireContext(), "Language Settings clicked", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.rowNotifications)?.setOnClickListener {
            Toast.makeText(requireContext(), "Notifications clicked", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.rowBiometricAuth)?.setOnClickListener {
            Toast.makeText(requireContext(), "Biometric Settings clicked", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.rowLogout)?.setOnClickListener {
            auth.signOut()
            Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show()
        }
    }
}