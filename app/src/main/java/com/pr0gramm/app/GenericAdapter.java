package com.pr0gramm.app;

import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 */
class GenericAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final List<ViewTypedObject> content = new ArrayList<>();
    private final BiMap<ViewType, Integer> viewTypeIds = HashBiMap.create();

    public void add(ViewType type, Object value) {
        content.add(new ViewTypedObject(type, value));
        notifyItemInserted(content.size() - 1);
    }

    public void addAll(ViewType type, Collection<?> objects) {
        for (Object object : objects)
            content.add(new ViewTypedObject(type, object));

        notifyItemRangeInserted(content.size() - objects.size(), objects.size());
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        ViewType type = viewTypeIds.inverse().get(viewType);
        return type.newViewHolder(parent);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        ViewTypedObject item = content.get(position);
        item.type.bind(holder, item.value);
    }

    @Override
    public int getItemViewType(int position) {
        ViewType type = content.get(position).type;
        Integer id = viewTypeIds.get(type);

        if (id == null)
            viewTypeIds.put(type, id = viewTypeIds.size());

        return id;
    }

    @Override
    public int getItemCount() {
        return content.size();
    }

    private static class ViewTypedObject {
        final ViewType type;
        final Object value;

        ViewTypedObject(ViewType type, Object value) {
            this.type = type;
            this.value = value;
        }
    }

    public interface ViewType {
        long getId(Object object);

        RecyclerView.ViewHolder newViewHolder(ViewGroup parent);

        void bind(RecyclerView.ViewHolder holder, Object object);
    }
}
