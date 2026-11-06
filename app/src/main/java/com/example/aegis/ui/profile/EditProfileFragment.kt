package com.example.aegis.ui.profile

import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.aegis.R
import com.example.aegis.core.AegisBaseFragment
import com.example.aegis.core.Taxonomy
import com.example.aegis.databinding.FragmentEditProfileBinding
import com.example.aegis.domain.model.UserProfile
import com.example.aegis.ui.main.UiState
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.launch

/** Edit the signed-in user's profile. Volunteers edit name, bio, causes and skills; organisations name and description. */
class EditProfileFragment : AegisBaseFragment() {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EditProfileViewModel by viewModels { EditProfileViewModel.Factory }

    private val causeChips = mutableMapOf<String, Chip>()
    private val skillChips = mutableMapOf<String, Chip>()
    private var isOrg = false
    private var populated = false
    private var currentImageUrl: String? = null

    // The system photo picker needs no storage permission.
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.setPickedPhoto(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val openPicker = {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.avatarCard.setOnClickListener { openPicker() }
        binding.changePhotoButton.setOnClickListener { openPicker() }
        binding.saveButton.setOnClickListener { onSaveClicked() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.profile.collect { renderProfile(it) } }
                launch { viewModel.saveState.collect { renderSave(it) } }
                launch { viewModel.pickedPhoto.collect { showAvatar(it) } }
            }
        }
    }

    private fun renderProfile(state: UiState<UserProfile>) {
        when (state) {
            is UiState.Loading, is UiState.Empty -> Unit
            is UiState.Error -> {
                Toast.makeText(requireContext(), state.error.message, Toast.LENGTH_LONG).show()
                findNavController().popBackStack()
            }
            is UiState.Success -> populate(state.data)
        }
    }

    // Fills the form once; later emissions (e.g. after a rotation) must not overwrite what the user typed.
    private fun populate(profile: UserProfile) {
        if (populated) return
        populated = true
        isOrg = profile.role == "organization"
        currentImageUrl = profile.profileImageUrl

        binding.nameInput.setText(profile.name)
        binding.aboutLayout.hint = getString(if (isOrg) R.string.about_us else R.string.about_me)
        binding.aboutLayout.counterMaxLength = ProfileValidator.aboutMax(isOrg)
        binding.aboutInput.setText((if (isOrg) profile.description else profile.bio).orEmpty())

        binding.interestsSection.visibility = if (isOrg) View.GONE else View.VISIBLE
        if (!isOrg) {
            fillChips(binding.causesChipGroup, Taxonomy.causes, profile.interests.toSet(), causeChips)
            fillChips(binding.skillsChipGroup, Taxonomy.skills, profile.skills.toSet(), skillChips)
        }

        showAvatar(viewModel.pickedPhoto.value)
        binding.loadingIndicator.visibility = View.GONE
        binding.contentGroup.visibility = View.VISIBLE
    }

    /** Shows the newly picked photo if there is one, else the saved picture, else the placeholder icon. */
    private fun showAvatar(picked: Uri?) {
        val avatar = binding.avatarPreview
        val source: Any? = picked ?: currentImageUrl?.takeIf { it.isNotBlank() }
        if (source == null) {
            val pad = (18 * resources.displayMetrics.density).toInt()
            avatar.setPadding(pad, pad, pad, pad)
            avatar.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            avatar.imageTintList = ColorStateList.valueOf(MaterialColors.getColor(avatar, androidx.appcompat.R.attr.colorPrimary))
            avatar.setImageResource(R.drawable.profile)
        } else {
            // A real photo fills the circle; the padding and tint only suit the placeholder icon.
            avatar.setPadding(0, 0, 0, 0)
            avatar.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            avatar.imageTintList = null
            avatar.load(source) {
                placeholder(R.drawable.profile)
                error(R.drawable.profile)
            }
        }
    }

    private fun fillChips(group: ChipGroup, values: List<String>, selected: Set<String>, into: MutableMap<String, Chip>) {
        group.removeAllViews()
        into.clear()
        values.forEach { value ->
            val chip = Chip(requireContext()).apply {
                text = Taxonomy.label(requireContext(), value)
                isCheckable = true
                isChecked = value in selected
            }
            group.addView(chip)
            into[value] = chip
        }
    }

    private fun onSaveClicked() {
        val name = binding.nameInput.text.toString()
        val about = binding.aboutInput.text.toString()
        binding.nameLayout.error = null
        binding.aboutLayout.error = null

        when (ProfileValidator.validate(name, about, isOrg)) {
            ProfileValidator.Problem.NAME_REQUIRED ->
                binding.nameLayout.error = getString(R.string.error_name_required)
            ProfileValidator.Problem.NAME_TOO_LONG ->
                binding.nameLayout.error = getString(R.string.error_name_too_long, ProfileValidator.NAME_MAX)
            ProfileValidator.Problem.ABOUT_TOO_LONG ->
                binding.aboutLayout.error = getString(R.string.error_about_too_long, ProfileValidator.aboutMax(isOrg))
            null -> viewModel.save(
                isOrg, name, about,
                interests = causeChips.filterValues { it.isChecked }.keys.toList(),
                skills = skillChips.filterValues { it.isChecked }.keys.toList()
            )
        }
    }

    private fun renderSave(state: EditProfileViewModel.SaveState) {
        val saving = state is EditProfileViewModel.SaveState.Saving
        binding.saveButton.isEnabled = !saving
        binding.changePhotoButton.isEnabled = !saving
        binding.avatarCard.isEnabled = !saving
        binding.savingIndicator.visibility = if (saving) View.VISIBLE else View.GONE

        when (state) {
            is EditProfileViewModel.SaveState.Saved -> {
                viewModel.consumeSaveState()
                Toast.makeText(requireContext(), R.string.profile_saved, Toast.LENGTH_SHORT).show()
                // Tells ProfileFragment to reload, because its view model outlives this screen.
                findNavController().previousBackStackEntry?.savedStateHandle?.set(PROFILE_UPDATED, true)
                findNavController().popBackStack()
            }
            is EditProfileViewModel.SaveState.Error -> {
                viewModel.consumeSaveState()
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
            }
            else -> Unit
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val PROFILE_UPDATED = "profile_updated"
    }
}