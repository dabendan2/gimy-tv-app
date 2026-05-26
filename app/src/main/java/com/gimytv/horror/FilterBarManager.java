package com.gimytv.horror;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FilterBarManager {
    private static final String TAG = "GimyHorror_FilterBar";

    public interface FilterBarListener {
        void onFilterChanged(String sort, String region, String year);
    }

    private final Activity activity;
    private final LinearLayout filterContainer;
    private final FilterBarListener listener;

    private String selectedSort = "熱門推薦";
    private String selectedRegion = "全部";
    private String selectedYear = "全部";

    private final String[] SORTS = {"熱門推薦", "最新上架", "好評高分"};
    private final String[] REGIONS = {"全部", "泰國", "日本", "韓國", "美國", "台灣", "香港"};
    private final String[] YEARS = {"全部", "2026", "2025", "2024", "2023", "2022", "2021", "2020"};

    public FilterBarManager(Activity activity, LinearLayout filterContainer, FilterBarListener listener) {
        this.activity = activity;
        this.filterContainer = filterContainer;
        this.listener = listener;
        buildFilterUI();
    }

    private void buildFilterUI() {
        filterContainer.removeAllViews();
        filterContainer.addView(createFilterRow("排序：", SORTS, "Sort"));
        filterContainer.addView(createFilterRow("地區：", REGIONS, "Region"));
        filterContainer.addView(createFilterRow("年份：", YEARS, "Year"));
    }

    public String getSelectedSort() {
        return selectedSort;
    }

    public String getSelectedRegion() {
        return selectedRegion;
    }

    public String getSelectedYear() {
        return selectedYear;
    }

    private View createFilterRow(String label, final String[] options, final String type) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 5, 0, 5);

        TextView lbl = new TextView(activity);
        lbl.setText(label);
        lbl.setTextSize(14);
        lbl.setTextColor(Color.parseColor("#9AA0A6"));
        lbl.setPadding(0, 0, 20, 0);
        row.addView(lbl);

        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        final LinearLayout optionsLayout = new LinearLayout(activity);
        optionsLayout.setOrientation(LinearLayout.HORIZONTAL);

        for (final String opt : options) {
            final TextView item = new TextView(activity);
            item.setText(opt);
            item.setTextSize(14);
            item.setFocusable(true);
            item.setClickable(true);
            item.setPadding(30, 10, 30, 10);
            
            updateFilterItemStyle(item, opt, type, false);

            item.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        Log.i(TAG, "🎯 FocusState: Filter focused -> " + type + ": " + opt);
                    }
                    updateFilterItemStyle(item, opt, type, hasFocus);
                }
            });

            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if ("Sort".equals(type)) selectedSort = opt;
                    else if ("Region".equals(type)) selectedRegion = opt;
                    else if ("Year".equals(type)) selectedYear = opt;

                    // Refresh styles of all items in this row!
                    for (int i = 0; i < optionsLayout.getChildCount(); i++) {
                        View child = optionsLayout.getChildAt(i);
                        if (child instanceof TextView) {
                            TextView tv = (TextView) child;
                            String childOpt = tv.getText().toString();
                            updateFilterItemStyle(tv, childOpt, type, tv.hasFocus());
                        }
                    }

                    if (listener != null) {
                        listener.onFilterChanged(selectedSort, selectedRegion, selectedYear);
                    }
                }
            });

            optionsLayout.addView(item);
        }
        scroll.addView(optionsLayout);
        row.addView(scroll);
        return row;
    }

    private void updateFilterItemStyle(TextView item, String opt, String type, boolean hasFocus) {
        boolean isSelected = false;
        if ("Sort".equals(type)) isSelected = opt.equals(selectedSort);
        else if ("Region".equals(type)) isSelected = opt.equals(selectedRegion);
        else if ("Year".equals(type)) isSelected = opt.equals(selectedYear);

        if (hasFocus) {
            item.setBackgroundColor(Color.parseColor("#3C4043")); // focused dark grey
            item.setTextColor(Color.WHITE);
        } else if (isSelected) {
            if ("Sort".equals(type)) item.setBackgroundColor(Color.parseColor("#1A73E8")); // Blue tag
            else if ("Region".equals(type)) item.setBackgroundColor(Color.parseColor("#C5221F")); // Red tag
            else if ("Year".equals(type)) item.setBackgroundColor(Color.parseColor("#137333")); // Green tag
            item.setTextColor(Color.WHITE);
        } else {
            item.setBackgroundColor(Color.TRANSPARENT);
            item.setTextColor(Color.parseColor("#9AA0A6"));
        }
    }
}
