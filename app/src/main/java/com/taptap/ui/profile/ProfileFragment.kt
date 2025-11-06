package com.taptap.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.taptap.MainActivity
import com.taptap.R
import com.taptap.viewmodel.UserViewModel
import com.google.android.material.textfield.TextInputEditText

class ProfileFragment : Fragment() {

    private var userViewModel: UserViewModel? = null

    // ui elements
    private lateinit var fullNameEdit: TextInputEditText
    private lateinit var descriptionEdit: TextInputEditText
    private lateinit var linkedInEdit: TextInputEditText
    private lateinit var websiteEdit: TextInputEditText
    private lateinit var saveButton: com.google.android.material.button.MaterialButton
    private lateinit var backButton: ImageButton

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
        setupBackButton()
        loadCurrentProfile()
    }

    private fun setupViews(view: View) {
        // find all the views by id
        fullNameEdit = view.findViewById(R.id.full_name_edit)
        descriptionEdit = view.findViewById(R.id.description_edit)
        linkedInEdit = view.findViewById(R.id.linkedin_edit)
        websiteEdit = view.findViewById(R.id.website_edit)
        saveButton = view.findViewById(R.id.save_button)
        backButton = view.findViewById(R.id.back_button)
    }

    private fun setupBackButton() {
        backButton.setOnClickListener {
            findNavController().navigateUp()
        }
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
            descriptionEdit.setText(user.description)
            linkedInEdit.setText(user.linkedIn)
            // For website, might want to add a website field to your User model
            // For now, using email as placeholder
            websiteEdit.setText(user.email)
        }
    }

    private fun saveProfile() {
        // get all the text from input fields
        val fullName = fullNameEdit.text.toString()
        val description = descriptionEdit.text.toString()
        val linkedIn = linkedInEdit.text.toString()
        val website = websiteEdit.text.toString()

        if (fullName.isEmpty()) {
            showMessage("Please enter your full name")
            return
        }

        // save to global user data using website as email for now
        // TODO: might want to update your User model to include website separately
        userViewModel!!.saveUserProfile(fullName, "", website, linkedIn, description, "")
        showMessage("Profile saved successfully!")

        // navigate back
        findNavController().navigateUp()
    }

    private fun showMessage(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
