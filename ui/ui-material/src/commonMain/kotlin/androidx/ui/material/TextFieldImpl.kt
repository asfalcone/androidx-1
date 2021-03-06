/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// TODO(b/160821157): Replace FocusDetailedState with FocusState2 DEPRECATION
@file:Suppress("DEPRECATION")

package androidx.ui.material

import androidx.compose.animation.core.FloatPropKey
import androidx.compose.animation.core.TransitionSpec
import androidx.compose.animation.core.transitionDefinition
import androidx.compose.animation.core.tween
import androidx.compose.Composable
import androidx.compose.Providers
import androidx.compose.Stable
import androidx.compose.getValue
import androidx.compose.mutableStateOf
import androidx.compose.remember
import androidx.compose.setValue
import androidx.compose.stateFor
import androidx.compose.structuralEqualityPolicy
import androidx.compose.animation.ColorPropKey
import androidx.compose.animation.DpPropKey
import androidx.compose.animation.transition
import androidx.compose.ui.unit.Constraints
import androidx.ui.core.Layout
import androidx.compose.ui.unit.LayoutDirection
import androidx.ui.core.LayoutModifier
import androidx.ui.core.Measurable
import androidx.ui.core.MeasureScope
import androidx.ui.core.Modifier
import androidx.ui.core.Placeable
import androidx.ui.core.Ref
import androidx.ui.core.clipToBounds
import androidx.compose.ui.unit.constrainWidth
import androidx.ui.core.focus.FocusModifier
import androidx.ui.core.focus.FocusState
import androidx.ui.core.focus.focusState
import androidx.ui.core.gesture.scrollorientationlocking.Orientation
import androidx.compose.ui.unit.offset
import androidx.compose.foundation.ContentColorAmbient
import androidx.compose.foundation.ProvideTextStyle
import androidx.compose.foundation.BaseTextField
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberScrollableController
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.preferredSizeIn
import androidx.ui.material.ripple.RippleIndication
import androidx.compose.runtime.savedinstancestate.Saver
import androidx.compose.runtime.savedinstancestate.rememberSavedInstanceState
import androidx.compose.ui.text.SoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.annotation.VisibleForTesting
import kotlin.math.min
import kotlin.math.roundToInt

internal enum class TextFieldType {
    Filled, Outlined
}

/**
 * Implementation of the [TextField] and [OutlinedTextField]
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun TextFieldImpl(
    type: TextFieldType,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier,
    textStyle: TextStyle,
    label: @Composable () -> Unit,
    placeholder: @Composable (() -> Unit)?,
    leading: @Composable (() -> Unit)?,
    trailing: @Composable (() -> Unit)?,
    isErrorValue: Boolean,
    visualTransformation: VisualTransformation,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onImeActionPerformed: (ImeAction, SoftwareKeyboardController?) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onTextInputStarted: (SoftwareKeyboardController) -> Unit,
    activeColor: Color,
    inactiveColor: Color,
    errorColor: Color,
    backgroundColor: Color,
    shape: Shape
) {
    @Suppress("DEPRECATION")
    val focusModifier = FocusModifier()
    val keyboardController: Ref<SoftwareKeyboardController> = remember { Ref() }

    val inputState = stateFor(value.text, focusModifier.focusState) {
        when {
            focusModifier.focusState == FocusState.Focused -> InputPhase.Focused
            value.text.isEmpty() -> InputPhase.UnfocusedEmpty
            else -> InputPhase.UnfocusedNotEmpty
        }
    }

    val decoratedPlaceholder: @Composable (() -> Unit)? =
        if (placeholder != null && inputState.value == InputPhase.Focused && value.text.isEmpty()) {
            {
                Decoration(
                    contentColor = inactiveColor,
                    typography = MaterialTheme.typography.subtitle1,
                    emphasis = EmphasisAmbient.current.medium,
                    children = placeholder
                )
            }
        } else null

    val decoratedTextField = @Composable { tagModifier: Modifier ->
        Decoration(
            contentColor = inactiveColor,
            typography = MaterialTheme.typography.subtitle1,
            emphasis = EmphasisAmbient.current.high
        ) {
            TextFieldScroller(
                scrollerPosition = rememberSavedInstanceState(
                    saver = TextFieldScrollerPosition.Saver
                ) {
                    TextFieldScrollerPosition()
                },
                modifier = tagModifier
            ) {
                BaseTextField(
                    value = value,
                    modifier = focusModifier,
                    textStyle = textStyle,
                    onValueChange = onValueChange,
                    onFocusChanged = onFocusChanged,
                    cursorColor = if (isErrorValue) errorColor else activeColor,
                    visualTransformation = visualTransformation,
                    keyboardType = keyboardType,
                    imeAction = imeAction,
                    onImeActionPerformed = {
                        onImeActionPerformed(it, keyboardController.value)
                    },
                    onTextInputStarted = {
                        keyboardController.value = it
                        onTextInputStarted(it)
                    }
                )
            }
        }
    }

    val textFieldModifier = modifier
        .clickable(indication = RippleIndication(bounded = false)) {
            focusModifier.requestFocus()
            keyboardController.value?.showSoftwareKeyboard()
        }

    val emphasisLevels = EmphasisAmbient.current

    TextFieldTransitionScope.transition(
        inputState = inputState.value,
        activeColor = if (isErrorValue) {
            errorColor
        } else {
            emphasisLevels.high.applyEmphasis(activeColor)
        },
        labelInactiveColor = emphasisLevels.medium.applyEmphasis(inactiveColor),
        indicatorInactiveColor = inactiveColor.applyAlpha(alpha = IndicatorInactiveAlpha)
    ) { labelProgress, animatedLabelColor, indicatorWidth, animatedIndicatorColor ->

        val leadingColor =
            inactiveColor.applyAlpha(alpha = TrailingLeadingAlpha)
        val trailingColor = if (isErrorValue) errorColor else leadingColor

        val decoratedLabel = @Composable {
            val labelAnimatedStyle = lerp(
                MaterialTheme.typography.subtitle1,
                MaterialTheme.typography.caption,
                labelProgress
            )
            Decoration(
                contentColor = animatedLabelColor,
                typography = labelAnimatedStyle,
                children = label
            )
        }

        when (type) {
            TextFieldType.Filled -> {
                FilledTextFieldLayout(
                    textFieldModifier = Modifier
                        .preferredSizeIn(
                            minWidth = TextFieldMinWidth,
                            minHeight = TextFieldMinHeight
                        )
                        .then(textFieldModifier),
                    decoratedTextField = decoratedTextField,
                    decoratedPlaceholder = decoratedPlaceholder,
                    decoratedLabel = decoratedLabel,
                    leading = leading,
                    trailing = trailing,
                    leadingColor = leadingColor,
                    trailingColor = trailingColor,
                    labelProgress = labelProgress,
                    indicatorWidth = indicatorWidth,
                    indicatorColor = animatedIndicatorColor,
                    backgroundColor = backgroundColor,
                    shape = shape
                )
            }
            TextFieldType.Outlined -> {
                OutlinedTextFieldLayout(
                    textFieldModifier = Modifier
                        .preferredSizeIn(
                            minWidth = TextFieldMinWidth,
                            minHeight = TextFieldMinHeight + OutlinedTextFieldTopPadding
                        )
                        .then(textFieldModifier)
                        .padding(top = OutlinedTextFieldTopPadding),
                    decoratedTextField = decoratedTextField,
                    decoratedPlaceholder = decoratedPlaceholder,
                    decoratedLabel = decoratedLabel,
                    leading = leading,
                    trailing = trailing,
                    leadingColor = leadingColor,
                    trailingColor = trailingColor,
                    labelProgress = labelProgress,
                    indicatorWidth = indicatorWidth,
                    indicatorColor = animatedIndicatorColor,
                    focusModifier = focusModifier,
                    emptyInput = value.text.isEmpty()
                )
            }
        }
    }
}

/**
 * Similar to [androidx.compose.foundation.ScrollableColumn] but does not lose the minWidth constraints.
 */
@VisibleForTesting
@Composable
internal fun TextFieldScroller(
    scrollerPosition: TextFieldScrollerPosition,
    modifier: Modifier = Modifier,
    textField: @Composable () -> Unit
) {
    Layout(
        modifier = modifier
            .clipToBounds()
            .scrollable(
                orientation = Orientation.Vertical,
                canScroll = { scrollerPosition.maximum != 0f },
                controller = rememberScrollableController { delta ->
                    val newPosition = scrollerPosition.current + delta
                    val consumedDelta = when {
                        newPosition > scrollerPosition.maximum ->
                            scrollerPosition.maximum - scrollerPosition.current // too much down
                        newPosition < 0f -> -scrollerPosition.current // scrolled too much up
                        else -> delta
                    }
                    scrollerPosition.current += consumedDelta
                    consumedDelta
                }
            ),
        children = textField,
        measureBlock = { measurables, constraints ->
            val childConstraints = constraints.copy(maxHeight = Constraints.Infinity)
            val placeable = measurables.first().measure(childConstraints)
            val height = min(placeable.height, constraints.maxHeight)
            val diff = placeable.height.toFloat() - height.toFloat()
            layout(placeable.width, height) {
                // update current and maximum positions to correctly calculate delta in scrollable
                scrollerPosition.maximum = diff
                if (scrollerPosition.current > diff) scrollerPosition.current = diff

                val yOffset = scrollerPosition.current - diff
                placeable.place(0, yOffset.roundToInt())
            }
        }
    )
}

@VisibleForTesting
@Stable
internal class TextFieldScrollerPosition(private val initial: Float = 0f) {
    var current by mutableStateOf(initial, structuralEqualityPolicy())
    var maximum by mutableStateOf(Float.POSITIVE_INFINITY, structuralEqualityPolicy())

    companion object {
        val Saver = Saver<TextFieldScrollerPosition, Float>(
            save = { it.current },
            restore = { restored ->
                TextFieldScrollerPosition(
                    initial = restored
                )
            }
        )
    }
}

/**
 * Set alpha if the color is not translucent
 */
internal fun Color.applyAlpha(alpha: Float): Color {
    return if (this.alpha != 1f) this else this.copy(alpha = alpha)
}

/**
 * Set content color, typography and emphasis for [children] composable
 */
@Composable
internal fun Decoration(
    contentColor: Color,
    typography: TextStyle? = null,
    emphasis: Emphasis? = null,
    children: @Composable () -> Unit
) {
    val colorAndEmphasis = @Composable {
        Providers(ContentColorAmbient provides contentColor) {
            if (emphasis != null) ProvideEmphasis(
                emphasis,
                children
            ) else children()
        }
    }
    if (typography != null) ProvideTextStyle(typography, colorAndEmphasis) else colorAndEmphasis()
}

private val Placeable.nonZero: Boolean get() = this.width != 0 || this.height != 0
internal fun widthOrZero(placeable: Placeable?) = placeable?.width ?: 0
internal fun heightOrZero(placeable: Placeable?) = placeable?.height ?: 0

/**
 * A modifier that applies padding only if the size of the element is not zero
 */
internal fun Modifier.iconPadding(start: Dp = 0.dp, end: Dp = 0.dp) =
    this.then(object : LayoutModifier {
        override fun MeasureScope.measure(
            measurable: Measurable,
            constraints: Constraints,
            layoutDirection: LayoutDirection
        ): MeasureScope.MeasureResult {
            val horizontal = start.toIntPx() + end.toIntPx()
            val placeable = measurable.measure(constraints.offset(-horizontal))
            val width = if (placeable.nonZero) {
                constraints.constrainWidth(placeable.width + horizontal)
            } else {
                0
            }
            return layout(width, placeable.height) {
                placeable.place(start.toIntPx(), 0)
            }
        }
    })

private object TextFieldTransitionScope {
    private val LabelColorProp = ColorPropKey()
    private val LabelProgressProp = FloatPropKey()
    private val IndicatorColorProp = ColorPropKey()
    private val IndicatorWidthProp = DpPropKey()

    @Composable
    fun transition(
        inputState: InputPhase,
        activeColor: Color,
        labelInactiveColor: Color,
        indicatorInactiveColor: Color,
        children: @Composable (
            labelProgress: Float,
            labelColor: Color,
            indicatorWidth: Dp,
            indicatorColor: Color
        ) -> Unit
    ) {
        val definition = remember(activeColor, labelInactiveColor, indicatorInactiveColor) {
            generateLabelTransitionDefinition(
                activeColor,
                labelInactiveColor,
                indicatorInactiveColor
            )
        }
        val state = transition(definition = definition, toState = inputState)
        children(
            state[LabelProgressProp],
            state[LabelColorProp],
            state[IndicatorWidthProp],
            state[IndicatorColorProp]
        )
    }

    private fun generateLabelTransitionDefinition(
        activeColor: Color,
        labelInactiveColor: Color,
        indicatorInactiveColor: Color
    ) = transitionDefinition {
        state(InputPhase.Focused) {
            this[LabelColorProp] = activeColor
            this[IndicatorColorProp] = activeColor
            this[LabelProgressProp] = 1f
            this[IndicatorWidthProp] =
                IndicatorFocusedWidth
        }
        state(InputPhase.UnfocusedEmpty) {
            this[LabelColorProp] = labelInactiveColor
            this[IndicatorColorProp] = indicatorInactiveColor
            this[LabelProgressProp] = 0f
            this[IndicatorWidthProp] =
                IndicatorUnfocusedWidth
        }
        state(InputPhase.UnfocusedNotEmpty) {
            this[LabelColorProp] = labelInactiveColor
            this[IndicatorColorProp] = indicatorInactiveColor
            this[LabelProgressProp] = 1f
            this[IndicatorWidthProp] = 1.dp
        }

        transition(fromState = InputPhase.Focused, toState = InputPhase.UnfocusedEmpty) {
            labelTransition()
            indicatorTransition()
        }
        transition(fromState = InputPhase.Focused, toState = InputPhase.UnfocusedNotEmpty) {
            indicatorTransition()
        }
        transition(fromState = InputPhase.UnfocusedNotEmpty, toState = InputPhase.Focused) {
            indicatorTransition()
        }
        transition(fromState = InputPhase.UnfocusedEmpty, toState = InputPhase.Focused) {
            labelTransition()
            indicatorTransition()
        }
        // below states are needed to support case when a single state is used to control multiple
        // text fields.
        transition(fromState = InputPhase.UnfocusedNotEmpty, toState = InputPhase.UnfocusedEmpty) {
            labelTransition()
        }
        transition(fromState = InputPhase.UnfocusedEmpty, toState = InputPhase.UnfocusedNotEmpty) {
            labelTransition()
        }
    }

    private fun TransitionSpec<InputPhase>.indicatorTransition() {
        IndicatorColorProp using tween(durationMillis = AnimationDuration)
        IndicatorWidthProp using tween(durationMillis = AnimationDuration)
    }

    private fun TransitionSpec<InputPhase>.labelTransition() {
        LabelColorProp using tween(durationMillis = AnimationDuration)
        LabelProgressProp using tween(durationMillis = AnimationDuration)
    }
}

/**
 * An internal state used to animate a label and an indicator.
 */
private enum class InputPhase {
    // Text field is focused
    Focused,
    // Text field is not focused and input text is empty
    UnfocusedEmpty,
    // Text field is not focused but input text is not empty
    UnfocusedNotEmpty
}

internal const val TextFieldId = "TextField"
internal const val PlaceholderId = "Hint"
internal const val LabelId = "Label"

private const val AnimationDuration = 150
private val IndicatorUnfocusedWidth = 1.dp
private val IndicatorFocusedWidth = 2.dp
private const val IndicatorInactiveAlpha = 0.42f
private const val TrailingLeadingAlpha = 0.54f
private val TextFieldMinHeight = 56.dp
private val TextFieldMinWidth = 280.dp
internal val TextFieldPadding = 16.dp
internal val HorizontalIconPadding = 12.dp

/*
This padding is used to allow label not overlap with the content above it. This 8.dp will work
for default cases when developers do not override the label's font size. If they do, they will
need to add additional padding themselves
*/
private val OutlinedTextFieldTopPadding = 8.dp