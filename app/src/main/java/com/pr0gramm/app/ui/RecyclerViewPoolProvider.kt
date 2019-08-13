package com.pr0gramm.app.ui

import androidx.recyclerview.widget.RecyclerView

interface RecyclerViewPoolProvider {
    val recyclerViewPool: RecyclerView.RecycledViewPool
}