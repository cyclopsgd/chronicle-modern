package local.oss.chronicle.features.currentlyplaying

import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.ConstraintSet.*
import androidx.databinding.BindingAdapter
import com.google.android.material.slider.Slider
import local.oss.chronicle.R
import local.oss.chronicle.application.MainActivityViewModel
import local.oss.chronicle.application.MainActivityViewModel.BottomSheetState.*
import timber.log.Timber

@BindingAdapter("bottomSheetState")
fun setBottomSheetState(
    parent: ConstraintLayout,
    state: MainActivityViewModel.BottomSheetState,
) {
    Timber.i("Bottom sheet state is $state")
    val constraints = ConstraintSet()
    constraints.clone(parent)
    when (state) {
        EXPANDED -> expandConstraint(constraints)
        COLLAPSED -> collapseConstraint(constraints)
        HIDDEN -> hideConstraint(constraints)
    }

    // Create a smooth transition with proper easing
    val transition = TransitionSet().apply {
        addTransition(ChangeBounds().apply {
            duration = 350
            interpolator = DecelerateInterpolator(2f)
        })
        addTransition(Fade().apply {
            duration = 200
        })
        ordering = TransitionSet.ORDERING_TOGETHER
    }

    TransitionManager.beginDelayedTransition(parent, transition)
    constraints.applyTo(parent)

    // Hide mini player handle when not collapsed
    val bottomSheetHandle = parent.findViewById<View>(R.id.currently_playing_handle)
    bottomSheetHandle.visibility = if (state == COLLAPSED) View.VISIBLE else View.GONE

    // Hide bottom navigation when expanded (full screen player)
    val bottomNav = parent.findViewById<View>(R.id.bottom_nav)
    bottomNav?.visibility = if (state == EXPANDED) View.GONE else View.VISIBLE
}

/**
 * Binding adapter to hide bottom navigation and mini player when car mode is active.
 * This provides a full-screen experience in car mode by:
 * 1. Hiding bottom nav, divider, and mini player handle
 * 2. Making the currently playing container height 0 (not GONE to preserve state)
 * 3. Extending the fragment container to the bottom of the screen
 *
 * NOTE: This adapter only takes action when entering car mode (isActive=true).
 * When exiting car mode, it triggers a refresh of the bottomSheetState to restore proper layout.
 */
@BindingAdapter("carModeActive")
fun setCarModeActive(
    parent: ConstraintLayout,
    isActive: Boolean,
) {
    Timber.i("Car mode active: $isActive")

    val bottomNav = parent.findViewById<View>(R.id.bottom_nav)
    val miniPlayerHandle = parent.findViewById<View>(R.id.currently_playing_handle)

    if (isActive) {
        // Hide bottom nav and mini player for full-screen car mode
        bottomNav?.visibility = View.GONE
        miniPlayerHandle?.visibility = View.GONE

        // Update constraints so fragNavHost extends to bottom when car mode is active
        val constraints = ConstraintSet()
        constraints.clone(parent)

        // Extend fragment container to bottom of parent
        constraints.connect(R.id.fragNavHost, BOTTOM, PARENT_ID, BOTTOM)

        // Collapse the currently_playing_container to zero height (preserves internal state)
        constraints.connect(R.id.currently_playing_container, TOP, PARENT_ID, BOTTOM)
        constraints.connect(R.id.currently_playing_container, BOTTOM, PARENT_ID, BOTTOM)

        constraints.applyTo(parent)
    } else {
        // Exiting car mode - restore bottom nav and mini player visibility
        bottomNav?.visibility = View.VISIBLE
        miniPlayerHandle?.visibility = View.VISIBLE

        // Restore constraints to normal collapsed state
        val constraints = ConstraintSet()
        constraints.clone(parent)

        // Restore fragment container to stop above currently_playing_container
        constraints.connect(R.id.fragNavHost, BOTTOM, R.id.currently_playing_container, TOP)

        // Restore currently_playing_container to collapsed position
        constraints.connect(R.id.currently_playing_container, TOP, R.id.currently_playing_collapsed_top, BOTTOM)
        constraints.connect(R.id.currently_playing_container, BOTTOM, R.id.bottom_nav, TOP)

        constraints.applyTo(parent)
    }
}

private fun collapseConstraint(constraintSet: ConstraintSet) {
    constraintSet.connect(
        R.id.currently_playing_container,
        TOP,
        R.id.currently_playing_collapsed_top,
        BOTTOM,
    )
    constraintSet.connect(R.id.currently_playing_container, BOTTOM, R.id.bottom_nav, TOP)
}

private fun expandConstraint(constraintSet: ConstraintSet) {
    // Full screen: extend from top to bottom of parent (bottom nav will be hidden)
    constraintSet.connect(R.id.currently_playing_container, TOP, PARENT_ID, TOP)
    constraintSet.connect(R.id.currently_playing_container, BOTTOM, PARENT_ID, BOTTOM)
}

private fun hideConstraint(constraintSet: ConstraintSet) {
    constraintSet.connect(R.id.currently_playing_container, TOP, R.id.bottom_nav, TOP)
    constraintSet.connect(R.id.currently_playing_container, BOTTOM, R.id.bottom_nav, TOP)
}

/**
 * Binding adapter that safely sets the Slider's valueTo property.
 * Ensures valueTo is always greater than valueFrom to prevent IllegalStateException.
 *
 * When chapter data is not yet loaded, duration may be 0, which would cause
 * the Slider to crash with "valueFrom(0.0) must be smaller than valueTo(0.0)".
 * This adapter ensures a minimum value of 1 is used when the duration is 0 or negative.
 */
@BindingAdapter("safeValueTo")
fun setSafeValueTo(
    slider: Slider,
    valueTo: Int,
) {
    // Ensure valueTo is always greater than valueFrom (which defaults to 0)
    // Use 1 as the minimum to prevent the IllegalStateException
    val safeValueTo =
        if (valueTo <= slider.valueFrom) {
            1f
        } else {
            valueTo.toFloat()
        }

    // Only update if the value has changed to avoid unnecessary updates
    if (slider.valueTo != safeValueTo) {
        slider.valueTo = safeValueTo

        // Also ensure the current value doesn't exceed the new maximum
        if (slider.value > safeValueTo) {
            slider.value = safeValueTo
        }
    }
}
