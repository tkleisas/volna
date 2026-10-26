package org.bokontep.wavesynth;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A settings section with a clickable header that shows or hides its content. */
public final class CollapsibleSection {
    private final LinearLayout root;
    private final TextView header;
    private final LinearLayout content;
    private String title;
    private boolean expanded;

    public CollapsibleSection(Context context, String title, boolean expanded) {
        this.title = title;
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        header = new TextView(context);
        header.setTextSize(22);
        header.setTextColor(Color.LTGRAY);
        header.setPadding(0, 24, 0, 8);
        header.setClickable(true);
        header.setOnClickListener(v -> setExpanded(!this.expanded));

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        setExpanded(expanded);
    }

    public LinearLayout root() {
        return root;
    }

    public LinearLayout content() {
        return content;
    }

    public void addView(View view) {
        content.addView(view);
    }

    public void addView(View view, LinearLayout.LayoutParams params) {
        content.addView(view, params);
    }

    public void clear() {
        content.removeAllViews();
    }

    public void setTitle(String title) {
        this.title = title;
        updateHeader();
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        content.setVisibility(expanded ? View.VISIBLE : View.GONE);
        updateHeader();
    }

    public boolean isExpanded() {
        return expanded;
    }

    private void updateHeader() {
        header.setText((expanded ? "▼ " : "▶ ") + title);
    }
}
