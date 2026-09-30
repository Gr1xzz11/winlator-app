package com.winlator;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.winlator.container.ContainerManager;
import com.winlator.container.Shortcut;
import java.util.ArrayList;

public class EduRunnerHomeFragment extends Fragment {
    private RecyclerView recyclerView;
    private TextView emptyView;
    private ContainerManager manager;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.edurunner_home_fragment, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        manager = new ContainerManager(requireContext());
        recyclerView = view.findViewById(R.id.ProgramGrid);
        emptyView = view.findViewById(R.id.EmptyPrograms);
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), getResources().getConfiguration().smallestScreenWidthDp >= 600 ? 3 : 2));
        refresh();
    }

    @Override public void onResume() { super.onResume(); if (manager != null) refresh(); }

    private void refresh() {
        ArrayList<Shortcut> items = manager.loadShortcuts(null);
        items.removeIf(s -> s.file.isDirectory());
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setAdapter(new Adapter(items));
    }

    private void run(Shortcut shortcut) {
        Intent intent = new Intent(requireContext(), XServerDisplayActivity.class);
        intent.putExtra("container_id", shortcut.container.id);
        intent.putExtra("shortcut_path", shortcut.file.getPath());
        startActivity(intent);
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        private final ArrayList<Shortcut> items;
        Adapter(ArrayList<Shortcut> items) { this.items = items; }
        class Holder extends RecyclerView.ViewHolder {
            android.widget.ImageView icon; TextView title, subtitle;
            Holder(View v) { super(v); icon=v.findViewById(R.id.ProgramIcon); title=v.findViewById(R.id.ProgramTitle); subtitle=v.findViewById(R.id.ProgramSubtitle); }
        }
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new Holder(LayoutInflater.from(p.getContext()).inflate(R.layout.edurunner_program_card, p, false));
        }
        @Override public void onBindViewHolder(@NonNull Holder h, int pos) {
            Shortcut s=items.get(pos);
            if (s.icon != null) h.icon.setImageBitmap(s.icon); else h.icon.setImageResource(R.mipmap.ic_launcher);
            h.title.setText(s.name);
            h.subtitle.setText(s.container.getName());
            h.itemView.setOnClickListener(v -> run(s));
        }
        @Override public int getItemCount(){ return items.size(); }
    }
}