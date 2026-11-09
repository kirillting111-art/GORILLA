package com.gorilla.companion

import android.view.View
import android.widget.AdapterView

class SimpleItemSelectedListener(private val selected: (String) -> Unit) : AdapterView.OnItemSelectedListener {
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        selected(parent?.getItemAtPosition(position)?.toString().orEmpty())
    }
    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
}
