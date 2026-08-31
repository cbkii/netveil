package io.github.cbkii.netveil.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import io.github.cbkii.netveil.R;

/**
 * Tiny platform-only design system for NetVeil.
 *
 * <p>Keeps spacing, colour, control hierarchy and semantic status treatment consistent without
 * AndroidX, Material Components, Compose, bundled fonts or bitmap UI assets.</p>
 */
public final class UiFactory {
    public enum Tone { NEUTRAL, INFO, SUCCESS, WARNING, ERROR }
    public enum ButtonKind { PRIMARY, TONAL, OUTLINE, TEAL, ERROR }

    private static final int SPACE_4 = 4;
    private static final int SPACE_8 = 8;
    private static final int SPACE_12 = 12;
    private static final int SPACE_16 = 16;
    private static final int SPACE_20 = 20;
    private static final int CARD_RADIUS = 20;
    private static final int CONTROL_RADIUS = 12;

    private final Context context;

    public UiFactory(Context context) {
        this.context = context;
    }

    public int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public int color(int id) {
        return context.getColor(id);
    }

    public LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public LinearLayout row() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    public LinearLayout card() {
        LinearLayout card = vertical();
        card.setPadding(dp(SPACE_20), dp(SPACE_16), dp(SPACE_20), dp(SPACE_20));
        card.setBackground(shape(R.color.nv_surface, CARD_RADIUS, 0, 0));
        card.setLayoutParams(blockParams(SPACE_16));
        return card;
    }

    public LinearLayout innerCard() {
        LinearLayout card = vertical();
        card.setPadding(dp(SPACE_16), dp(SPACE_16), dp(SPACE_16), dp(SPACE_16));
        card.setBackground(shape(R.color.nv_surface_container, CARD_RADIUS, R.color.nv_outline, 1));
        return card;
    }

    public LinearLayout.LayoutParams blockParams(int bottomMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(bottomMarginDp);
        return params;
    }

    public LinearLayout.LayoutParams weightedParams(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    public LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public TextView appTitle(String text) {
        return text(text, 28, true, R.color.nv_text_primary);
    }

    public TextView sectionTitle(String text) {
        TextView view = text(text, 20, true, R.color.nv_text_primary);
        view.setPadding(0, 0, 0, dp(SPACE_12));
        return view;
    }

    public TextView subheading(String text) {
        return text(text, 17, true, R.color.nv_text_primary);
    }

    public TextView label(String text) {
        TextView view = text(text, 14, true, R.color.nv_text_secondary);
        view.setPadding(0, dp(SPACE_8), 0, dp(SPACE_4));
        return view;
    }

    public TextView body(String text) {
        return text(text, 15, false, R.color.nv_text_primary);
    }

    public TextView helper(String text) {
        TextView view = text(text, 13, false, R.color.nv_text_secondary);
        view.setLineSpacing(0, 1.12f);
        view.setPadding(0, dp(SPACE_4), 0, dp(SPACE_8));
        return view;
    }

    public TextView chip(String text, Tone tone) {
        TextView view = text(text, 12, true, textColorFor(tone));
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(32));
        view.setPadding(dp(SPACE_12), dp(SPACE_4), dp(SPACE_12), dp(SPACE_4));
        setChip(view, tone, text);
        return view;
    }

    public void setChip(TextView view, Tone tone, String text) {
        view.setText(text);
        view.setTextColor(color(textColorFor(tone)));
        view.setBackground(shape(containerColorFor(tone), 16, 0, 0));
    }

    public TextView status(String text, Tone tone) {
        TextView view = text(text, 13, false, textColorFor(tone));
        view.setLineSpacing(0, 1.10f);
        setStatus(view, tone, text);
        return view;
    }

    public void setStatus(TextView view, Tone tone, String text) {
        view.setText(text);
        view.setTextColor(color(textColorFor(tone)));
        view.setPadding(dp(SPACE_12), dp(10), dp(SPACE_12), dp(10));
        view.setBackground(shape(containerColorFor(tone), CONTROL_RADIUS, 0, 0));
        view.setVisibility(text == null || text.isBlank() ? View.GONE : View.VISIBLE);
    }

    public EditText input(boolean multiLine) {
        EditText field = new EditText(context);
        styleInput(field, multiLine);
        return field;
    }

    public void styleInput(EditText field, boolean multiLine) {
        field.setTextColor(color(R.color.nv_text_primary));
        field.setHintTextColor(color(R.color.nv_text_subtle));
        field.setTextSize(15);
        field.setPadding(dp(SPACE_16), dp(SPACE_12), dp(SPACE_16), dp(SPACE_12));
        field.setBackground(shape(
                R.color.nv_surface_container_high, CONTROL_RADIUS, R.color.nv_outline, 1));
        field.setMinHeight(dp(52));
        field.setLayoutParams(matchWrap());
        if (multiLine) {
            field.setMinLines(3);
            field.setGravity(Gravity.TOP | Gravity.START);
        } else {
            field.setSingleLine(true);
        }
    }

    public Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setLayoutParams(matchWrap());
        spinner.setMinimumHeight(dp(52));
        spinner.setPadding(dp(SPACE_8), 0, dp(SPACE_8), 0);
        // Keep the platform dropdown affordance/ripple instead of replacing its background drawable.
        spinner.setBackgroundTintList(ColorStateList.valueOf(color(R.color.nv_text_secondary)));
        return spinner;
    }

    public Switch switchControl(String label, boolean checked) {
        Switch control = new Switch(context);
        control.setText(label);
        control.setTextColor(color(R.color.nv_text_primary));
        control.setTextSize(15);
        control.setChecked(checked);
        control.setMinHeight(dp(52));
        control.setGravity(Gravity.CENTER_VERTICAL);
        control.setShowText(false);
        control.setPadding(0, dp(SPACE_4), 0, dp(SPACE_4));

        int[][] states = new int[][] {
                new int[] { android.R.attr.state_checked },
                new int[] {}
        };
        control.setThumbTintList(new ColorStateList(
                states, new int[] {
                        color(R.color.nv_secondary), color(R.color.nv_text_subtle)
                }));
        control.setTrackTintList(new ColorStateList(
                states, new int[] {
                        color(R.color.nv_secondary_container),
                        color(R.color.nv_surface_container_high)
                }));
        return control;
    }

    public RadioGroup choiceGroup(String[] labels, boolean vertical) {
        RadioGroup group = new RadioGroup(context);
        group.setOrientation(vertical ? RadioGroup.VERTICAL : RadioGroup.HORIZONTAL);
        group.setLayoutParams(matchWrap());

        for (int i = 0; i < labels.length; i++) {
            RadioButton option = new RadioButton(context);
            option.setId(View.generateViewId());
            option.setText(labels[i]);
            option.setTextSize(14);
            option.setGravity(Gravity.CENTER_VERTICAL);
            option.setMinHeight(dp(50));
            option.setPadding(dp(SPACE_12), dp(SPACE_8), dp(SPACE_12), dp(SPACE_8));
            option.setTextColor(choiceTextColors());
            option.setBackground(choiceBackground());

            LinearLayout.LayoutParams params = vertical
                    ? matchWrap()
                    : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) {
                if (vertical) params.topMargin = dp(SPACE_8);
                else params.leftMargin = dp(SPACE_8);
            }
            group.addView(option, params);
        }
        return group;
    }

    public void setChoice(RadioGroup group, int position) {
        if (position < 0 || position >= group.getChildCount()) return;
        group.check(group.getChildAt(position).getId());
    }

    public int choiceIndex(RadioGroup group) {
        int id = group.getCheckedRadioButtonId();
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i).getId() == id) return i;
        }
        return 0;
    }

    public Button button(String label, ButtonKind kind) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setMinHeight(dp(52));
        button.setPadding(dp(SPACE_16), dp(SPACE_8), dp(SPACE_16), dp(SPACE_8));
        styleButton(button, kind);
        return button;
    }

    public void styleButton(Button button, ButtonKind kind) {
        int fill;
        int text;
        int outline = 0;
        switch (kind) {
            case PRIMARY -> {
                fill = R.color.nv_primary;
                text = R.color.nv_on_primary;
            }
            case TONAL -> {
                fill = R.color.nv_primary_container;
                text = R.color.nv_on_primary_container;
            }
            case TEAL -> {
                fill = R.color.nv_secondary_container;
                text = R.color.nv_on_secondary_container;
            }
            case ERROR -> {
                fill = R.color.nv_error_container;
                text = R.color.nv_error_text;
            }
            case OUTLINE -> {
                fill = R.color.nv_surface_container;
                text = R.color.nv_text_primary;
                outline = R.color.nv_outline;
            }
            default -> throw new IllegalStateException("Unexpected button kind: " + kind);
        }
        button.setTextColor(color(text));
        button.setBackground(shape(fill, CONTROL_RADIUS, outline, outline == 0 ? 0 : 1));
    }

    public View divider() {
        View view = new View(context);
        view.setBackgroundColor(color(R.color.nv_outline));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.topMargin = dp(SPACE_12);
        params.bottomMargin = dp(SPACE_12);
        view.setLayoutParams(params);
        return view;
    }

    private TextView text(String value, float sizeSp, boolean bold, int colorId) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color(colorId));
        view.setTypeface(android.graphics.Typeface.create(
                "sans", bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
        return view;
    }

    private ColorStateList choiceTextColors() {
        int[][] states = new int[][] {
                new int[] { android.R.attr.state_checked },
                new int[] {}
        };
        return new ColorStateList(states, new int[] {
                color(R.color.nv_on_primary_container),
                color(R.color.nv_text_secondary)
        });
    }

    private StateListDrawable choiceBackground() {
        StateListDrawable list = new StateListDrawable();
        list.addState(new int[] { android.R.attr.state_checked },
                shape(R.color.nv_primary_container, CONTROL_RADIUS, R.color.nv_primary, 1));
        list.addState(new int[] {},
                shape(R.color.nv_surface_container_high, CONTROL_RADIUS, R.color.nv_outline, 1));
        return list;
    }

    private GradientDrawable shape(
            int fillColorId, int radiusDp, int strokeColorId, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color(fillColorId));
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeColorId != 0 && strokeDp > 0) {
            drawable.setStroke(dp(strokeDp), color(strokeColorId));
        }
        return drawable;
    }

    private int containerColorFor(Tone tone) {
        return switch (tone) {
            case INFO -> R.color.nv_info_container;
            case SUCCESS -> R.color.nv_success_container;
            case WARNING -> R.color.nv_warning_container;
            case ERROR -> R.color.nv_error_container;
            case NEUTRAL -> R.color.nv_surface_container_high;
        };
    }

    private int textColorFor(Tone tone) {
        return switch (tone) {
            case INFO -> R.color.nv_info_text;
            case SUCCESS -> R.color.nv_success_text;
            case WARNING -> R.color.nv_warning_text;
            case ERROR -> R.color.nv_error_text;
            case NEUTRAL -> R.color.nv_text_secondary;
        };
    }
}
