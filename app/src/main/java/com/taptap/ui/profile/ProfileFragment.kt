package com.taptap.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.taptap.MainActivity
import com.taptap.R
import com.taptap.viewmodel.UserViewModel

class ProfileFragment : Fragment() {

    private var userViewModel: UserViewModel? = null

    // ui elements
    private lateinit var fullNameEdit: EditText
    private lateinit var emailEdit: EditText
    private lateinit var phoneEdit: EditText
    private lateinit var linkedInEdit: EditText
    private lateinit var descriptionEdit: EditText
    private lateinit var locationEdit: EditText
    private lateinit var saveButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_profile, container, false)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userViewModel = (requireActivity() as MainActivity).userViewModel

        // setup ui elements
        setupViews(view)
        setupSaveButton()
        loadCurrentProfile()
    }

    private fun setupViews(view: View) {
        // find all the views by id
        val textProfile = view.findViewById<TextView>(R.id.text_profile)
        textProfile.text = "Edit Your Profile"

        fullNameEdit = view.findViewById(R.id.full_name_edit)
        emailEdit = view.findViewById(R.id.email_edit)
        phoneEdit = view.findViewById(R.id.phone_edit)
        linkedInEdit = view.findViewById(R.id.linkedin_edit)
        descriptionEdit = view.findViewById(R.id.description_edit)
        locationEdit = view.findViewById(R.id.location_edit)
        saveButton = view.findViewById(R.id.save_button)
    }

    private fun setupSaveButton() {
        saveButton.setOnClickListener {
            saveProfile()
        }
    }

    private fun loadCurrentProfile() {
        // load current user data into the form
        userViewModel!!.currentUser.observe(viewLifecycleOwner) { user ->
            fullNameEdit.setText(user.fullName)
            emailEdit.setText(user.email)
            phoneEdit.setText(user.phone)
            linkedInEdit.setText(user.linkedIn)
            descriptionEdit.setText(user.description)
            locationEdit.setText(user.location)
        }
    }

    private fun saveProfile() {
        // get all the text from input fields
        val fullName = fullNameEdit.text.toString()
        val email = emailEdit.text.toString()
        val phone = phoneEdit.text.toString()
        val linkedIn = linkedInEdit.text.toString()
        val description = descriptionEdit.text.toString()
        val location = locationEdit.text.toString()

        if (fullName.isEmpty()) {
            showMessage("Please enter your full name")
            return
        }

        // save to global user data - this is what gets shared via nfc/qr
        userViewModel!!.saveUserProfile(fullName, phone, email, linkedIn, description, location)
        showMessage("Profile saved! Changes will show in Share tab")

        // clear keyboard focus
        fullNameEdit.clearFocus()
    }

    private fun showMessage(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
